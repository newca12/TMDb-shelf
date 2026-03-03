use std::sync::LazyLock;

use iced::widget::{button, column, container, image, row, stack, text};
use iced::{ContentFit, Element, Length, Padding, Shadow, Vector, alignment};

use crate::app::Message;
use crate::models::PosterEntry;
use crate::ui::theme::{self, GRID_COLUMNS, POSTER_HEIGHT, POSTER_WIDTH, SHELF_ROW_HEIGHT};

const SHELF_BYTES: &[u8] = include_bytes!("../../assets/Shelf.png");

/// Pre-computed tiled shelf image: 4-row source tiled to 5 rows (1024x720 RGBA).
static SHELF_TILED: LazyLock<(u32, u32, Vec<u8>)> = LazyLock::new(|| {
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

    (w, target_h, pixels)
});

pub fn view<'a>(
    entries: &'a [PosterEntry],
    poster_handles: &'a std::collections::HashMap<i32, image::Handle>,
) -> Element<'a, Message> {
    let mut rows_vec: Vec<Element<'a, Message>> = Vec::new();

    for chunk in entries.chunks(GRID_COLUMNS) {
        let mut row_items: Vec<Element<'a, Message>> = Vec::new();

        for entry in chunk {
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

            let shadow_color = theme::runtime_shadow_color(entry.run_time);

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
                    shadow: Shadow {
                        color: shadow_color,
                        offset: Vector::new(4.0, -4.0),
                        blur_radius: 5.0,
                    },
                    ..button::Style::default()
                });

            let item: Element<'a, Message> = container(poster_btn)
                .width(Length::Fill)
                .center_x(Length::Fill)
                .into();

            row_items.push(item);
        }

        // Pad with empty containers if row is incomplete
        for _ in chunk.len()..GRID_COLUMNS {
            row_items.push(
                container(text(""))
                    .width(Length::Fill)
                    .height(Length::Fixed(POSTER_HEIGHT))
                    .into(),
            );
        }

        // Bottom-align posters within the shelf row, raised 10px above the ledge
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

    // Create shelf background from pre-tiled RGBA pixels
    let (w, h, ref pixels) = *SHELF_TILED;
    let shelf_handle = image::Handle::from_rgba(w, h, pixels.clone());
    let shelf_bg = image(shelf_handle)
        .width(Length::Fill)
        .height(Length::Fill)
        .content_fit(ContentFit::Fill);

    stack![shelf_bg, grid].into()
}
