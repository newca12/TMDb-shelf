use libsql::{Builder, Connection, params};

use crate::error::Result;
use crate::models::{CollectionFilter, MovieDB, SearchFilter};

pub struct Db {
    conn: Connection,
}

impl Db {
    pub async fn open(path: &str) -> Result<Self> {
        let db = Builder::new_local(path).build().await?;
        let conn = db.connect()?;
        let this = Self { conn };
        this.create_table().await?;
        this.migrate().await;
        Ok(this)
    }

    async fn create_table(&self) -> Result<()> {
        self.conn
            .execute(
                "CREATE TABLE IF NOT EXISTS movies (
                    tmdb_id        INTEGER PRIMARY KEY,
                    release_date   TEXT NOT NULL,
                    title          TEXT NOT NULL,
                    original_title TEXT NOT NULL,
                    director       TEXT NOT NULL,
                    add_date       TEXT NOT NULL,
                    viewing_date   TEXT,
                    availability   INTEGER NOT NULL DEFAULT 1,
                    imdb_id        TEXT NOT NULL,
                    imdb_score     REAL,
                    seen           INTEGER NOT NULL DEFAULT 0,
                    comment        TEXT NOT NULL DEFAULT '',
                    viewable       INTEGER NOT NULL DEFAULT 1,
                    run_time       INTEGER,
                    is_non_theatrical INTEGER NOT NULL DEFAULT 0
                )",
                params![],
            )
            .await?;
        Ok(())
    }

    async fn migrate(&self) {
        // Add is_non_theatrical column for existing databases
        let _ = self
            .conn
            .execute(
                "ALTER TABLE movies ADD COLUMN is_non_theatrical INTEGER NOT NULL DEFAULT 0",
                params![],
            )
            .await;
    }

    pub async fn insert(&self, movie: &MovieDB) -> Result<()> {
        self.conn
            .execute(
                "INSERT INTO movies (tmdb_id, release_date, title, original_title, director,
                    add_date, viewing_date, availability, imdb_id, imdb_score, seen, comment,
                    viewable, run_time, is_non_theatrical)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)",
                params![
                    movie.tmdb_id,
                    movie.release_date.clone(),
                    movie.title.clone(),
                    movie.original_title.clone(),
                    movie.director.clone(),
                    movie.add_date.clone(),
                    movie.viewing_date.clone(),
                    movie.availability as i32,
                    movie.imdb_id.clone(),
                    movie.imdb_score,
                    movie.seen as i32,
                    movie.comment.clone(),
                    movie.viewable as i32,
                    movie.run_time,
                    movie.is_non_theatrical as i32,
                ],
            )
            .await?;
        Ok(())
    }

    pub async fn delete(&self, tmdb_id: i32) -> Result<()> {
        self.conn
            .execute("DELETE FROM movies WHERE tmdb_id = ?1", params![tmdb_id])
            .await?;
        Ok(())
    }

    pub async fn find_by_id(&self, tmdb_id: i32) -> Result<Option<MovieDB>> {
        let mut rows = self
            .conn
            .query(
                "SELECT tmdb_id, release_date, title, original_title, director,
                        add_date, viewing_date, availability, imdb_id, imdb_score,
                        seen, comment, viewable, run_time, is_non_theatrical
                 FROM movies WHERE tmdb_id = ?1",
                params![tmdb_id],
            )
            .await?;

        if let Some(row) = rows.next().await? {
            Ok(Some(row_to_movie(&row)?))
        } else {
            Ok(None)
        }
    }

    pub async fn filter(
        &self,
        collection_filter: CollectionFilter,
        search_filter: SearchFilter,
        search: &str,
    ) -> Result<Vec<MovieDB>> {
        let mut conditions = Vec::new();

        let order_by = match collection_filter {
            CollectionFilter::NotSeen => {
                conditions.push("seen = 0 AND availability = 1 AND viewable = 1".to_string());
                "ORDER BY COALESCE(imdb_score, 0) DESC, release_date ASC".to_string()
            }
            CollectionFilter::All => "ORDER BY title ASC".to_string(),
            CollectionFilter::Seen => {
                conditions.push("seen = 1".to_string());
                "ORDER BY title ASC".to_string()
            }
            CollectionFilter::NotAvailable => {
                conditions.push("availability = 0".to_string());
                "ORDER BY title ASC".to_string()
            }
            CollectionFilter::NotViewable => {
                conditions.push("viewable = 0".to_string());
                "ORDER BY COALESCE(imdb_score, 0) DESC, release_date ASC".to_string()
            }
        };

        let search_lower = search.to_lowercase();
        if !search_lower.is_empty() {
            match search_filter {
                SearchFilter::All => {}
                SearchFilter::Director => {
                    conditions.push(format!(
                        "LOWER(director) LIKE '%{}%'",
                        search_lower.replace('\'', "''")
                    ));
                }
                SearchFilter::MovieName => {
                    let escaped = search_lower.replace('\'', "''");
                    conditions.push(format!(
                        "(LOWER(title) LIKE '%{}%' OR LOWER(original_title) LIKE '%{}%')",
                        escaped, escaped
                    ));
                }
            }
        }

        let where_clause = if conditions.is_empty() {
            String::new()
        } else {
            format!("WHERE {}", conditions.join(" AND "))
        };

        let sql = format!(
            "SELECT tmdb_id, release_date, title, original_title, director,
                    add_date, viewing_date, availability, imdb_id, imdb_score,
                    seen, comment, viewable, run_time, is_non_theatrical
             FROM movies {} {}",
            where_clause, order_by
        );

        let mut rows = self.conn.query(&sql, params![]).await?;
        let mut movies = Vec::new();
        while let Some(row) = rows.next().await? {
            movies.push(row_to_movie(&row)?);
        }
        Ok(movies)
    }

    pub async fn update_seen_date(&self, tmdb_id: i32, date: &str) -> Result<()> {
        self.conn
            .execute(
                "UPDATE movies SET viewing_date = ?1, seen = 1 WHERE tmdb_id = ?2",
                params![date.to_string(), tmdb_id],
            )
            .await?;
        Ok(())
    }

    pub async fn refresh_movie(
        &self,
        tmdb_id: i32,
        imdb_score: Option<f64>,
        comment: &str,
        viewable: bool,
        available: bool,
        is_non_theatrical: bool,
    ) -> Result<()> {
        self.conn
            .execute(
                "UPDATE movies SET imdb_score = ?1, comment = ?2, viewable = ?3, availability = ?4,
                 is_non_theatrical = ?5
                 WHERE tmdb_id = ?6",
                params![
                    imdb_score,
                    comment.to_string(),
                    viewable as i32,
                    available as i32,
                    is_non_theatrical as i32,
                    tmdb_id
                ],
            )
            .await?;
        Ok(())
    }

    pub async fn save_runtime(&self, tmdb_id: i32, runtime: i32) -> Result<()> {
        self.conn
            .execute(
                "UPDATE movies SET run_time = ?1 WHERE tmdb_id = ?2",
                params![runtime, tmdb_id],
            )
            .await?;
        Ok(())
    }

    pub async fn all_unseen_available(&self) -> Result<Vec<MovieDB>> {
        let mut rows = self
            .conn
            .query(
                "SELECT tmdb_id, release_date, title, original_title, director,
                        add_date, viewing_date, availability, imdb_id, imdb_score,
                        seen, comment, viewable, run_time, is_non_theatrical
                 FROM movies WHERE seen = 0 AND availability = 1 AND viewable = 1
                 ORDER BY COALESCE(imdb_score, 0) DESC, release_date ASC",
                params![],
            )
            .await?;

        let mut movies = Vec::new();
        while let Some(row) = rows.next().await? {
            movies.push(row_to_movie(&row)?);
        }
        Ok(movies)
    }
}

fn row_to_movie(row: &libsql::Row) -> Result<MovieDB> {
    Ok(MovieDB {
        tmdb_id: row.get::<i32>(0)?,
        release_date: row.get::<String>(1)?,
        title: row.get::<String>(2)?,
        original_title: row.get::<String>(3)?,
        director: row.get::<String>(4)?,
        add_date: row.get::<String>(5)?,
        viewing_date: row.get::<Option<String>>(6)?,
        availability: row.get::<i32>(7)? != 0,
        imdb_id: row.get::<String>(8)?,
        imdb_score: row.get::<Option<f64>>(9)?,
        seen: row.get::<i32>(10)? != 0,
        comment: row.get::<String>(11)?,
        viewable: row.get::<i32>(12)? != 0,
        run_time: row.get::<Option<i32>>(13)?,
        is_non_theatrical: row.get::<i32>(14).unwrap_or(0) != 0,
    })
}
