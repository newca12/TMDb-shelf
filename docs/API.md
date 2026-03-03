# API Integration

tmdb-shelf integrates with two external APIs: TMDb (REST) for movie metadata and IMDb (GraphQL) for ratings.

## TMDb API

**Source**: `src/api/tmdb.rs`

### Client

`TmdbClient` wraps a `reqwest::Client` with API key and language configuration. It implements `Clone` for safe sharing across async tasks.

```rust
let client = TmdbClient::new("your-api-key", "en");
```

### Base URLs

| Purpose | URL |
|---------|-----|
| API | `https://api.themoviedb.org/3` |
| Poster images | `https://image.tmdb.org/t/p/w342` |

### Endpoints Used

#### Search Movies

```
GET /search/movie?api_key={key}&language={lang}&query={query}&page={page}
```

Returns paginated results. The application fetches up to 2 pages (40 results) per search to fill one grid page.

**Response type**: `TmdbSearchResults` containing `Vec<TmdbSearchResult>`:
- `id: i32` - TMDb movie ID
- `title: Option<String>` - Localized title
- `original_title: Option<String>` - Original language title
- `release_date: Option<String>` - Release date (YYYY-MM-DD)
- `poster_path: Option<String>` - Poster image path (e.g., `/abc123.jpg`)

#### Get Movie Details

```
GET /movie/{id}?api_key={key}&language={lang}
```

Returns full movie metadata including IMDb ID and runtime.

**Response type**: `TmdbMovie`:
- `imdb_id: Option<String>` - IMDb identifier (e.g., `tt1234567`)
- `runtime: Option<i32>` - Runtime in minutes
- Plus title, original_title, release_date, poster_path

#### Get Credits

```
GET /movie/{id}/credits?api_key={key}
```

Returns cast and crew. The application extracts the director via `director_from_credits()`, which finds the first crew member with `job == "Director"`.

Note: This endpoint is called without the `language` parameter.

#### Get Releases

```
GET /movie/{id}/releases?api_key={key}
```

Returns release dates by country. The application uses `localized_release()` to find the release date matching the configured language's country code (e.g., `FR` for French).

Note: This endpoint is called without the `language` parameter.

#### Download Poster

```
GET https://image.tmdb.org/t/p/w342/{poster_path}
```

Downloads poster images at 342px width. Files are cached locally -- if the destination file already exists, the download is skipped.

- For TMDb search results: cached in the system temp directory.
- When a movie is added to the collection: copied to `~/.tmdb-shelf/{tmdb_id}.jpg`.

### URL Encoding

The client uses a minimal URL encoder (`urlencoded()`) handling spaces, ampersands, equals, plus signs, and hash characters.

### Error Handling

All TMDb client methods return `Result<T>`, propagating `reqwest` errors as `AppError::Http`.

## IMDb GraphQL API

**Source**: `src/api/imdb.rs`

### Client

`ImdbClient` wraps a `reqwest::Client` configured with:
- `Content-Type: application/json` header
- A Firefox user-agent string to avoid request blocking

```rust
let client = ImdbClient::new();
```

### Endpoint

```
POST https://caching.graphql.imdb.com/
```

### GraphQL Query

```graphql
query TitleInfo($id: ID!) {
    title(id: $id) {
        ratingsSummary {
            aggregateRating
        }
        titleType {
            id
        }
    }
}
```

Variables: `{ "id": "tt1234567" }`

### Methods

#### `get_info(imdb_id) -> (Option<f64>, Option<bool>)`

Returns a tuple of:
1. **Rating**: The aggregate IMDb rating (0.0-10.0), or `None` if unavailable.
2. **Non-theatrical flag**: `Some(true)` if the title is non-theatrical (TV movie, TV series, etc.), `Some(false)` if theatrical, `None` if type unknown.

Non-theatrical types: `tvMovie`, `tvShort`, `video`, `tvEpisode`, `tvSeries`, `tvMiniSeries`, `tvSpecial`.

#### `get_score(imdb_id) -> Option<f64>`

Convenience wrapper that calls `get_info()` and returns only the rating.

### Error Handling

Unlike the TMDb client, the IMDb client **silently swallows all errors** and returns `(None, None)`. This is intentional: IMDb data is supplementary and network failures or parsing issues should not disrupt the application. Errors are not logged or propagated.

### Score Change Detection

The `ScoreChange` enum in `models.rs` compares stored and live IMDb scores:

| Condition | Variant | Symbol |
|-----------|---------|--------|
| \|diff\| < 0.05 | `Equal` | `=` |
| diff >= 0.15 | `UpUp` | `⇈` |
| 0.0 < diff < 0.15 | `Up` | `↑` |
| diff <= -0.15 | `DownDown` | `⇊` |
| -0.15 < diff < 0.0 | `Down` | `↓` |
| No comparison possible | `Unknown` | *(empty)* |

The threshold-based system avoids noise from minor floating-point variations.

## Request Flow Diagram

When a user clicks a poster, 5 API/DB requests are dispatched in parallel:

```
PosterClicked
  ├─→ TMDb: GET /movie/{id}           → MovieDetailsLoaded
  ├─→ TMDb: GET /movie/{id}/credits   → CreditsLoaded
  ├─→ TMDb: GET /movie/{id}/releases  → ReleasesLoaded
  ├─→ IMDb: POST GraphQL              → ImdbInfoLoaded
  └─→ DB:   find_by_id                → DbMovieLoaded
```

Each response updates the detail panel independently as it arrives, providing a responsive experience.
