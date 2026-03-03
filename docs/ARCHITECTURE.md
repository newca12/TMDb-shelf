# Architecture

## Overview

tmdb-shelf is a desktop GUI application following the **Elm Architecture** (Model-View-Update) pattern via the [Iced](https://iced.rs/) framework. All state mutations are driven by messages, all I/O is asynchronous, and the UI is a pure function of the application state.

## Module Dependency Graph

```
main.rs
  └── app.rs (TmdbShelf state machine)
        ├── config.rs (AppConfig)
        ├── db.rs (Db - SQLite via libsql)
        ├── models.rs (MovieDB, enums)
        ├── error.rs (AppError, Result)
        ├── runtime_detect.rs (mediainfo/ffprobe)
        ├── api.rs (module declarations)
        │     ├── api/tmdb.rs (TmdbClient - REST)
        │     └── api/imdb.rs (ImdbClient - GraphQL)
        └── ui.rs (module declarations)
              ├── ui/theme.rs (constants, colors)
              ├── ui/toolbar.rs (navigation bar)
              ├── ui/poster_grid.rs (8x5 grid of posters)
              ├── ui/details_panel.rs (right sidebar)
              └── ui/score_log.rs (score scan results)
```

## Layers

### Entry Point (`main.rs`)

Loads configuration via `config::load_or_create_config()`, then initializes the Iced application with a 1340x800 window. If configuration is missing or invalid, the process exits with a descriptive error.

### Application Layer (`app.rs`)

The central module. Contains:

- **`TmdbShelf`** struct: The complete application state (~30 fields) covering navigation, grid display, detail panel, score scanning, and status.
- **`Message`** enum: 36 variants representing every possible event (user interactions, async operation results, initialization).
- **`update()`**: The core state machine (~640 lines). Dispatches on `Message` variants, mutates state, and returns `Task<Message>` for async follow-ups.
- **`view()`**: Composes the UI from sub-modules: toolbar at the top, scrollable poster grid on the left, detail panel + score log on the right, status bar at the bottom.
- **`update_page_display()`**: Handles pagination by slicing `all_collection_entries` and spawning poster load tasks for the current page.

### Data Model (`models.rs`)

Pure data types with no dependencies on other internal modules:

- **`MovieDB`**: The persistent movie record (14 fields matching the database schema).
- **`PosterEntry`**: Display-oriented movie entry for the grid (includes poster file path and non-theatrical flag).
- **`CollectionFilter`**: Enum for collection view filtering (NotSeen, All, Seen, NotAvailable, NotViewable).
- **`SearchFilter`**: Enum for search scope (All, Director, MovieName).
- **`SearchMode`**: Enum distinguishing TMDb search results from collection browsing.
- **`ScoreChange`**: Enum with threshold-based IMDb score change detection and Unicode arrow symbols.

### Configuration (`config.rs`)

- **`AppConfig`**: Top-level config container with `api`, `general`, and `tools` sections.
- **`load_or_create_config()`**: Creates `~/.tmdb-shelf/config.toml` from a template on first run. Validates the API key is present. Returns a descriptive error if the user still needs to edit the file.
- Path helpers: `data_dir()`, `db_path()`, `poster_dir()` centralize file system layout.

### Database (`db.rs`)

- **`Db`** struct wrapping a `libsql::Connection`.
- Single `movies` table with 14 columns. Booleans stored as `INTEGER` (0/1), dates as `TEXT` (YYYY-MM-DD), scores as `REAL`.
- All operations are async. Uses parameterized queries for safety.
- `filter()` dynamically builds SQL WHERE clauses based on collection and search filters.

### API Clients (`api/`)

**`tmdb.rs`** - RESTful TMDb API v3:
- `search_movie()`, `get_movie()`, `get_credits()`, `get_releases()` for metadata.
- `download_poster()` for poster images with local caching.
- Helper functions: `director_from_credits()`, `localized_release()`.

**`imdb.rs`** - IMDb GraphQL endpoint:
- `get_info()` returns `(Option<f64>, Option<bool>)` for rating and non-theatrical flag.
- `get_score()` convenience wrapper returning just the rating.
- Silently returns `(None, None)` on any error (no panics, no propagated errors).
- Uses a browser user-agent to avoid request blocking.

### UI Components (`ui/`)

All UI functions take immutable state references and return `Element<'_, Message>`. They produce messages but never mutate state directly.

- **`theme.rs`**: Layout constants (SIDEBAR_WIDTH, POSTER_WIDTH/HEIGHT, GRID_COLUMNS/ROWS, MAX_ITEMS) and `runtime_shadow_color()` mapping runtime ranges to colors.
- **`toolbar.rs`**: Horizontal bar with pagination buttons, collection/search filter dropdowns, search input, and score scan button.
- **`poster_grid.rs`**: 8-column grid of clickable poster tiles with runtime-based drop shadows and opacity dimming for non-theatrical content.
- **`details_panel.rs`**: Scrollable right panel showing selected movie details, action buttons (Add/Refresh/Delete), viewing date input, IMDb/TMDb links, comment field, and viewable/available checkboxes.
- **`score_log.rs`**: Score scan results with a scrollable log and progress bar.

### Error Handling (`error.rs`)

- **`AppError`**: Enum with 8 variants covering I/O, HTTP, database, JSON, TOML, config, image, and process errors.
- Implements `Display`, `Error`, and `From` traits manually (no derive macros).
- **`Result<T>`**: Type alias for `std::result::Result<T, AppError>`.

### Runtime Detection (`runtime_detect.rs`)

- `detect_runtime()`: Async function that opens a file dialog, then tries `mediainfo` (millisecond output) with `ffprobe` (second output) as fallback to extract video duration in minutes.

## Data Flow

### Search Flow
```
User types query → SearchSubmit message
  → async: TmdbClient::search_movie (2 pages)
  → TmdbSearchCompleted → update_page_display()
  → async: download posters for visible page
  → PosterLoaded → poster_handles updated → UI re-renders
```

### Collection Flow
```
User clicks "Show collection" → ShowCollection message
  → async: Db::filter(collection_filter, search_filter, search)
  → CollectionLoaded → update_page_display()
  → async: load local poster files
  → PosterLoaded → poster_handles updated → UI re-renders
```

### Movie Selection Flow
```
User clicks poster → PosterClicked(tmdb_id, imdb_id)
  → Task::batch of 5 parallel async tasks:
     1. TmdbClient::get_movie        → MovieDetailsLoaded
     2. TmdbClient::get_credits      → CreditsLoaded
     3. TmdbClient::get_releases     → ReleasesLoaded
     4. ImdbClient::get_info          → ImdbInfoLoaded
     5. Db::find_by_id               → DbMovieLoaded
  → Detail panel re-renders with all fetched data
```

### Add Movie Flow
```
User clicks "Add" → AddMovie message
  → async: copy poster from tmp to data_dir, Db::insert
  → MovieAdded → detail_is_in_db = true, status = "Registered"
```

### Score Scan Flow
```
User clicks "Find changed scores" → StartScoreScan
  → async: Db::all_unseen_available, then for each movie:
       ImdbClient::get_score → compare with stored score
  → ScoreScanResult(log) → score_log displayed
```

## Concurrency Model

- The Iced framework runs on Tokio. All I/O operations (network, database, file system) are spawned as `Task` values returned from `update()`.
- Multiple tasks can run concurrently via `Task::batch()` (e.g., the 5 parallel fetches on movie selection).
- The UI thread is never blocked. State is only mutated synchronously in `update()` when async results arrive as messages.
- API clients (`TmdbClient`, `ImdbClient`) implement `Clone` and are safe to move into async tasks.

## File System Layout

```
~/.tmdb-shelf/
├── config.toml          # Application configuration
├── tmdb-shelf.db        # SQLite database
└── *.jpg                # Cached poster images (named by tmdb_id)
```
