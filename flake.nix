{
  description = "A State-of-the-Art Rust development environment and package for TMDb-shelf (Iced GUI)";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    utils.url = "github:numtide/flake-utils";
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, utils, rust-overlay, ... }:
    utils.lib.eachDefaultSystem (system:
      let
        overlays = [ (import rust-overlay) ];
        pkgs = import nixpkgs { inherit system overlays; };
        
        # Parse Cargo.toml dynamically
        cargoToml = builtins.fromTOML (builtins.readFile ./Cargo.toml);
        rustVersion = cargoToml.package.rust-version or "latest";

        baseRustToolchain = (
          if rustVersion == "latest" then
            pkgs.rust-bin.stable.latest.default
          else
            pkgs.rust-bin.stable.${rustVersion}.default
        );

        devRustToolchain = baseRustToolchain.override {
          extensions = [ "rust-src" "rust-analyzer" ];
        };

        rustPlatform = pkgs.makeRustPlatform {
          cargo = baseRustToolchain;
          rustc = baseRustToolchain;
        };

        runtimeLibs = with pkgs; [
          libGL
          libxkbcommon
          wayland
          libx11
          libxcursor
          libxrandr
          libxi
          fontconfig
          vulkan-loader
        ];

        runtimeTools = with pkgs; [
          mediainfo
          ffmpeg
          xdg-utils
        ];

        tmdb-shelf = rustPlatform.buildRustPackage {
          pname = cargoToml.package.name;
          version = cargoToml.package.version;

          src = pkgs.lib.cleanSource ./.;

          cargoLock = {
            lockFile = ./Cargo.lock;
          };

          nativeBuildInputs = with pkgs; [
            pkg-config
            makeWrapper
            copyDesktopItems
          ];

          buildInputs = with pkgs; [
            openssl
            zlib
          ] ++ runtimeLibs;

          # Tests require internet access (IMDb GraphQL API)
          doCheck = false;

          desktopItems = [
            (pkgs.makeDesktopItem {
              name = "tmdb-shelf";
              desktopName = "TMDb-shelf";
              comment = cargoToml.package.description;
              exec = "tmdb-shelf";
              icon = "tmdb-shelf";
              categories = [ "AudioVideo" "Video" ];
            })
          ];

          postInstall = ''
            install -Dm644 assets/Shelf.png $out/share/pixmaps/tmdb-shelf.png

            wrapProgram $out/bin/tmdb-shelf \
              --prefix PATH : ${pkgs.lib.makeBinPath runtimeTools} \
              --prefix LD_LIBRARY_PATH : ${pkgs.lib.makeLibraryPath runtimeLibs}
          '';

          meta = with pkgs.lib; {
            description = cargoToml.package.description;
            homepage = cargoToml.package.homepage;
            license = licenses.gpl3Plus;
            mainProgram = "tmdb-shelf";
            platforms = platforms.linux;
          };
        };
      in
      {
        packages.default = tmdb-shelf;
        packages.tmdb-shelf = tmdb-shelf;

        apps.default = {
          type = "app";
          program = "${tmdb-shelf}/bin/tmdb-shelf";
          meta = tmdb-shelf.meta;
        };
        apps.tmdb-shelf = {
          type = "app";
          program = "${tmdb-shelf}/bin/tmdb-shelf";
          meta = tmdb-shelf.meta;
        };

        devShells.default = pkgs.mkShell {
          nativeBuildInputs = with pkgs; [
            devRustToolchain
            pkg-config
          ] ++ runtimeTools;

          buildInputs = with pkgs; [
            openssl
            zlib
          ] ++ runtimeLibs;

          shellHook = ''
            export PKG_CONFIG_PATH="${pkgs.openssl.dev}/lib/pkgconfig:${pkgs.zlib.dev}/lib/pkgconfig"
            export LD_LIBRARY_PATH="${pkgs.lib.makeLibraryPath runtimeLibs}:$LD_LIBRARY_PATH"
            export RUST_BACKTRACE=1
          '';
        };
      });
}
