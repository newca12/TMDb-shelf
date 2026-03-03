use std::collections::HashMap;

use iced::futures::SinkExt;
use iced::widget::{column, container, image, row, scrollable, text};
use iced::{Element, Length, Subscription, Task};
use iced_aw::date_picker::Date;

use crate::api::imdb::ImdbClient;
use crate::api::tmdb::{self, TmdbClient};
use crate::config::AppConfig;
use crate::db::Db;
use crate::models::*;
use crate::ui::{details_panel, poster_grid, score_log, toolbar};

pub struct TmdbShelf {
    config: AppConfig,
    tmdb_client: TmdbClient,
    imdb_client: ImdbClient,

    // Navigation
    search_mode: SearchMode,
    current_page: usize,
    max_page: usize,
    search_text: String,
    collection_filter: CollectionFilter,
    search_filter: SearchFilter,

    // Grid
    poster_entries: Vec<PosterEntry>,
    poster_handles: HashMap<i32, image::Handle>,

    // All entries for collection (before pagination)
    all_collection_entries: Vec<PosterEntry>,

    // Detail panel
    selected_tmdb_id: Option<i32>,
    selected_imdb_id: Option<String>,
    detail_poster_handle: Option<image::Handle>,
    detail_title: String,
    detail_original_title: String,
    detail_director: String,
    detail_release_date: String,
    detail_localized_release: String,
    detail_runtime_label: String,
    detail_runtime_value: Option<i32>,
    detail_imdb_url: String,
    detail_tmdb_url: String,
    detail_score: Option<f64>,
    detail_stored_score: Option<f64>,
    detail_score_change: ScoreChange,
    detail_seen_date: String,
    detail_seen_date_obj: Date,
    show_date_picker: bool,
    detail_comment: String,
    detail_viewable: bool,
    detail_available: bool,
    detail_is_in_db: bool,
    detail_has_runtime: bool,
    detail_is_non_theatrical: bool,

    // Score scan
    score_log: Vec<String>,
    score_progress: f32,
    score_scan_active: bool,

    // Status
    status_message: Option<String>,
}

pub type MovieDetails = (
    String,
    String,
    String,
    Option<String>,
    Option<String>,
    Option<i32>,
);

#[derive(Debug, Clone)]
pub enum Message {
    // Navigation
    PreviousPage,
    NextPage,
    ShowCollection,
    SearchTextChanged(String),
    SearchSubmit,
    CollectionFilterChanged(CollectionFilter),
    SearchFilterChanged(SearchFilter),

    // TMDb search results
    TmdbSearchCompleted(Result<Vec<PosterEntry>, String>),
    PosterLoaded(i32, Result<image::Handle, String>),

    // Collection results
    CollectionLoaded(Result<Vec<PosterEntry>, String>),

    // Grid interaction
    PosterClicked(i32, String),

    // Detail panel async results
    MovieDetailsLoaded(Result<MovieDetails, String>),
    CreditsLoaded(Result<String, String>),
    ReleasesLoaded(Result<String, String>),
    ImdbInfoLoaded(Result<(Option<f64>, Option<bool>), String>),
    DbMovieLoaded(Result<Option<MovieDB>, String>),

    // Detail panel actions
    AddMovie,
    MovieAdded(Result<(), String>),
    RefreshMovie,
    MovieRefreshed(Result<(), String>),
    DeleteMovie,
    MovieDeleted(Result<(), String>),
    ChooseSeenDate,
    SubmitSeenDate(Date),
    CancelSeenDate,
    SeenDateSaved(Result<(), String>),
    CommentChanged(String),
    ViewableToggled(bool),
    AvailableToggled(bool),
    OpenImdbLink,
    OpenTmdbLink,

    // Runtime detection
    DetectRuntime,
    RuntimeDetected(Result<i32, String>),

    // Score scan
    StartScoreScan,
    ScoreScanProgress(f32),
    ScoreScanFound(String),
    ScoreScanDone,

    // DB initialized
    DbInitialized(Result<(), String>),
}

impl TmdbShelf {
    pub fn title(&self) -> String {
        format!("{} {}", "TMDb Shelf", env!("CARGO_PKG_VERSION"))
    }

