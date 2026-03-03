use reqwest::Client;
use reqwest::header::{CONTENT_TYPE, HeaderMap, HeaderValue};
use serde::{Deserialize, Serialize};

const IMDB_GRAPHQL_URL: &str = "https://caching.graphql.imdb.com/";

const NON_THEATRICAL_TYPES: &[&str] = &[
    "tvMovie",
    "tvShort",
    "video",
    "tvEpisode",
    "tvSeries",
    "tvMiniSeries",
    "tvSpecial",
];

#[derive(Clone)]
pub struct ImdbClient {
    client: Client,
}

#[derive(Serialize)]
struct GraphQLQuery {
    query: String,
    variables: GraphQLVariables,
}

#[derive(Serialize)]
struct GraphQLVariables {
    id: String,
}

#[derive(Deserialize, Debug)]
struct GraphQLResponse {
    data: GraphQLData,
}

#[derive(Deserialize, Debug)]
struct GraphQLData {
    title: Option<TitleData>,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
struct TitleData {
    ratings_summary: Option<RatingsSummary>,
    title_type: Option<TitleType>,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
struct RatingsSummary {
    aggregate_rating: Option<f64>,
}

#[derive(Deserialize, Debug)]
struct TitleType {
    id: Option<String>,
}

fn is_non_theatrical(type_id: &str) -> bool {
    NON_THEATRICAL_TYPES.contains(&type_id)
}

impl ImdbClient {
    pub fn new() -> Self {
        let mut headers = HeaderMap::new();
        headers.insert(CONTENT_TYPE, HeaderValue::from_static("application/json"));

        let client = Client::builder()
            .user_agent("Mozilla/5.0 (X11; Linux x86_64; rv:134.0) Gecko/20100101 Firefox/134.0")
            .default_headers(headers)
            .build()
            .expect("Failed to build IMDB HTTP client");

        Self { client }
    }

    pub async fn get_info(&self, imdb_id: &str) -> (Option<f64>, Option<bool>) {
        if imdb_id.is_empty() {
            return (None, None);
        }

        let query = r#"query TitleInfo($id: ID!) {
            title(id: $id) {
                ratingsSummary {
                    aggregateRating
                }
                titleType {
                    id
                }
            }
        }"#
        .to_string();

        let graphql_query = GraphQLQuery {
            query,
            variables: GraphQLVariables {
                id: imdb_id.to_string(),
            },
        };

        let resp = match self
            .client
            .post(IMDB_GRAPHQL_URL)
            .json(&graphql_query)
            .send()
            .await
        {
            Ok(r) => r,
            Err(_) => return (None, None),
        };

        if !resp.status().is_success() {
            return (None, None);
        }

        let body: GraphQLResponse = match resp.json().await {
            Ok(b) => b,
            Err(_) => return (None, None),
        };

        let title = match body.data.title {
            Some(t) => t,
            None => return (None, None),
        };

        let rating = title.ratings_summary.and_then(|rs| rs.aggregate_rating);

        let theatrical = title
            .title_type
            .and_then(|tt| tt.id.map(|id| is_non_theatrical(&id)));

        (rating, theatrical)
    }

    pub async fn get_score(&self, imdb_id: &str) -> Option<f64> {
        self.get_info(imdb_id).await.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn get_correct_ratings_and_detect_theatrical_film() {
        let client = ImdbClient::new();
        let (rating, theatrical) = client.get_info("tt1390411").await;
        assert!(rating.is_some(), "tt1390411 should have a rating");
        assert_eq!(theatrical, Some(false), "tt1390411 should be theatrical");

        let (_, theatrical) = client.get_info("tt0304584").await;
        assert_eq!(theatrical, Some(true), "tt0304584 should be non-theatrical");

        let (rating, _) = client.get_info("tt0001539").await;
        assert!(rating.is_none(), "tt0001539 should have no rating");
    }
}
