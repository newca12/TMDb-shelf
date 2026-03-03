use iced::widget::{Space, column, container, row, scrollable, text};
use iced::{Background, Color, Element, Length};

use crate::app::Message;

const BAR_HEIGHT: f32 = 150.0;
const BAR_WIDTH: f32 = 16.0;

fn vertical_progress_bar(progress: f32) -> Element<'static, Message> {
    let clamped = progress.clamp(0.0, 1.0);
    let filled_h = clamped * BAR_HEIGHT;
    let empty_h = BAR_HEIGHT - filled_h;

    let bar_color = Color::from_rgb(0.26, 0.63, 0.88); // steel blue
    let track_color = Color::from_rgb(0.85, 0.85, 0.85);

    let empty_part: Element<'_, Message> = container(Space::new().width(BAR_WIDTH).height(empty_h))
        .style(move |_theme: &iced::Theme| container::Style {
            background: Some(Background::Color(track_color)),
            ..container::Style::default()
        })
        .into();

    let filled_part: Element<'_, Message> =
        container(Space::new().width(BAR_WIDTH).height(filled_h))
            .style(move |_theme: &iced::Theme| container::Style {
                background: Some(Background::Color(bar_color)),
                ..container::Style::default()
            })
            .into();

    column![empty_part, filled_part]
        .width(Length::Fixed(BAR_WIDTH))
        .into()
}

pub fn view<'a>(log: &'a [String], progress: f32, active: bool) -> Element<'a, Message> {
    let mut content = column![].spacing(4).width(Length::Fill);

    if active || !log.is_empty() {
        content = content.push(text("Score changes:").size(13));

        let mut log_column = column![].spacing(2);
        for entry in log {
            log_column = log_column.push(text(entry).size(11));
        }
        let list_view = scrollable(log_column).height(Length::Fixed(BAR_HEIGHT));

        let bar = vertical_progress_bar(progress);

        content = content.push(row![list_view, bar].spacing(4).width(Length::Fill));
    }

    content.into()
}