    pub fn new(config: AppConfig) -> (Self, Task<Message>) {
        let tmdb_client = TmdbClient::new(&config.api.key, &config.general.language);
        let imdb_client = ImdbClient::new();

        let db_path = config.db_path().to_string_lossy().to_string();

        let app = Self {
            config,
            tmdb_client,
            imdb_client,
            search_mode: SearchMode::TmdbSearch,
            current_page: 1,
            max_page: 1,
            search_text: String::new(),
            collection_filter: CollectionFilter::NotSeen,
            search_filter: SearchFilter::All,
            poster_entries: Vec::new(),
            poster_handles: HashMap::new(),
            all_collection_entries: Vec::new(),
            selected_tmdb_id: None,
            selected_imdb_id: None,
            detail_poster_handle: None,
            detail_title: String::new(),
            detail_original_title: String::new(),
            detail_director: String::new(),
            detail_release_date: String::new(),
            detail_localized_release: String::new(),
            detail_runtime_label: String::new(),
            detail_runtime_value: None,
            detail_imdb_url: String::new(),
            detail_tmdb_url: String::new(),
            detail_score: None,
            detail_stored_score: None,
            detail_score_change: ScoreChange::Unknown,
            detail_seen_date: String::new(),
            detail_seen_date_obj: Date::today(),
            show_date_picker: false,
            detail_comment: String::new(),
            detail_viewable: true,
            detail_available: true,
            detail_is_in_db: false,
            detail_has_runtime: false,
            detail_is_non_theatrical: false,
            score_log: Vec::new(),
            score_progress: 0.0,
            score_scan_active: false,
            status_message: None,
        };

        // Initialize DB (create table) on startup
        let init_task = Task::perform(
            async move {
                Db::open(&db_path).await.map_err(|e| e.to_string())?;
                Ok::<(), String>(())
            },
            Message::DbInitialized,
        );

        (app, init_task)
    }

