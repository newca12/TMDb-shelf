# TMDb-shelf

![Image](./screenshot.png?raw=true)

### About
tmdb-shelf is a desktop application written in Rust for managing a personal movie collection using [The Movie Database][1] (TMDb).
It is built heavily on:
* [Iced][2] - A cross-platform GUI library
* [libsql][3] - Async SQLite database
* [Tokio][4] - Async runtime

tmdb-shelf is an EDLA project.

The purpose of [edla.org](http://www.edla.org) is to promote the state of the art in various domains.

This is a Rust rewrite of the original [Scala/JavaFX TMDb-shelf][5].

### Requirements
1. Rust 2024 edition
2. Optional: [mediainfo](https://mediainfo.sourceforge.net/) or [ffprobe](https://ffmpeg.org/) for video file runtime detection

### API Key
You will need an API key to The Movie Database to access the API. To obtain a key, follow these steps:

1. Register for and verify an [account](https://www.themoviedb.org/account/signup).
2. [Log](https://www.themoviedb.org/login) into your account.
3. Select the API section on left side of your account page.
4. Click on the link to generate a new API key and follow the instructions.



### Installation

```
cargo install --locked tmdb-shelf
```

On first launch, a configuration file is created at `~/.tmdb-shelf/config.toml`. Edit it to set your TMDb API key, then relaunch.

### Configuration

The configuration file (`~/.tmdb-shelf/config.toml`) supports the following options:

```toml
[api]
key = "your-tmdb-api-key"

[general]
language = "en"              # ISO 639-1 language code for TMDb queries
# data_dir = "/custom/path"  # Custom data directory (default: ~/.tmdb-shelf)

[tools]
# mediainfo_path = "/usr/bin/mediainfo"
# ffprobe_path = "/usr/bin/ffprobe"
```

### Features

- Search movies on TMDb and add them to your personal collection
- Browse your collection with filtering (Not Seen, Seen, All, Not Available, Not Viewable)
- Search within your collection by director or movie title
- View movie details: poster, title, director, release dates, runtime, IMDb score
- Track viewing dates and personal comments
- Detect video file runtime via mediainfo or ffprobe
- Scan for IMDb score changes across your collection
- Color-coded poster drop shadow based on runtime (purple < 75 min, red 75-89, yellow 90-94, black >= 95)
- Non-theatrical content (TV movies, shorts, etc.) visually dimmed in the grid

### Documentation

See the [docs/](docs/) folder for detailed documentation:
- [Architecture](docs/ARCHITECTURE.md) - System design and module structure
- [Developing](docs/DEVELOPING.md) - Development setup and guidelines
- [Database](docs/DATABASE.md) - Database schema and migration from H2
- [API Integration](docs/API.md) - TMDb and IMDb API usage
- [AI Agents](docs/AGENTS.md) - Guidelines for AI coding agents

### License
(C) 2025-2026 Olivier ROLAND. Distributed under the GPLv3 License.

[1]: https://www.themoviedb.org/
[2]: https://iced.rs/
[3]: https://github.com/tursodatabase/libsql
[4]: https://tokio.rs/
[5]: https://github.com/newca12/TMDb-shelf/tree/scala-legacy
