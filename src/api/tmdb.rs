use reqwest::Client;
use serde::Deserialize;
use std::path::{Path, PathBuf};

use crate::error::{AppError, Result};

const TMDB_BASE_URL: &str = "https://api.themoviedb.org/3";
const TMDB_IMAGE_BASE: &str = "https://image.tmdb.org/t/p/w342";

#[derive(Clone)]
pub struct TmdbClient {
    client: Client,
    api_key: String,
    pub language: String,
}

// --- Response types ---

#[derive(Deserialize, Debug)]
#[allow(dead_code)]
pub struct TmdbSearchResults {
    pub page: i32,
    pub total_pages: i32,
    pub total_results: i32,
    pub results: Vec<TmdbSearchResult>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct TmdbSearchResult {
    pub id: i32,
    pub title: Option<String>,
    pub original_title: Option<String>,
    pub release_date: Option<String>,
    pub poster_path: Option<String>,
}

#[derive(Deserialize, Debug)]
#[allow(dead_code)]
pub struct TmdbMovie {
    pub id: i32,
    pub title: Option<String>,
    pub original_title: Option<String>,
    pub release_date: Option<String>,
    pub imdb_id: Option<String>,
    pub poster_path: Option<String>,
    pub runtime: Option<i32>,
}

#[derive(Deserialize, Debug)]
pub struct TmdbCredits {
    pub crew: Vec<TmdbCrewMember>,
}

#[derive(Deserialize, Debug)]
pub struct TmdbCrewMember {
    pub name: String,
    pub job: String,
}

#[derive(Deserialize, Debug)]
pub struct TmdbReleases {
    pub countries: Vec<TmdbCountryRelease>,
}

#[derive(Deserialize, Debug)]
pub struct TmdbCountryRelease {
    pub iso_3166_1: String,
    pub release_date: String,
}

impl TmdbClient {
    pub fn new(api_key: &str, language: &str) -> Self {
        let client = Client::builder()
            .user_agent("tmdb-shelf/0.1.0")
            .build()
            .expect("Failed to build TMDb HTTP client");

        Self {
            client,
            api_key: api_key.to_string(),
            language: language.to_string(),
        }
    }

    fn build_url(&self, path: &str, extra_params: &[(&str, &str)]) -> String {
        let mut url = format!(
            "{}{}?api_key={}&language={}",
            TMDB_BASE_URL, path, self.api_key, self.language
        );
        for (k, v) in extra_params {
            url.push('&');
            url.push_str(k);
            url.push('=');
            url.push_str(&urlencoded(v));
        }
        url
    }

    fn build_url_no_lang(&self, path: &str) -> String {
        format!("{}{}?api_key={}", TMDB_BASE_URL, path, self.api_key)
    }

    pub async fn search_movie(&self, query: &str, page: i32) -> Result<TmdbSearchResults> {
        let page_str = page.to_string();
        let url = self.build_url("/search/movie", &[("query", query), ("page", &page_str)]);
        let resp = self
            .client
            .get(&url)
            .send()
            .await?
            .json::<TmdbSearchResults>()
            .await?;
        Ok(resp)
    }

    pub async fn get_movie(&self, id: i32) -> Result<TmdbMovie> {
        let url = self.build_url(&format!("/movie/{}", id), &[]);
        let resp = self
            .client
            .get(&url)
            .send()
            .await?
            .json::<TmdbMovie>()
            .await?;
        Ok(resp)
    }

    pub async fn get_credits(&self, id: i32) -> Result<TmdbCredits> {
        let url = self.build_url_no_lang(&format!("/movie/{}/credits", id));
        let resp = self
            .client
            .get(&url)
            .send()
            .await?
            .json::<TmdbCredits>()
            .await?;
        Ok(resp)
    }

    pub async fn get_releases(&self, id: i32) -> Result<TmdbReleases> {
        let url = self.build_url_no_lang(&format!("/movie/{}/releases", id));
        let resp = self
            .client
            .get(&url)
            .send()
            .await?
            .json::<TmdbReleases>()
            .await?;
        Ok(resp)
    }

    pub async fn download_poster(&self, poster_path: &str, dest_dir: &Path) -> Result<PathBuf> {
        let filename = poster_path.trim_start_matches('/');
        let dest = dest_dir.join(filename);

        if dest.exists() {
            return Ok(dest);
        }

        let url = format!("{}{}", TMDB_IMAGE_BASE, poster_path);
        let bytes = self.client.get(&url).send().await?.bytes().await?;
        tokio::fs::write(&dest, &bytes)
            .await
            .map_err(AppError::Io)?;
        Ok(dest)
    }
}

fn urlencoded(s: &str) -> String {
    s.replace(' ', "%20")
        .replace('&', "%26")
        .replace('=', "%3D")
        .replace('+', "%2B")
        .replace('#', "%23")
}

pub fn director_from_credits(credits: &TmdbCredits) -> String {
    credits
        .crew
        .iter()
        .find(|c| c.job == "Director")
        .map(|c| c.name.clone())
        .unwrap_or_else(|| "Unknown".to_string())
}

pub fn localized_release(releases: &TmdbReleases, country_code: &str) -> Option<String> {
    releases
        .countries
        .iter()
        .find(|c| c.iso_3166_1 == country_code)
        .map(|c| c.release_date.clone())
}