    pub fn update(&mut self, message: Message) -> Task<Message> {
        match message {
            Message::DbInitialized(result) => {
                if let Err(e) = result {
                    self.status_message = Some(format!("DB init error: {}", e));
                }
                Task::none()
            }

            // === Navigation ===
            Message::PreviousPage => {
                if self.current_page > 1 {
                    self.current_page -= 1;
                    self.update_page_display()
                } else {
                    Task::none()
                }
            }
            Message::NextPage => {
                if self.current_page < self.max_page {
                    self.current_page += 1;
                    self.update_page_display()
                } else {
                    Task::none()
                }
            }

            // === Search ===
            Message::SearchTextChanged(text) => {
                self.search_text = text;
                Task::none()
            }
            Message::SearchSubmit => {
                if self.search_text.is_empty() {
                    return Task::none();
                }
                self.search_mode = SearchMode::TmdbSearch;
                self.current_page = 1;
                self.poster_entries.clear();
                self.poster_handles.clear();
                self.clear_detail();

                let client = self.tmdb_client.clone();
                let query = self.search_text.clone();
                let tmp_dir = std::env::temp_dir();

                Task::perform(
                    async move {
                        let mut all_results = Vec::new();

                        // Fetch 2 pages for up to 40 results
                        for page in 1..=2 {
                            match client.search_movie(&query, page).await {
                                Ok(results) => {
                                    for r in results.results {
                                        all_results.push(PosterEntry {
                                            tmdb_id: r.id,
                                            imdb_id: String::new(),
                                            title: r.title.unwrap_or_default(),
                                            original_title: r.original_title.unwrap_or_default(),
                                            release_date: r.release_date.unwrap_or_default(),
                                            run_time: None,
                                            is_non_theatrical: false,
                                            poster_path: r
                                                .poster_path
                                                .map(|p| tmp_dir.join(p.trim_start_matches('/'))),
                                        });
                                    }
                                    if results.page >= results.total_pages {
                                        break;
                                    }
                                }
                                Err(e) => return Err(e.to_string()),
                            }
                        }
                        Ok(all_results)
                    },
                    Message::TmdbSearchCompleted,
                )
            }
            Message::TmdbSearchCompleted(result) => match result {
                Ok(entries) => {
                    let max_items = crate::ui::theme::MAX_ITEMS;
                    self.max_page = entries.len().div_ceil(max_items).max(1);
                    self.all_collection_entries = entries;
                    self.current_page = 1;
                    self.update_page_display()
                }
                Err(e) => {
                    self.status_message = Some(format!("Search error: {}", e));
                    Task::none()
                }
            },

            // === Show collection ===
            Message::ShowCollection => {
                self.search_mode = SearchMode::Collection;
                self.current_page = 1;
                self.poster_entries.clear();
                self.poster_handles.clear();
                self.clear_detail();

                let collection_filter = self.collection_filter;
                let search_filter = self.search_filter;
                let search = self.search_text.clone();
                let poster_dir = self.config.poster_dir();
                let db_path = self.config.db_path().to_string_lossy().to_string();

                Task::perform(
                    async move {
                        let db = Db::open(&db_path).await.map_err(|e| e.to_string())?;
                        let movies = db
                            .filter(collection_filter, search_filter, &search)
                            .await
                            .map_err(|e| e.to_string())?;

                        let entries: Vec<PosterEntry> = movies
                            .into_iter()
                            .map(|m| {
                                let poster_path = poster_dir.join(format!("{}.jpg", m.tmdb_id));
                                let poster_path = if poster_path.exists() {
                                    Some(poster_path)
                                } else {
                                    None
                                };
                                PosterEntry {
                                    tmdb_id: m.tmdb_id,
                                    imdb_id: m.imdb_id,
                                    title: m.title,
                                    original_title: m.original_title,
                                    release_date: m.release_date,
                                    run_time: m.run_time,
                                    is_non_theatrical: m.is_non_theatrical,
                                    poster_path,
                                }
                            })
                            .collect();
                        Ok(entries)
                    },
                    Message::CollectionLoaded,
                )
            }
            Message::CollectionLoaded(result) => match result {
                Ok(entries) => {
                    let max_items = crate::ui::theme::MAX_ITEMS;
                    self.max_page = entries.len().div_ceil(max_items).max(1);
                    self.all_collection_entries = entries;
                    self.current_page = 1;
                    self.update_page_display()
                }
                Err(e) => {
                    self.status_message = Some(format!("Collection error: {}", e));
                    Task::none()
                }
            },

            Message::CollectionFilterChanged(f) => {
                self.collection_filter = f;
                Task::none()
            }
            Message::SearchFilterChanged(f) => {
                self.search_filter = f;
                Task::none()
            }

            // === Poster interaction ===
            Message::PosterClicked(tmdb_id, imdb_id) => {
                self.selected_tmdb_id = Some(tmdb_id);
                self.selected_imdb_id = Some(imdb_id.clone());
                self.detail_is_in_db = false;
                self.detail_has_runtime = false;
                self.detail_score = None;
                self.detail_stored_score = None;
                self.detail_score_change = ScoreChange::Unknown;
                self.detail_director = String::new();
                self.detail_localized_release = String::new();
                self.detail_seen_date = String::new();
                self.detail_seen_date_obj = Date::today();
                self.show_date_picker = false;
                self.detail_comment = String::new();
                self.detail_viewable = true;
                self.detail_available = true;

                // Set what we know from the PosterEntry
                if let Some(entry) = self.poster_entries.iter().find(|e| e.tmdb_id == tmdb_id) {
                    self.detail_title = entry.title.clone();
                    self.detail_original_title = entry.original_title.clone();
                    self.detail_release_date = entry.release_date.clone();
                    self.detail_runtime_value = entry.run_time;
                    self.detail_runtime_label = entry
                        .run_time
                        .map(|r| format!("{} min", r))
                        .unwrap_or_default();
                    self.detail_poster_handle = self.poster_handles.get(&tmdb_id).cloned();
                }

                self.detail_tmdb_url = format!("https://www.themoviedb.org/movie/{}", tmdb_id);

                let mut tasks = Vec::new();

                // Fetch full movie details from TMDb
                let client = self.tmdb_client.clone();
                tasks.push(Task::perform(
                    async move {
                        let movie = client.get_movie(tmdb_id).await.map_err(|e| e.to_string())?;
                        Ok((
                            movie.title.unwrap_or_default(),
                            movie.original_title.unwrap_or_default(),
                            movie.release_date.unwrap_or_default(),
                            movie.imdb_id.clone(),
                            movie.poster_path.clone(),
                            movie.runtime,
                        ))
                    },
                    Message::MovieDetailsLoaded,
                ));

                // Fetch credits
                let client = self.tmdb_client.clone();
                tasks.push(Task::perform(
                    async move {
                        let credits = client
                            .get_credits(tmdb_id)
                            .await
                            .map_err(|e| e.to_string())?;
                        Ok(tmdb::director_from_credits(&credits))
                    },
                    Message::CreditsLoaded,
                ));

                // Fetch releases
                let client = self.tmdb_client.clone();
                let lang = self.config.general.language.to_uppercase();
                tasks.push(Task::perform(
                    async move {
                        let releases = client
                            .get_releases(tmdb_id)
                            .await
                            .map_err(|e| e.to_string())?;
                        Ok(tmdb::localized_release(&releases, &lang).unwrap_or_default())
                    },
                    Message::ReleasesLoaded,
                ));

                // Fetch IMDB info
                if !imdb_id.is_empty() {
                    let imdb_client = self.imdb_client.clone();
                    let imdb_id_clone = imdb_id.clone();
                    tasks.push(Task::perform(
                        async move { Ok(imdb_client.get_info(&imdb_id_clone).await) },
                        |r: Result<(Option<f64>, Option<bool>), String>| Message::ImdbInfoLoaded(r),
                    ));
                }

                // Check if in DB
                let db_path = self.config.db_path().to_string_lossy().to_string();
                tasks.push(Task::perform(
                    async move {
                        let db = Db::open(&db_path).await.map_err(|e| e.to_string())?;
                        db.find_by_id(tmdb_id).await.map_err(|e| e.to_string())
                    },
                    Message::DbMovieLoaded,
                ));

                Task::batch(tasks)
            }

            Message::MovieDetailsLoaded(result) => {
                if let Ok((title, original_title, release_date, imdb_id, _poster_path, runtime)) =
                    result
                {
                    self.detail_title = title;
                    self.detail_original_title = original_title;
                    self.detail_release_date = release_date;
                    if let Some(ref id) = imdb_id
                        && !id.is_empty()
                    {
                        self.selected_imdb_id = Some(id.clone());
                        self.detail_imdb_url = format!("https://www.imdb.com/title/{}/", id);

                        // If we didn't already have IMDB info, fetch it now
                        if self.detail_score.is_none() {
                            let imdb_client = self.imdb_client.clone();
                            let id = id.clone();
                            return Task::perform(
                                async move { Ok(imdb_client.get_info(&id).await) },
                                |r: Result<(Option<f64>, Option<bool>), String>| {
                                    Message::ImdbInfoLoaded(r)
                                },
                            );
                        }
                    }
                    if let Some(rt) = runtime {
                        self.detail_runtime_value = Some(rt);
                        self.detail_runtime_label = format!("{} min", rt);
                    }
                }
                Task::none()
            }

            Message::CreditsLoaded(result) => {
                if let Ok(director) = result {
                    self.detail_director = director;
                }
                Task::none()
            }

            Message::ReleasesLoaded(result) => {
                if let Ok(release) = result {
                    self.detail_localized_release = release;
                }
                Task::none()
            }

            Message::ImdbInfoLoaded(result) => {
                if let Ok((rating, is_non_theatrical)) = result {
                    self.detail_score = rating;
                    self.detail_score_change =
                        ScoreChange::from_scores(self.detail_stored_score, rating);
                    let non_theatrical = is_non_theatrical.unwrap_or(false);
                    self.detail_is_non_theatrical = non_theatrical;
                    // Update the grid entry so dimming takes effect immediately
                    if let Some(tmdb_id) = self.selected_tmdb_id {
                        for entry in self
                            .poster_entries
                            .iter_mut()
                            .chain(self.all_collection_entries.iter_mut())
                        {
                            if entry.tmdb_id == tmdb_id {
                                entry.is_non_theatrical = non_theatrical;
                            }
                        }
                    }
                }
                Task::none()
            }

            Message::DbMovieLoaded(result) => {
                if let Ok(Some(movie)) = result {
                    self.detail_is_in_db = true;
                    self.detail_seen_date = movie.viewing_date.unwrap_or_default();
                    if let Some(date) = parse_date_str(&self.detail_seen_date) {
                        self.detail_seen_date_obj = date;
                    }
                    self.detail_comment = movie.comment;
                    self.detail_viewable = movie.viewable;
                    self.detail_available = movie.availability;
                    self.detail_stored_score = movie.imdb_score;
                    self.detail_has_runtime = movie.run_time.is_some();
                    self.detail_is_non_theatrical = movie.is_non_theatrical;
                    if let Some(rt) = movie.run_time {
                        self.detail_runtime_value = Some(rt);
                        self.detail_runtime_label = format!("{} min", rt);
                    }
                    self.detail_score_change =
                        ScoreChange::from_scores(movie.imdb_score, self.detail_score);
                } else {
                    self.detail_is_in_db = false;
                }
                Task::none()
            }

            // === Add movie ===
            Message::AddMovie => {
                let tmdb_id = match self.selected_tmdb_id {
                    Some(id) => id,
                    None => return Task::none(),
                };

                let movie = MovieDB {
                    tmdb_id,
                    release_date: self.detail_release_date.clone(),
                    title: self.detail_title.clone(),
                    original_title: self.detail_original_title.clone(),
                    director: self.detail_director.clone(),
                    add_date: chrono::Local::now().format("%Y-%m-%d").to_string(),
                    viewing_date: None,
                    availability: true,
                    imdb_id: self.selected_imdb_id.clone().unwrap_or_default(),
                    imdb_score: self.detail_score,
                    seen: false,
                    comment: String::new(),
                    viewable: true,
                    run_time: self.detail_runtime_value,
                    is_non_theatrical: self.detail_is_non_theatrical,
                };

                let db_path = self.config.db_path().to_string_lossy().to_string();
                let poster_dir = self.config.poster_dir();

                // Copy poster from tmp to poster_dir
                let poster_entry = self.poster_entries.iter().find(|e| e.tmdb_id == tmdb_id);
                let src_poster = poster_entry.and_then(|e| e.poster_path.clone());

                Task::perform(
                    async move {
                        // Copy poster
                        if let Some(src) = src_poster
                            && src.exists()
                        {
                            let dest = poster_dir.join(format!("{}.jpg", tmdb_id));
                            tokio::fs::copy(&src, &dest)
                                .await
                                .map_err(|e| e.to_string())?;
                        }
                        let db = Db::open(&db_path).await.map_err(|e| e.to_string())?;
                        db.insert(&movie).await.map_err(|e| e.to_string())?;
                        Ok(())
                    },
                    Message::MovieAdded,
                )
            }
            Message::MovieAdded(result) => {
                match result {
                    Ok(()) => {
                        self.detail_is_in_db = true;
                        self.status_message = Some("Registered".to_string());
                    }
                    Err(e) => {
                        self.status_message = Some(format!("Add failed: {}", e));
                    }
                }
                Task::none()
            }

            // === Refresh movie ===
            Message::RefreshMovie => {
                let tmdb_id = match self.selected_tmdb_id {
                    Some(id) => id,
                    None => return Task::none(),
                };
                let db_path = self.config.db_path().to_string_lossy().to_string();
                let score = self.detail_score;
                let comment = self.detail_comment.clone();
                let viewable = self.detail_viewable;
                let available = self.detail_available;
                let is_non_theatrical = self.detail_is_non_theatrical;

                Task::perform(
                    async move {
                        let db = Db::open(&db_path).await.map_err(|e| e.to_string())?;
                        db.refresh_movie(
                            tmdb_id,
                            score,
                            &comment,
                            viewable,
                            available,
                            is_non_theatrical,
                        )
                        .await
                        .map_err(|e| e.to_string())?;
                        Ok(())
                    },
                    Message::MovieRefreshed,
                )
            }
            Message::MovieRefreshed(result) => {
                match result {
                    Ok(()) => {
                        self.status_message = Some("Refreshed".to_string());
                    }
                    Err(e) => {
                        self.status_message = Some(format!("Refresh failed: {}", e));
                    }
                }
                Task::none()
            }

            // === Delete movie ===
            Message::DeleteMovie => {
                let tmdb_id = match self.selected_tmdb_id {
                    Some(id) => id,
                    None => return Task::none(),
                };
                let db_path = self.config.db_path().to_string_lossy().to_string();
                let poster_dir = self.config.poster_dir();

                Task::perform(
                    async move {
                        let db = Db::open(&db_path).await.map_err(|e| e.to_string())?;
                        db.delete(tmdb_id).await.map_err(|e| e.to_string())?;
                        // Remove poster
                        let poster = poster_dir.join(format!("{}.jpg", tmdb_id));
                        let _ = tokio::fs::remove_file(poster).await;
                        Ok(())
                    },
                    Message::MovieDeleted,
                )
            }
            Message::MovieDeleted(result) => {
                match result {
                    Ok(()) => {
                        self.detail_is_in_db = false;
                        self.status_message = Some("Deleted".to_string());
                    }
                    Err(e) => {
                        self.status_message = Some(format!("Delete failed: {}", e));
                    }
                }
                Task::none()
            }

            // === Seen date ===
            Message::ChooseSeenDate => {
                self.show_date_picker = true;
                Task::none()
            }
            Message::CancelSeenDate => {
                self.show_date_picker = false;
                Task::none()
            }
            Message::SubmitSeenDate(date) => {
                self.show_date_picker = false;
                self.detail_seen_date_obj = date;
                self.detail_seen_date =
                    format!("{:04}-{:02}-{:02}", date.year, date.month, date.day);

                let tmdb_id = match self.selected_tmdb_id {
                    Some(id) => id,
                    None => return Task::none(),
                };
                let date_str = self.detail_seen_date.clone();
                let db_path = self.config.db_path().to_string_lossy().to_string();

                Task::perform(
                    async move {
                        let db = Db::open(&db_path).await.map_err(|e| e.to_string())?;
                        db.update_seen_date(tmdb_id, &date_str)
                            .await
                            .map_err(|e| e.to_string())?;
                        Ok(())
                    },
                    Message::SeenDateSaved,
                )
            }
            Message::SeenDateSaved(result) => {
                match result {
                    Ok(()) => {
                        self.status_message = Some("Date saved".to_string());
                    }
                    Err(e) => {
                        self.status_message = Some(format!("Save date failed: {}", e));
                    }
                }
                Task::none()
            }

            // === Comment / checkboxes ===
            Message::CommentChanged(c) => {
                self.detail_comment = c;
                Task::none()
            }
            Message::ViewableToggled(v) => {
                self.detail_viewable = v;
                Task::none()
            }
            Message::AvailableToggled(v) => {
                self.detail_available = v;
                Task::none()
            }

            // === Links ===
            Message::OpenImdbLink => {
                let _ = open::that(&self.detail_imdb_url);
                Task::none()
            }
            Message::OpenTmdbLink => {
                let _ = open::that(&self.detail_tmdb_url);
                Task::none()
            }

            // === Runtime detection ===
            Message::DetectRuntime => {
                let mediainfo = self.config.tools.mediainfo_path.clone();
                let ffprobe = self.config.tools.ffprobe_path.clone();
                let db_path = self.config.db_path().to_string_lossy().to_string();
                let tmdb_id = self.selected_tmdb_id.unwrap_or(0);

                Task::perform(
                    async move {
                        let file = rfd::AsyncFileDialog::new()
                            .set_title("Select video file")
                            .pick_file()
                            .await;

                        let file = file.ok_or_else(|| "No file selected".to_string())?;
                        let path = file.path().to_path_buf();

                        let runtime = crate::runtime_detect::detect_runtime(
                            &path,
                            mediainfo.as_deref(),
                            ffprobe.as_deref(),
                        )
                        .await
                        .map_err(|e| e.to_string())?;

                        // Save to DB
                        let db = Db::open(&db_path).await.map_err(|e| e.to_string())?;
                        db.save_runtime(tmdb_id, runtime)
                            .await
                            .map_err(|e| e.to_string())?;

                        Ok(runtime)
                    },
                    Message::RuntimeDetected,
                )
            }
            Message::RuntimeDetected(result) => {
                match result {
                    Ok(minutes) => {
                        self.detail_runtime_value = Some(minutes);
                        self.detail_runtime_label = format!("{} min", minutes);
                        self.detail_has_runtime = true;
                        self.status_message = Some(format!("Runtime: {} min", minutes));
                    }
                    Err(e) => {
                        if e != "No file selected" {
                            self.status_message = Some(format!("Runtime error: {}", e));
                        }
                    }
                }
                Task::none()
            }
            // === Score scan ===
            Message::StartScoreScan => {
                if self.score_scan_active {
                    return Task::none();
                }
                self.score_scan_active = true;
                self.score_log.clear();
                self.score_progress = 0.0;

                let db_path = self.config.db_path().to_string_lossy().to_string();
                let imdb_client = self.imdb_client.clone();

                Task::run(
                    iced::stream::channel(
                        100,
                        move |mut sender: iced::futures::channel::mpsc::Sender<Message>| async move {
                            let db = match Db::open(&db_path).await {
                                Ok(db) => db,
                                Err(e) => {
                                    let _ = sender
                                        .send(Message::ScoreScanFound(format!("Error: {}", e)))
                                        .await;
                                    let _ = sender.send(Message::ScoreScanDone).await;
                                    return;
                                }
                            };
                            let movies = match db.all_unseen_available().await {
                                Ok(m) => m,
                                Err(e) => {
                                    let _ = sender
                                        .send(Message::ScoreScanFound(format!("Error: {}", e)))
                                        .await;
                                    let _ = sender.send(Message::ScoreScanDone).await;
                                    return;
                                }
                            };

                            let total = movies.len() as f32;
                            let max_items = crate::ui::theme::MAX_ITEMS;
                            let grid_cols = crate::ui::theme::GRID_COLUMNS;
                            let mut found_any = false;

                            for (i, movie) in movies.iter().enumerate() {
                                if !movie.imdb_id.is_empty() {
                                    let live_score = imdb_client.get_score(&movie.imdb_id).await;
                                    if live_score != movie.imdb_score {
                                        let page = i / max_items + 1;
                                        let index_in_page = i % max_items;
                                        let row = index_in_page / grid_cols + 1;
                                        let col = index_in_page % grid_cols + 1;
                                        let _ = sender
                                            .send(Message::ScoreScanFound(format!(
                                                "{} ({}:{}:{})",
                                                movie.title, page, row, col,
                                            )))
                                            .await;
                                        found_any = true;
                                    }
                                }
                                let progress = (i + 1) as f32 / total;
                                let _ = sender.send(Message::ScoreScanProgress(progress)).await;
                            }

                            if !found_any {
                                let _ = sender
                                    .send(Message::ScoreScanFound(
                                        "No score changes found.".to_string(),
                                    ))
                                    .await;
                            }
                            let _ = sender.send(Message::ScoreScanDone).await;
                        },
                    ),
                    |msg| msg,
                )
            }
            Message::ScoreScanProgress(progress) => {
                self.score_progress = progress;
                Task::none()
            }
            Message::ScoreScanFound(entry) => {
                self.score_log.push(entry);
                Task::none()
            }
            Message::ScoreScanDone => {
                self.score_progress = 1.0;
                self.score_scan_active = false;
                Task::none()
            }

            Message::PosterLoaded(tmdb_id, result) => {
                if let Ok(handle) = result {
                    self.poster_handles.insert(tmdb_id, handle);
                }
                Task::none()
            }
        }
    }

