# AI Agent Guidelines

This document provides guidance for AI coding agents (Claude Code, Copilot, Cursor, etc.) working on the tmdb-shelf codebase.

## Project Context

tmdb-shelf is a Rust desktop application for managing a personal movie collection. It uses the Iced GUI framework following the Elm Architecture pattern. The codebase is ~2,500 lines across 15 source files.

**Key technologies**: Rust 2024 edition (nightly), Iced 0.14, libsql (async SQLite), reqwest, Tokio, serde.

## Architecture Quick Reference

- **`app.rs`**: The central module. Contains `TmdbShelf` (state), `Message` (36 variants), `update()` (message handler), `view()` (UI composition). This is where most feature work happens.
- **`models.rs`**: Pure data types. No internal dependencies.
- **`db.rs`**: All database operations. Single `movies` table, async methods.
- **`api/tmdb.rs`**: TMDb REST client. Movie search, details, credits, releases, poster downloads.
- **`api/imdb.rs`**: IMDb GraphQL client. Ratings and content type detection. Silently returns `None` on errors.
- **`ui/`**: Stateless view functions. Each takes immutable state references and returns `Element<'_, Message>`.
- **`config.rs`**: TOML configuration loading from `~/.tmdb-shelf/config.toml`.
- **`error.rs`**: `AppError` enum with manual `Display`/`Error`/`From` impls. All internal functions use `Result<T>`.

## Essential Patterns

### Elm Architecture (Message-Driven Updates)

All state changes go through `update()` in `app.rs`. The pattern is:

1. User interacts with UI -> UI component emits a `Message`.
2. `update()` matches on the message, mutates state, optionally returns an async `Task<Message>`.
3. When the async task completes, it produces another `Message` (the result variant).
4. `view()` re-renders based on the new state.

When adding features, follow this pattern. Never mutate state outside of `update()`.

### Async Tasks

```rust
// Pattern for async operations in update():
Task::perform(
    async move {
        // do async work...
        Ok(result)  // or Err(e.to_string())
    },
    Message::SomeResultVariant,  // maps Result into a Message
)
```

For parallel operations, use `Task::batch(vec![task1, task2, ...])`.

### UI Components

UI functions in `src/ui/` follow this pattern:

```rust
pub struct SomeState<'a> {
    pub field: &'a str,  // borrow from TmdbShelf
}

pub fn view(state: &SomeState) -> Element<'_, Message> {
    // build and return UI elements
}
```

State is passed by reference. UI functions emit `Message` variants but never mutate anything.

### Error Handling

- Use `Result<T>` (aliased to `std::result::Result<T, AppError>`) for functions that can fail.
- In `update()`, async task errors are typically converted to `String` via `.map_err(|e| e.to_string())` and stored as `status_message`.
- The IMDb client is unique: it returns `(None, None)` on any error instead of propagating.

## Critical Files for Common Tasks

| Task | Files to modify |
|------|----------------|
| Add new collection filter | `models.rs`, `db.rs` (filter logic), `app.rs` (message handling) |
| Add new detail panel field | `models.rs`, `app.rs` (state + messages), `ui/details_panel.rs` |
| Add new API endpoint | `api/tmdb.rs` or `api/imdb.rs`, `app.rs` (message + handler) |
| Add new database column | `db.rs` (schema + queries), `models.rs` (MovieDB struct) |
| Change grid layout | `ui/theme.rs` (constants), `ui/poster_grid.rs` |
| Change toolbar controls | `ui/toolbar.rs`, `app.rs` (if new messages needed) |

## Codebase Conventions

- **No `unwrap()` in production paths**. Use `unwrap_or()`, `unwrap_or_default()`, or error propagation.
- **Enums for dropdowns** provide `const ALL: &[Self]` and implement `Display`.
- **Booleans in SQLite** are stored as `INTEGER` (0/1) and converted in `row_to_movie()`.
- **Dates** are `String` in `YYYY-MM-DD` format, not `chrono` types. `chrono` is only used for `Local::now()` when setting `add_date`.
- **Poster filenames** are `{tmdb_id}.jpg`.
- All API clients implement `Clone` for safe use in async tasks.

## Testing

Run tests with `cargo test`. Currently, only `src/api/imdb.rs` has tests (integration tests hitting the real IMDb API).

When adding tests:
- API tests may require network access.
- Database tests should use a temporary file path.
- UI functions are pure and can be tested by examining returned elements, but Iced does not provide simple element inspection utilities.

## Common Gotchas

1. **Iced edition**: The project uses Iced 0.14, not 0.13 or 0.12. Widget APIs differ significantly between versions; refer to the [Iced 0.14 docs](https://docs.rs/iced/0.14).

2. **libsql vs rusqlite**: The project uses `libsql`, not `rusqlite`. The API surface is similar but not identical. Parameters use `libsql::params![]`.

3. **Database connections**: `Db::open()` is called fresh in each async task (not shared). This is intentional since `libsql::Connection` is not `Send`.

4. **Poster loading**: Posters are loaded lazily per page via `update_page_display()`. The `poster_handles` HashMap caches loaded images by `tmdb_id` to avoid reloading.

5. **Score scan**: Runs sequentially (one IMDb request at a time) to avoid rate-limiting. This is intentional.

6. **TMDb search**: Fetches 2 pages of results (up to 40 items) to fill the grid (8 columns x 5 rows = 40 items per page).

7. **`Message` variants carry `Result<T, String>`**, not `Result<T, AppError>`, because `AppError` does not implement `Clone` (required by Iced messages).

## Build and Run

```bash
cargo build          # debug build
cargo build --release  # release build
cargo run            # run (debug)
cargo test           # run tests
```

First run creates `~/.tmdb-shelf/config.toml`. Edit it to add your TMDb API key before relaunching.

## File System Layout

```
~/.tmdb-shelf/
├── config.toml      # Configuration (API key, language, tool paths)
├── tmdb-shelf.db    # SQLite database
└── *.jpg            # Poster images ({tmdb_id}.jpg)
```
