use std::fmt;
use std::io;

#[derive(Debug)]
pub enum AppError {
    Io(io::Error),
    Http(reqwest::Error),
    Database(libsql::Error),
    Json(serde_json::Error),
    TomlParse(toml::de::Error),
    Config(String),
    #[allow(dead_code)]
    ImageLoad(String),
    Process(String),
}

impl fmt::Display for AppError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AppError::Io(e) => write!(f, "I/O error: {e}"),
            AppError::Http(e) => write!(f, "HTTP error: {e}"),
            AppError::Database(e) => write!(f, "Database error: {e}"),
            AppError::Json(e) => write!(f, "JSON error: {e}"),
            AppError::TomlParse(e) => write!(f, "TOML parse error: {e}"),
            AppError::Config(msg) => write!(f, "Configuration error: {msg}"),
            AppError::ImageLoad(msg) => write!(f, "Image load error: {msg}"),
            AppError::Process(msg) => write!(f, "Process error: {msg}"),
        }
    }
}

impl std::error::Error for AppError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            AppError::Io(e) => Some(e),
            AppError::Http(e) => Some(e),
            AppError::Database(e) => Some(e),
            AppError::Json(e) => Some(e),
            AppError::TomlParse(e) => Some(e),
            _ => None,
        }
    }
}

impl From<io::Error> for AppError {
    fn from(e: io::Error) -> Self {
        AppError::Io(e)
    }
}

impl From<reqwest::Error> for AppError {
    fn from(e: reqwest::Error) -> Self {
        AppError::Http(e)
    }
}

impl From<libsql::Error> for AppError {
    fn from(e: libsql::Error) -> Self {
        AppError::Database(e)
    }
}

impl From<serde_json::Error> for AppError {
    fn from(e: serde_json::Error) -> Self {
        AppError::Json(e)
    }
}

impl From<toml::de::Error> for AppError {
    fn from(e: toml::de::Error) -> Self {
        AppError::TomlParse(e)
    }
}

pub type Result<T> = std::result::Result<T, AppError>;