    fn update_page_display(&mut self) -> Task<Message> {
        let max_items = crate::ui::theme::MAX_ITEMS;
        let start = (self.current_page - 1) * max_items;
        let end = (start + max_items).min(self.all_collection_entries.len());

        self.poster_entries = if start < self.all_collection_entries.len() {
            self.all_collection_entries[start..end].to_vec()
        } else {
            Vec::new()
        };

        // Load posters for current page
        let mut tasks = Vec::new();
        let client = self.tmdb_client.clone();
        let tmp_dir = std::env::temp_dir();

        for entry in &self.poster_entries {
            let tmdb_id = entry.tmdb_id;
            if self.poster_handles.contains_key(&tmdb_id) {
                continue;
            }

            if let Some(ref poster_path) = entry.poster_path {
                let path = poster_path.clone();
                if path.exists() {
                    // Load from local file
                    tasks.push(Task::perform(
                        async move {
                            let bytes = tokio::fs::read(&path).await.map_err(|e| e.to_string())?;
                            Ok(image::Handle::from_bytes(bytes))
                        },
                        move |result: Result<image::Handle, String>| {
                            Message::PosterLoaded(tmdb_id, result)
                        },
                    ));
                } else {
                    // Download from TMDb
                    let filename = path
                        .file_name()
                        .and_then(|n| n.to_str())
                        .unwrap_or("")
                        .to_string();
                    if !filename.is_empty() {
                        let client = client.clone();
                        let tmp = tmp_dir.clone();
                        tasks.push(Task::perform(
                            async move {
                                let poster_api_path = format!("/{}", filename);
                                let downloaded = client
                                    .download_poster(&poster_api_path, &tmp)
                                    .await
                                    .map_err(|e| e.to_string())?;
                                let bytes = tokio::fs::read(&downloaded)
                                    .await
                                    .map_err(|e| e.to_string())?;
                                Ok(image::Handle::from_bytes(bytes))
                            },
                            move |result: Result<image::Handle, String>| {
                                Message::PosterLoaded(tmdb_id, result)
                            },
                        ));
                    }
                }
            }
        }

        if tasks.is_empty() {
            Task::none()
        } else {
            Task::batch(tasks)
        }
    }

