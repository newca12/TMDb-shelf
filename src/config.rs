use serde::{Deserialize, Serialize};
use std::path::PathBuf;

use crate::error::{AppError, Result};

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct AppConfig {
    pub api: ApiConfig,
    #[serde(default)]
    pub general: GeneralConfig,
    #[serde(default)]
    pub tools: ToolsConfig,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct ApiConfig {
    pub key: String,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct GeneralConfig {
    #[serde(default = "default_language")]
    pub language: String,
    pub data_dir: Option<String>,
}

impl Default for GeneralConfig {
    fn default() -> Self {
        Self {
            language: default_language(),
            data_dir: None,
        }
    }
}

fn default_language() -> String {
    "en".to_string()
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
pub struct ToolsConfig {
    pub mediainfo_path: Option<String>,
    pub ffprobe_path: Option<String>,
}

impl AppConfig {
    pub fn data_dir(&self) -> PathBuf {
        if let Some(ref dir) = self.general.data_dir {
            PathBuf::from(dir)
        } else {
            dirs::home_dir()
                .unwrap_or_else(|| PathBuf::from("."))
                .join(".tmdb-shelf")
        }
    }

    pub fn db_path(&self) -> PathBuf {
        self.data_dir().join("tmdb-shelf.db")
    }

    pub fn poster_dir(&self) -> PathBuf {
        self.data_dir()
    }
}

const TEMPLATE_CONFIG: &str = r#"# TMDb-shelf configuration

[api]
# Your TMDb API key (get one at https://www.themoviedb.org/settings/api)
key = ""

[general]
# Language for TMDb API queries (ISO 639-1 code)
language = "en"
# Path to store posters and database (default: ~/.tmdb-shelf)
# data_dir = "/home/user/.tmdb-shelf"

[tools]
# Path to mediainfo binary (leave empty to try ffprobe instead)
# mediainfo_path = "/usr/bin/mediainfo"
# Path to ffprobe binary (fallback if mediainfo is not set)
# ffprobe_path = "/usr/bin/ffprobe"
"#;

pub fn load_or_create_config() -> Result<AppConfig> {
    let config_dir = dirs::home_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join(".tmdb-shelf");

    std::fs::create_dir_all(&config_dir).map_err(AppError::Io)?;

    let config_path = config_dir.join("config.toml");

    if !config_path.exists() {
        std::fs::write(&config_path, TEMPLATE_CONFIG).map_err(AppError::Io)?;
        return Err(AppError::Config(format!(
            "Config file created at {}. Please edit it and set your TMDb API key.",
            config_path.display()
        )));
    }

    let contents = std::fs::read_to_string(&config_path).map_err(AppError::Io)?;
    let config: AppConfig = toml::from_str(&contents)?;

    if config.api.key.is_empty() {
        return Err(AppError::Config(format!(
            "TMDb API key is empty. Please edit {}",
            config_path.display()
        )));
    }

    std::fs::create_dir_all(config.data_dir()).map_err(AppError::Io)?;

    Ok(config)
}
