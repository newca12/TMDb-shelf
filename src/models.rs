use std::fmt;
use std::path::PathBuf;

#[derive(Debug, Clone)]
pub struct MovieDB {
    pub tmdb_id: i32,
    pub release_date: String,
    pub title: String,
    pub original_title: String,
    pub director: String,
    pub add_date: String,
    pub viewing_date: Option<String>,
    pub availability: bool,
    pub imdb_id: String,
    pub imdb_score: Option<f64>,
    pub seen: bool,
    pub comment: String,
    pub viewable: bool,
    pub run_time: Option<i32>,
    pub is_non_theatrical: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum CollectionFilter {
    #[default]
    NotSeen,
    All,
    Seen,
    NotAvailable,
    NotViewable,
}

impl CollectionFilter {
    pub const ALL: &[CollectionFilter] = &[
        CollectionFilter::NotSeen,
        CollectionFilter::All,
        CollectionFilter::Seen,
        CollectionFilter::NotAvailable,
        CollectionFilter::NotViewable,
    ];
}

impl fmt::Display for CollectionFilter {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            CollectionFilter::NotSeen => write!(f, "Not seen"),
            CollectionFilter::All => write!(f, "All"),
            CollectionFilter::Seen => write!(f, "Seen"),
            CollectionFilter::NotAvailable => write!(f, "Not available"),
            CollectionFilter::NotViewable => write!(f, "Not viewable"),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum SearchFilter {
    #[default]
    All,
    Director,
    MovieName,
}

impl SearchFilter {
    pub const ALL: &[SearchFilter] = &[
        SearchFilter::All,
        SearchFilter::Director,
        SearchFilter::MovieName,
    ];
}

impl fmt::Display for SearchFilter {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            SearchFilter::All => write!(f, "All"),
            SearchFilter::Director => write!(f, "Director"),
            SearchFilter::MovieName => write!(f, "Movie name"),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum SearchMode {
    #[default]
    TmdbSearch,
    Collection,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum ScoreChange {
    #[default]
    Unknown,
    Equal,
    Up,
    UpUp,
    Down,
    DownDown,
}

impl ScoreChange {
    pub fn from_scores(stored: Option<f64>, live: Option<f64>) -> Self {
        match (stored, live) {
            (Some(s), Some(l)) => {
                let diff = l - s;
                if diff.abs() < 0.05 {
                    ScoreChange::Equal
                } else if diff >= 0.15 {
                    ScoreChange::UpUp
                } else if diff > 0.0 {
                    ScoreChange::Up
                } else if diff <= -0.15 {
                    ScoreChange::DownDown
                } else {
                    ScoreChange::Down
                }
            }
            _ => ScoreChange::Unknown,
        }
    }

    pub fn symbol(&self) -> &str {
        match self {
            ScoreChange::Equal => "=",
            ScoreChange::Up => "\u{2191}",       // ↑
            ScoreChange::UpUp => "\u{21c8}",     // ⇈
            ScoreChange::Down => "\u{2193}",     // ↓
            ScoreChange::DownDown => "\u{21ca}", // ⇊
            ScoreChange::Unknown => "",
        }
    }
}

#[derive(Debug, Clone)]
pub struct PosterEntry {
    pub tmdb_id: i32,
    pub imdb_id: String,
    pub title: String,
    pub original_title: String,
    pub release_date: String,
    pub run_time: Option<i32>,
    pub is_non_theatrical: bool,
    pub poster_path: Option<PathBuf>,
}