    fn clear_detail(&mut self) {
        self.selected_tmdb_id = None;
        self.selected_imdb_id = None;
        self.detail_poster_handle = None;
        self.detail_title.clear();
        self.detail_original_title.clear();
        self.detail_director.clear();
        self.detail_release_date.clear();
        self.detail_localized_release.clear();
        self.detail_runtime_label.clear();
        self.detail_runtime_value = None;
        self.detail_imdb_url.clear();
        self.detail_tmdb_url.clear();
        self.detail_score = None;
        self.detail_stored_score = None;
        self.detail_score_change = ScoreChange::Unknown;
        self.detail_seen_date.clear();
        self.detail_seen_date_obj = Date::today();
        self.show_date_picker = false;
        self.detail_comment.clear();
        self.detail_viewable = true;
        self.detail_available = true;
        self.detail_is_in_db = false;
        self.detail_has_runtime = false;
        self.detail_is_non_theatrical = false;
    }

    pub fn view(&self) -> Element<'_, Message> {
        let toolbar_state = toolbar::ToolbarState {
            current_page: self.current_page,
            max_page: self.max_page,
            search_text: &self.search_text,
            collection_filter: self.collection_filter,
            search_filter: self.search_filter,
        };

        let toolbar_view = toolbar::view(&toolbar_state);

