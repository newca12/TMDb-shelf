# Database

## Overview

tmdb-shelf uses SQLite (via [libsql](https://github.com/tursodatabase/libsql)) as its database engine. The database file is located at `~/.tmdb-shelf/tmdb-shelf.db` by default (configurable via `data_dir` in `config.toml`).

## Schema

The database contains a single table:

```sql
CREATE TABLE IF NOT EXISTS movies (
    tmdb_id        INTEGER PRIMARY KEY,  -- The Movie Database ID
    release_date   TEXT NOT NULL,         -- YYYY-MM-DD
    title          TEXT NOT NULL,         -- Localized title
    original_title TEXT NOT NULL,         -- Original language title
    director       TEXT NOT NULL,         -- Director name
    add_date       TEXT NOT NULL,         -- YYYY-MM-DD, when added to collection
    viewing_date   TEXT,                  -- YYYY-MM-DD, when watched (NULL if not seen)
    availability   INTEGER NOT NULL DEFAULT 1,  -- 1 = available, 0 = not
    imdb_id        TEXT NOT NULL,         -- IMDb ID (e.g., "tt1234567")
    imdb_score     REAL,                  -- IMDb rating 0.0-10.0 (NULL if unknown)
    seen           INTEGER NOT NULL DEFAULT 0,  -- 1 = watched, 0 = not
    comment        TEXT NOT NULL DEFAULT '',     -- User notes
    viewable       INTEGER NOT NULL DEFAULT 1,  -- 1 = theatrical, 0 = non-theatrical
    run_time       INTEGER               -- Runtime in minutes (NULL if unknown)
);
```

### Type Conventions

| Rust Type | SQLite Type | Notes |
|-----------|-------------|-------|
| `i32` | `INTEGER` | tmdb_id, run_time |
| `String` | `TEXT` | Titles, dates, IDs, comments |
| `bool` | `INTEGER` | Stored as 0/1, converted in `row_to_movie()` |
| `Option<String>` | `TEXT` | NULL when not set (viewing_date) |
| `Option<f64>` | `REAL` | NULL when unknown (imdb_score) |
| `Option<i32>` | `INTEGER` | NULL when unknown (run_time) |

### Date Format

All dates are stored as `TEXT` in `YYYY-MM-DD` format. This maintains compatibility with the original H2 database and allows direct string comparison for sorting.

## Operations

All database operations are in `src/db.rs`. The `Db` struct wraps a `libsql::Connection` and all methods are async.

| Method | Description |
|--------|-------------|
| `open(path)` | Opens (or creates) the database and ensures the schema exists |
| `insert(movie)` | Adds a new movie to the collection |
| `delete(tmdb_id)` | Removes a movie by its TMDb ID |
| `find_by_id(tmdb_id)` | Looks up a single movie, returns `Option<MovieDB>` |
| `filter(collection, search, text)` | Queries with dynamic WHERE clause and ordering |
| `update_seen_date(tmdb_id, date)` | Marks a movie as seen with a date |
| `refresh_movie(tmdb_id, ...)` | Updates score, comment, viewable, and availability |
| `save_runtime(tmdb_id, runtime)` | Stores detected runtime |
| `all_unseen_available()` | Returns unseen, available movies for score scanning |

### Filtering Logic

The `filter()` method builds SQL dynamically based on the combination of collection filter and search filter:

**Collection filters** (WHERE clauses):

| Filter | Condition | ORDER BY |
|--------|-----------|----------|
| Not Seen | `seen = 0 AND availability = 1 AND viewable = 1` | `imdb_score DESC, release_date ASC` |
| All | *(none)* | `title ASC` |
| Seen | `seen = 1` | `title ASC` |
| Not Available | `availability = 0` | `title ASC` |
| Not Viewable | `viewable = 0` | `imdb_score DESC, release_date ASC` |

**Search filters** (additional WHERE clauses):

| Filter | Condition |
|--------|-----------|
| All | *(none)* |
| Director | `LOWER(director) LIKE '%query%'` |
| Movie Name | `LOWER(title) LIKE '%query%' OR LOWER(original_title) LIKE '%query%'` |

## Migration from H2 (Scala version)

The original Scala TMDb-shelf used an [H2](https://h2database.com/) embedded database. The H2 database file is located at `~/.tmdb-shelf/tmdb-shelf.mv.db`.

### Schema Comparison

| Column | H2 Type | SQLite Type |
|--------|---------|-------------|
| TMDB_ID | `INTEGER` | `INTEGER` |
| RELEASE_DATE | `DATE` | `TEXT` |
| TITLE | `VARCHAR` | `TEXT` |
| ORIGINAL_TITLE | `VARCHAR` | `TEXT` |
| DIRECTOR | `VARCHAR` | `TEXT` |
| ADD_DATE | `DATE` | `TEXT` |
| VIEWING_DATE | `DATE` | `TEXT` |
| AVAILABILITY | `BOOLEAN` | `INTEGER` |
| IMDB_ID | `VARCHAR` | `TEXT` |
| IMDB_SCORE | `DECIMAL(2,1)` | `REAL` |
| SEEN | `BOOLEAN` | `INTEGER` |
| COMMENT | `VARCHAR` | `TEXT` |
| VIEWABLE | `BOOLEAN` | `INTEGER` |
| RUNTIME | `INTEGER` | `INTEGER` |

### Migration Procedure

The H2 data can be exported to CSV and imported into SQLite. The H2 jar matching the Scala project's version (1.4.197) is required.

**Step 1: Export from H2**

```bash
java -cp h2-1.4.197.jar org.h2.tools.Shell \
  -url "jdbc:h2:file:$HOME/.tmdb-shelf/tmdb-shelf" \
  -user "" -password "" \
  -sql "CALL CSVWRITE('/tmp/tmdb-shelf-export.csv', \
    'SELECT TMDB_ID, CAST(RELEASE_DATE AS VARCHAR), TITLE, ORIGINAL_TITLE, \
     DIRECTOR, CAST(ADD_DATE AS VARCHAR), CAST(VIEWING_DATE AS VARCHAR), \
     CASE WHEN AVAILABILITY THEN 1 ELSE 0 END, IMDB_ID, IMDB_SCORE, \
     CASE WHEN SEEN THEN 1 ELSE 0 END, COMMENT, \
     CASE WHEN VIEWABLE THEN 1 ELSE 0 END, RUNTIME FROM MOVIES')"
```

The SQL handles type conversions: `DATE` to `VARCHAR` strings, `BOOLEAN` to `0`/`1` integers.

**Step 2: Import into SQLite**

```bash
sqlite3 ~/.tmdb-shelf/tmdb-shelf.db <<'EOSQL'
CREATE TEMP TABLE import_tmp (
    c_tmdb_id TEXT, c_release_date TEXT, c_title TEXT,
    c_original_title TEXT, c_director TEXT, c_add_date TEXT,
    c_viewing_date TEXT, c_availability TEXT, c_imdb_id TEXT,
    c_imdb_score TEXT, c_seen TEXT, c_comment TEXT,
    c_viewable TEXT, c_runtime TEXT
);

.mode csv
.import /tmp/tmdb-shelf-export.csv import_tmp

DELETE FROM import_tmp WHERE c_tmdb_id = 'TMDB_ID';

INSERT INTO movies (tmdb_id, release_date, title, original_title, director,
    add_date, viewing_date, availability, imdb_id, imdb_score, seen,
    comment, viewable, run_time)
SELECT
    CAST(c_tmdb_id AS INTEGER),
    c_release_date,
    c_title,
    c_original_title,
    c_director,
    c_add_date,
    NULLIF(c_viewing_date, ''),
    CAST(c_availability AS INTEGER),
    c_imdb_id,
    CASE WHEN c_imdb_score = '' THEN NULL
         ELSE CAST(c_imdb_score AS REAL) END,
    CAST(c_seen AS INTEGER),
    c_comment,
    CAST(c_viewable AS INTEGER),
    CASE WHEN c_runtime = '' THEN NULL
         ELSE CAST(c_runtime AS INTEGER) END
FROM import_tmp;

DROP TABLE import_tmp;
EOSQL
```

**Step 3: Verify**

```bash
sqlite3 ~/.tmdb-shelf/tmdb-shelf.db "SELECT COUNT(*) FROM movies;"
```

### Poster Compatibility

Both the Scala and Rust versions store poster images as `{tmdb_id}.jpg` in the data directory. Existing posters from the Scala version are compatible with the Rust version without any migration.
