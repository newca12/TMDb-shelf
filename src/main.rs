mod api;
mod app;
mod config;
mod db;
mod error;
mod models;
mod runtime_detect;
mod ui;

use app::TmdbShelf;

fn main() -> iced::Result {
    let config = match config::load_or_create_config() {
        Ok(c) => c,
        Err(e) => {
            eprintln!("Configuration error: {}", e);
            eprintln!("Please edit ~/.tmdb-shelf/config.toml and restart.");
            std::process::exit(1);
        }
    };

    iced::application(
        move || TmdbShelf::new(config.clone()),
        TmdbShelf::update,
        TmdbShelf::view,
    )
    .title(TmdbShelf::title)
    .subscription(TmdbShelf::subscription)
    .font(iced_aw::ICED_AW_FONT_BYTES)
    .window_size(iced::Size::new(1340.0, 780.0))
    .run()
}
