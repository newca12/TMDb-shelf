use iced::widget::{button, pick_list, row, text, text_input};
use iced::{Element, Length};

use crate::app::Message;
use crate::models::{CollectionFilter, SearchFilter};

pub struct ToolbarState<'a> {
    pub current_page: usize,
    pub max_page: usize,
    pub search_text: &'a str,
    pub collection_filter: CollectionFilter,
    pub search_filter: SearchFilter,
}

pub fn view<'a>(state: &ToolbarState<'a>) -> Element<'a, Message> {
    let prev_btn = button(text("<"))
        .on_press_maybe(if state.current_page > 1 {
            Some(Message::PreviousPage)
        } else {
            None
        })
        .padding(8);

    let page_label = text(format!("{}/{}", state.current_page, state.max_page.max(1)));

    let next_btn = button(text(">"))
        .on_press_maybe(if state.current_page < state.max_page {
            Some(Message::NextPage)
        } else {
            None
        })
        .padding(8);

    let show_collection_btn = button(text("Show collection"))
        .on_press(Message::ShowCollection)
        .padding(8);

    let collection_filter = pick_list(
        CollectionFilter::ALL,
        Some(state.collection_filter),
        Message::CollectionFilterChanged,
    );

    let search_input = text_input("Search...", state.search_text)
        .on_input(Message::SearchTextChanged)
        .on_submit(Message::SearchSubmit)
        .width(Length::Fixed(200.0));

    let search_filter = pick_list(
        SearchFilter::ALL,
        Some(state.search_filter),
        Message::SearchFilterChanged,
    );

    let search_btn = button(text("Search"))
        .on_press(Message::SearchSubmit)
        .padding(8);

    let score_scan_btn = button(text("Find changed scores"))
        .on_press(Message::StartScoreScan)
        .padding(8);

    row![
        prev_btn,
        page_label,
        next_btn,
        show_collection_btn,
        collection_filter,
        search_input,
        search_filter,
        search_btn,
        score_scan_btn,
    ]
    .spacing(8)
    .padding(8)
    .align_y(iced::Alignment::Center)
    .into()
}
