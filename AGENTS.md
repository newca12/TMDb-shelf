# Instructions for AI Coding Agents

Welcome! This repository uses a highly structured, declarative development environment. To prevent execution failures, linker crashes, or tool failures, you **must** adhere to the instructions below.

---

## 1. System Environment: NixOS WSL
The host environment is **NixOS running inside Windows Subsystem for Linux (WSL)**. 
- Traditional FHS assumptions do NOT apply. Files and libraries are versioned under `/nix/store/` instead of `/lib` or `/usr/include`.
- Standard pre-compiled glibc binaries will fail to execute unless run under `nix-ld` (which is enabled on this system as a general fallback for libraries like `openssl` and `zlib`).

---

## 2. Project Environment: Nix Flakes + direnv
This project uses **Nix Flakes** (`flake.nix`) and **direnv** (`.envrc`) to declare its development dependencies (including Rust compiler, cargo, clippy, rust-analyzer, openssl, zlib, and GUI-related library dependencies for Iced).

### Execution Rule (Critical)
Because your native agent `bash` or terminal execution tool starts in a raw shell that does not automatically load `direnv`, **you must wrap any compilation, testing, or development command in the Nix environment.**

- **DO NOT RUN:** `cargo check` or `cargo test` directly.
- **DO RUN:** Nest your commands inside `nix develop` or `direnv exec`:
  ```bash
  # Option A (Preferred):
  nix develop -c cargo check
  
  # Option B:
  direnv exec . cargo check
  ```

---

## 3. Project Structure
The Rust GUI application (`TMDb-shelf`) is located in the **root directory**.
- When running compilation or check commands, run them directly from the root of the workspace:
  ```bash
  nix develop -c cargo check
  nix develop -c cargo test
  ```

---

## 4. Permission Model
OpenCode permissions are set to `"allow"` inside `~/.config/opencode/opencode.json` to streamline the agent flow. 
- You do not need to ask for permission before editing files or running wrapped bash commands.
- Proceed aggressively to accomplish tasks, but always ensure your changes are syntactically and logically validated using `nix develop -c cargo check`.
