use std::sync::LazyLock;

use iced::widget::{button, column, container, image, row, stack, text};
use iced::{Border, ContentFit, Element, Length, Padding, alignment};

use crate::app::Message;
use crate::models::PosterEntry;
use crate::ui::theme::{self, GRID_COLUMNS, POSTER_HEIGHT, POSTER_WIDTH, SHELF_ROW_HEIGHT};

const SHELF_BYTES: &[u8] = include_bytes!("../../assets/Shelf.png");

/// Pre-computed tiled shelf background handle, created once and reused across frames.
/// Storing the Handle in persistent state (rather than recreating it in view()) prevents
/// flickering caused by iced 0.14's async image decoding (see iced#3173).
static SHELF_HANDLE: LazyLock<image::Handle> = LazyLock::new(|| {
    let img = ::image::load_from_memory(SHELF_BYTES)
        .expect("Failed to decode Shelf.png")
        .to_rgba8();
    let (w, h) = img.dimensions(); // 1024 x 576
    let row_h = h / 4; // 144px per shelf row
    let target_h = row_h * 5; // 720px for 5 rows
    let src = img.as_raw();
    let stride = (w * 4) as usize;

    let mut pixels = vec![0u8; (w * target_h * 4) as usize];
    for y in 0..target_h {
        let src_y = y % h; // tile by wrapping around source image
        let src_offset = src_y as usize * stride;
        let dst_offset = y as usize * stride;
        pixels[dst_offset..dst_offset + stride]
            .copy_from_slice(&src[src_offset..src_offset + stride]);
    }

    image::Handle::from_rgba(w, target_h, pixels)
});

pub fn view<'a>(
    entries: &'a [PosterEntry],
    poster_handles: &'a std::collections::HashMap<i32, image::Handle>,
) -> Element<'a, Message> {
    let mut rows_vec: Vec<Element<'a, Message>> = Vec::new();

    for row_idx in 0..theme::GRID_ROWS {
        let mut row_items: Vec<Element<'a, Message>> = Vec::new();
        let row_start = row_idx * GRID_COLUMNS;

        for col_idx in 0..GRID_COLUMNS {
            let flat_idx = row_start + col_idx;

            if let Some(entry) = entries.get(flat_idx) {
                let poster_widget: Element<'a, Message> =
                    if let Some(handle) = poster_handles.get(&entry.tmdb_id) {
                        image(handle.clone())
                            .width(Length::Fixed(POSTER_WIDTH))
                            .height(Length::Fixed(POSTER_HEIGHT))
                            .content_fit(ContentFit::Contain)
                            .into()
                    } else {
                        container(text("No image").size(10))
                            .width(Length::Fixed(POSTER_WIDTH))
                            .height(Length::Fixed(POSTER_HEIGHT))
                            .center_x(Length::Fixed(POSTER_WIDTH))
                            .center_y(Length::Fixed(POSTER_HEIGHT))
                            .into()
                    };

                // Use a colored border instead of a shadow for runtime indication.
                // iced's damage tracking computes dirty regions from quad.bounds.expand(1.0),
                // which doesn't cover the shadow's blur/offset extent. This causes shadow
                // pixels to persist across page changes. Borders stay within quad bounds
                // and are properly tracked by the damage system.
                let border_color = theme::runtime_border_color(entry.run_time);

                let tmdb_id = entry.tmdb_id;
                let imdb_id = entry.imdb_id.clone();

                let poster_content: Element<'a, Message> = if entry.is_non_theatrical {
                    let dim_overlay = container(text(""))
                        .width(Length::Fixed(POSTER_WIDTH))
                        .height(Length::Fixed(POSTER_HEIGHT))
                        .style(|_theme: &iced::Theme| container::Style {
                            background: Some(iced::Background::Color(iced::Color {
                                r: 0.0,
                                g: 0.0,
                                b: 0.0,
                                a: 0.7,
                            })),
                            ..container::Style::default()
                        });
                    stack![poster_widget, dim_overlay].into()
                } else {
                    poster_widget
                };

                let poster_btn = button(poster_content)
                    .on_press(Message::PosterClicked(tmdb_id, imdb_id))
                    .padding(0)
                    .style(move |_theme: &iced::Theme, _status| button::Style {
                        border: Border {
                            color: border_color,
                            width: 2.0,
                            radius: 0.into(),
                        },
                        ..button::Style::default()
                    });

                let item: Element<'a, Message> = container(poster_btn)
                    .width(Length::Fill)
                    .center_x(Length::Fill)
                    .into();

                row_items.push(item);
            } else {
                row_items.push(
                    container(text(""))
                        .width(Length::Fill)
                        .height(Length::Fixed(POSTER_HEIGHT))
                        .into(),
                );
            }
        }

        // Bottom-align posters within the shelf row, raised above the ledge
        let poster_row = row(row_items).spacing(0);
        let aligned_row: Element<'a, Message> = container(poster_row)
            .height(Length::Fixed(SHELF_ROW_HEIGHT))
            .align_y(alignment::Vertical::Bottom)
            .padding(Padding {
                top: 0.0,
                right: 0.0,
                bottom: 18.0,
                left: 0.0,
            })
            .into();

        rows_vec.push(aligned_row);
    }

    // No vertical padding so grid height (5 x 144 = 720px) matches tiled shelf exactly
    let grid = column(rows_vec).spacing(0).padding(Padding {
        top: 0.0,
        right: 8.0,
        bottom: 0.0,
        left: 8.0,
    });

    // Reuse the pre-computed shelf handle (never recreated, so no async-decode flicker)
    let shelf_bg = image(SHELF_HANDLE.clone())
        .width(Length::Fill)
        .height(Length::Fill)
        .content_fit(ContentFit::Fill);

    stack![shelf_bg, grid].into()
}
