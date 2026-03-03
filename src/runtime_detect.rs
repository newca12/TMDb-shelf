use std::path::Path;
use tokio::process::Command;

use crate::error::{AppError, Result};

pub async fn detect_runtime(
    file_path: &Path,
    mediainfo_path: Option<&str>,
    ffprobe_path: Option<&str>,
) -> Result<i32> {
    // Try mediainfo first
    if let Some(mi_path) = mediainfo_path
        && let Ok(output) = Command::new(mi_path)
            .arg("--Inform=General;%Duration%")
            .arg(file_path)
            .output()
            .await
        && output.status.success()
    {
        let stdout = String::from_utf8_lossy(&output.stdout);
        let trimmed = stdout.trim();
        if let Ok(ms) = trimmed.parse::<f64>() {
            return Ok((ms / 60_000.0).round() as i32);
        }
    }

    // Fallback to ffprobe
    let ffprobe = ffprobe_path.unwrap_or("ffprobe");
    let output = Command::new(ffprobe)
        .args([
            "-i",
            file_path.to_str().unwrap_or(""),
            "-show_entries",
            "format=duration",
            "-v",
            "quiet",
            "-of",
            "csv=p=0",
        ])
        .output()
        .await
        .map_err(|e| AppError::Process(format!("Failed to run ffprobe: {}", e)))?;

    if !output.status.success() {
        return Err(AppError::Process("ffprobe failed".to_string()));
    }

    let stdout = String::from_utf8_lossy(&output.stdout);
    let seconds: f64 = stdout
        .trim()
        .parse()
        .map_err(|e| AppError::Process(format!("Failed to parse ffprobe output: {}", e)))?;

    Ok((seconds / 60.0).round() as i32)
}
