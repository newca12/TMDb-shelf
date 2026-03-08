# Developing

## Prerequisites

- **Rust** (edition 2024)
- **System libraries** for Iced GUI rendering (varies by platform)
- **TMDb API key** (see [README](../README.md#api-key))

### Linux (Debian/Ubuntu)

```bash
# Iced dependencies
sudo apt install pkg-config libfontconfig1-dev libxkbcommon-dev

# Optional: runtime detection tools
sudo apt install mediainfo ffmpeg
```

### macOS

```bash
# Iced dependencies are typically available via Xcode command line tools
xcode-select --install

# Optional
brew install mediainfo ffmpeg
```

## Building

```bash
# Debug build
cargo build

# Release build (optimized)
cargo build --release

# Run directly
cargo run
```

## First Run

On first launch, the application creates `~/.tmdb-shelf/config.toml` with a template and exits with a message asking you to edit it. Set your TMDb API key:

```toml
[api]
key = "your-api-key-here"
```

Then relaunch. The application will create the SQLite database and be ready to use.

## Project Structure

```
tmdb-shelf/
├── Cargo.toml              # Dependencies and project metadata
├── Cargo.lock              # Locked dependency versions
├── README.md               # Project overview
├── docs/                   # Documentation
│   ├── ARCHITECTURE.md     # System design
│   ├── DEVELOPING.md       # This file
│   ├── DATABASE.md         # Database schema and migration
│   ├── API.md              # External API integration
│   └── AGENTS.md           # AI agent guidelines
└── src/
    ├── main.rs             # Entry point
    ├── app.rs              # Application state machine (Elm Architecture)
    ├── models.rs           # Data types and enums
    ├── config.rs           # Configuration loading
    ├── db.rs               # SQLite database operations
    ├── error.rs            # Error type definitions
    ├── runtime_detect.rs   # Video file runtime detection
    ├── api.rs              # API module declarations
    ├── api/
    │   ├── tmdb.rs         # TMDb REST API client
    │   └── imdb.rs         # IMDb GraphQL API client
    ├── ui.rs               # UI module declarations
    └── ui/
        ├── theme.rs        # Layout constants and colors
        ├── toolbar.rs      # Top navigation bar
        ├── poster_grid.rs  # Movie poster grid
        ├── details_panel.rs # Right-side detail view
        └── score_log.rs    # Score change scan display
```

## Testing

```bash
# Run all tests
cargo test

# Run with output
cargo test -- --nocapture
```

The test suite currently includes integration tests for the IMDb GraphQL client (`src/api/imdb.rs`) that make real network requests to validate rating retrieval and non-theatrical content detection.

## Key Design Decisions

### Elm Architecture (via Iced)

The application follows the Iced framework's Model-View-Update pattern:

1. **Model**: `TmdbShelf` struct in `app.rs` holds all application state.
2. **View**: `view()` produces UI elements from the current state. UI components in `ui/` are pure functions of immutable state references.
3. **Update**: `update()` receives `Message` variants and returns (mutated state + optional async `Task`).

All mutations happen in `update()`. UI functions never mutate state. Async operations return results as `Message` variants.

### Async I/O

All network calls, database operations, and file I/O use Tokio async. The Iced `Task` abstraction ensures the UI thread is never blocked. Concurrent operations use `Task::batch()`.

### libsql over rusqlite

The project uses `libsql` instead of `rusqlite` for native async/await support without requiring `spawn_blocking`.

### Error Handling

A unified `AppError` enum with manual `Display`, `Error`, and `From` implementations covers all error sources. Most functions return `Result<T>` which propagates errors up to the `update()` handler, where they become status messages.

The IMDb client is an exception: it silently returns `None` on errors since IMDb data is supplementary and failures should not disrupt the user experience.

## Adding a New Feature

### Adding a new message

1. Add a variant to the `Message` enum in `app.rs`.
2. Add a handler in the `update()` match block.
3. If the message is triggered by UI, update the relevant `ui/` component to emit it.
4. If async, return a `Task::perform()` that produces a result message.

### Adding a new UI section

1. Create a new file in `src/ui/`.
2. Export it from `src/ui.rs`.
3. Define a state struct (borrowing fields from `TmdbShelf`) and a `view()` function.
4. Compose it into the layout in `TmdbShelf::view()`.

### Adding a new database column

1. Update the `movies` table schema in `Db::create_table()`.
2. Add the field to `MovieDB` in `models.rs`.
3. Update `row_to_movie()`, `insert()`, and any relevant query methods in `db.rs`.
4. Consider migration for existing databases (SQLite `ALTER TABLE ADD COLUMN`).

### Adding a new API endpoint

1. Add response types (with `#[derive(Deserialize)]`) to the relevant API module.
2. Add an async method to the client struct.
3. Call it from `update()` via `Task::perform()`, mapping the result to a new `Message` variant.

## Code Style

- No explicit `unwrap()` in production code paths; use `unwrap_or()`, `unwrap_or_default()`, or propagate errors.
- All public types derive `Debug` and `Clone`.
- UI functions take `&'a` references to avoid cloning state.
- Enums that appear in dropdowns provide a `const ALL` slice and implement `Display`.
