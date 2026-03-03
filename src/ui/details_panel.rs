use iced::widget::{button, checkbox, column, container, image, row, scrollable, text, text_input};
use iced::{Element, Length};
use iced_aw::date_picker::Date;
use iced_aw::helpers::date_picker;
use iced_aw::style::colors::BLACK;

use crate::app::Message;
use crate::models::ScoreChange;
use crate::ui::theme::LARGE_POSTER_WIDTH;

pub struct DetailState<'a> {
    pub poster_handle: &'a Option<image::Handle>,
    pub title: &'a str,
    pub original_title: &'a str,
    pub director: &'a str,
    pub release_date: &'a str,
    pub localized_release: &'a str,
    pub runtime_label: &'a str,
    pub imdb_url: &'a str,
    pub tmdb_url: &'a str,
    pub score: Option<f64>,
    pub score_change: ScoreChange,
    pub seen_date: &'a str,
    pub seen_date_obj: Date,
    pub show_date_picker: bool,
    pub comment: &'a str,
    pub viewable: bool,
    pub available: bool,
    pub is_in_db: bool,
    pub has_runtime: bool,
    pub has_selection: bool,
}

pub fn view<'a>(state: &DetailState<'a>) -> Element<'a, Message> {
    let mut content = column![].spacing(8).padding(12).width(Length::Fill);

    // Large poster
    if let Some(handle) = state.poster_handle {
        content = content.push(
            container(image(handle.clone()).width(Length::Fixed(LARGE_POSTER_WIDTH)))
                .center_x(Length::Fill),
        );
    } else {
        content = content.push(
            container(text("No poster"))
                .width(Length::Fill)
                .height(Length::Fixed(200.0))
                .center_x(Length::Fill)
                .center_y(Length::Fixed(200.0)),
        );
    }

    // Action buttons
    let add_btn = button(text("Add"))
        .on_press_maybe(if state.has_selection && !state.is_in_db {
            Some(Message::AddMovie)
        } else {
            None
        })
        .padding(6);

    let refresh_btn = button(text("Refresh"))
        .on_press_maybe(if state.has_selection && state.is_in_db {
            Some(Message::RefreshMovie)
        } else {
            None
        })
        .padding(6);

    let delete_btn = button(text("Delete"))
        .on_press_maybe(if state.has_selection && state.is_in_db {
            Some(Message::DeleteMovie)
        } else {
            None
        })
        .padding(6);

    content = content.push(row![add_btn, refresh_btn, delete_btn].spacing(8));

    // Title info
    if !state.title.is_empty() {
        content = content.push(text(state.title).size(16));
    }
    if !state.original_title.is_empty() && state.original_title != state.title {
        content = content.push(text(state.original_title).size(13));
    }
    if !state.director.is_empty() {
        content = content.push(text(format!("Director: {}", state.director)).size(13));
    }
    if !state.release_date.is_empty() {
        content = content.push(text(format!("Release: {}", state.release_date)).size(13));
    }
    if !state.localized_release.is_empty() {
        content =
            content.push(text(format!("Local release: {}", state.localized_release)).size(13));
    }

    // Runtime button
    let runtime_text = if state.runtime_label.is_empty() {
        "Runtime"
    } else {
        state.runtime_label
    };
    let runtime_btn = button(text(runtime_text).size(13).color(if state.is_in_db {
        BLACK
    } else {
        iced::Color::from_rgb(0.5, 0.5, 0.5)
    }))
    .on_press_maybe(
        if state.has_selection && state.is_in_db && !state.has_runtime {
            Some(Message::DetectRuntime)
        } else {
            None
        },
    )
    .padding(4);
    content = content.push(runtime_btn);

    // Viewing date (date picker)
    let date_label = if state.seen_date.is_empty() {
        "Seen date".to_string()
    } else {
        format!("Seen: {}", state.seen_date)
    };
    let date_btn = button(text(date_label).size(13))
        .on_press_maybe(if state.has_selection && state.is_in_db {
            Some(Message::ChooseSeenDate)
        } else {
            None
        })
        .padding(4);
    content = content.push(date_picker(
        state.show_date_picker,
        state.seen_date_obj,
        date_btn,
        Message::CancelSeenDate,
        Message::SubmitSeenDate,
    ));

    // IMDB score
    if let Some(score) = state.score {
        let score_text = format!("IMDB: {:.1} {}", score, state.score_change.symbol());
        content = content.push(
            row![
                text(score_text).size(14),
                button(text("Open IMDB").size(11))
                    .on_press_maybe(if !state.imdb_url.is_empty() {
                        Some(Message::OpenImdbLink)
                    } else {
                        None
                    })
                    .padding(2),
            ]
            .spacing(8)
            .align_y(iced::Alignment::Center),
        );
    }

    // TMDb link
    if !state.tmdb_url.is_empty() {
        content = content.push(
            button(text("Open TMDb").size(11))
                .on_press(Message::OpenTmdbLink)
                .padding(2),
        );
    }

    // Comment
    let comment_input = text_input("Comment...", state.comment)
        .on_input(Message::CommentChanged)
        .width(Length::Fill);
    content = content.push(comment_input);

    // Checkboxes
    let viewable_cb = checkbox(state.viewable)
        .label("Viewable")
        .on_toggle(Message::ViewableToggled);
    let available_cb = checkbox(state.available)
        .label("Available")
        .on_toggle(Message::AvailableToggled);
    content = content.push(row![viewable_cb, available_cb].spacing(16));

    scrollable(content).height(Length::Fill).into()
}
