use std::process::Command;
use std::thread;

const LINUX_BROWSERS: &[&str] = &[
    "firefox",
    "google-chrome",
    "chromium",
    "brave",
    "librewolf",
    "epiphany",
    "x-www-browser",
];

/// Attempts to open a URL in a browser, prioritizing the local Linux / NixOS browser.
pub fn open_url(url: &str) {
    let url = url.trim();
    if url.is_empty() {
        return;
    }

    // 1. Try $BROWSER environment variable if set
    if let Ok(browser_env) = std::env::var("BROWSER") {
        for candidate in browser_env.split(':') {
            let candidate = candidate.trim();
            if !candidate.is_empty() {
                let mut parts = candidate.split_whitespace();
                if let Some(bin) = parts.next() {
                    let mut cmd = Command::new(bin);
                    let mut has_url_arg = false;
                    for arg in parts {
                        if arg == "%s" {
                            cmd.arg(url);
                            has_url_arg = true;
                        } else {
                            cmd.arg(arg);
                        }
                    }
                    if !has_url_arg {
                        cmd.arg(url);
                    }
                    if spawn_detached(cmd) {
                        return;
                    }
                }
            }
        }
    }

    // 2. Query default scheme handler via xdg-mime (e.g. "firefox.desktop")
    if let Ok(output) = Command::new("xdg-mime")
        .args(["query", "default", "x-scheme-handler/https"])
        .output()
    {
        let stdout = String::from_utf8_lossy(&output.stdout);
        let handler = stdout.trim();
        if !handler.is_empty() {
            let bin_name = handler.strip_suffix(".desktop").unwrap_or(handler);
            let mut cmd = Command::new(bin_name);
            cmd.arg(url);
            if spawn_detached(cmd) {
                return;
            }
        }
    }

    // 3. Try standard Linux desktop browsers in PATH
    for &browser in LINUX_BROWSERS {
        let mut cmd = Command::new(browser);
        cmd.arg(url);
        if spawn_detached(cmd) {
            return;
        }
    }

    // 4. Try xdg-open with DE=generic to prevent it from redirecting to Windows on WSL
    let mut xdg_cmd = Command::new("xdg-open");
    xdg_cmd.env("DE", "generic").arg(url);
    if spawn_detached(xdg_cmd) {
        return;
    }

    // 5. Try gio open
    let mut gio_cmd = Command::new("gio");
    gio_cmd.args(["open", url]);
    if spawn_detached(gio_cmd) {
        return;
    }

    // 6. Fallback to open::that
    if let Ok(()) = open::that(url) {
        return;
    }

    eprintln!("Failed to open URL in browser: {}", url);
}

fn spawn_detached(mut cmd: Command) -> bool {
    match cmd.spawn() {
        Ok(mut child) => {
            thread::spawn(move || {
                let _ = child.wait();
            });
            true
        }
        Err(_) => false,
    }
}