        let grid_view = poster_grid::view(&self.poster_entries, &self.poster_handles);

        let detail_state = details_panel::DetailState {
            poster_handle: &self.detail_poster_handle,
            title: &self.detail_title,
            original_title: &self.detail_original_title,
            director: &self.detail_director,
            release_date: &self.detail_release_date,
            localized_release: &self.detail_localized_release,
            runtime_label: &self.detail_runtime_label,
            imdb_url: &self.detail_imdb_url,
            tmdb_url: &self.detail_tmdb_url,
            score: self.detail_score,
            score_change: self.detail_score_change,
            seen_date: &self.detail_seen_date,
            seen_date_obj: self.detail_seen_date_obj,
            show_date_picker: self.show_date_picker,
            comment: &self.detail_comment,
            viewable: self.detail_viewable,
            available: self.detail_available,
            is_in_db: self.detail_is_in_db,
            has_runtime: self.detail_has_runtime,
            has_selection: self.selected_tmdb_id.is_some(),
        };

        let detail_view = details_panel::view(&detail_state);
        let score_view =
            score_log::view(&self.score_log, self.score_progress, self.score_scan_active);

        let right_panel = column![detail_view, score_view]
            .spacing(8)
            .width(Length::Fixed(crate::ui::theme::SIDEBAR_WIDTH));

        let main_content = row![
            scrollable(grid_view)
                .width(Length::Fill)
                .height(Length::Fill),
            container(right_panel)
                .width(Length::Fixed(crate::ui::theme::SIDEBAR_WIDTH))
                .height(Length::Fill)
                .padding(4),
        ]
        .height(Length::Fill);

        let mut layout = column![toolbar_view, main_content];

        if let Some(ref msg) = self.status_message {
            layout = layout.push(container(text(msg).size(12)).padding(4).width(Length::Fill));
        }

        container(layout)
            .width(Length::Fill)
            .height(Length::Fill)
            .style(|_theme: &iced::Theme| container::Style {
                background: Some(iced::Background::Color(crate::ui::theme::BACKGROUND_COLOR)),
                ..container::Style::default()
            })
            .into()
    }

    pub fn subscription(&self) -> Subscription<Message> {
        Subscription::none()
    }
}

fn parse_date_str(s: &str) -> Option<Date> {
    let parts: Vec<&str> = s.split('-').collect();
    if parts.len() == 3
        && let (Ok(y), Ok(m), Ok(d)) = (parts[0].parse(), parts[1].parse(), parts[2].parse())
    {
        return Some(Date::from_ymd(y, m, d));
    }
    None
}
