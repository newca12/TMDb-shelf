use iced::Color;

/// Light yellow background color (#fffef0)
pub const BACKGROUND_COLOR: Color = Color {
    r: 1.0,
    g: 254.0 / 255.0,
    b: 240.0 / 255.0,
    a: 1.0,
};

/// Returns a shadow color based on movie runtime in minutes.
/// Purple: < 75min, Red: 75-89, Yellow: 90-94, Black: >= 95, White: unknown
pub fn runtime_shadow_color(runtime: Option<i32>) -> Color {
    match runtime {
        None => Color::WHITE,
        Some(r) if r < 75 => Color::from_rgb(0.58, 0.0, 0.83), // purple
        Some(r) if r < 90 => Color::from_rgb(0.9, 0.1, 0.1),   // red
        Some(r) if r < 95 => Color::from_rgb(0.9, 0.85, 0.0),  // yellow
        Some(_) => Color::BLACK,
    }
}

pub const SIDEBAR_WIDTH: f32 = 300.0;
pub const POSTER_WIDTH: f32 = 75.0;
pub const POSTER_HEIGHT: f32 = 108.0;
pub const GRID_COLUMNS: usize = 8;
pub const GRID_ROWS: usize = 5;
pub const MAX_ITEMS: usize = GRID_COLUMNS * GRID_ROWS;
pub const SHELF_ROW_HEIGHT: f32 = 144.0;
pub const LARGE_POSTER_WIDTH: f32 = 231.0;
