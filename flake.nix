{
  description = "A State-of-the-Art Rust development environment for TMDb-shelf (Iced GUI)";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    utils.url = "github:numtide/flake-utils";
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { nixpkgs, utils, rust-overlay, ... }:
    utils.lib.eachDefaultSystem (system:
      let
        overlays = [ (import rust-overlay) ];
        pkgs = import nixpkgs { inherit system overlays; };
        
        # Parse Cargo.toml dynamically
        cargoToml = builtins.fromTOML (builtins.readFile ./Cargo.toml);
        rustVersion = cargoToml.package.rust-version or "latest";

        rustToolchain = (
          if rustVersion == "latest" then
            pkgs.rust-bin.stable.latest.default
          else
            pkgs.rust-bin.stable.${rustVersion}.default
        ).override {
          extensions = [ "rust-src" "rust-analyzer" ];
        };
      in
      {
        devShells.default = pkgs.mkShell {
          nativeBuildInputs = with pkgs; [
            rustToolchain
            pkg-config
          ];

          buildInputs = with pkgs; [
            openssl
            zlib
            fontconfig
            libxkbcommon
            libGL
            wayland
            xorg.libX11
            xorg.libXcursor
            xorg.libXrandr
            xorg.libXi
          ];

          shellHook = ''
            export PKG_CONFIG_PATH="${pkgs.openssl.dev}/lib/pkgconfig:${pkgs.zlib.dev}/lib/pkgconfig"
            export LD_LIBRARY_PATH="${pkgs.libGL}/lib:${pkgs.libxkbcommon}/lib:${pkgs.wayland}/lib:${pkgs.xorg.libX11}/lib:${pkgs.xorg.libXcursor}/lib:${pkgs.xorg.libXrandr}/lib:${pkgs.xorg.libXi}/lib:$LD_LIBRARY_PATH"
            export RUST_BACKTRACE=1
          '';
        };
      });
}
