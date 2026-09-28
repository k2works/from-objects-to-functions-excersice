{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    rustc
    cargo
    rustfmt
    clippy
    rust-analyzer
    # カバレッジ計測（Zettai 連載 Rust 版 Unit 1 で採用。ADR-026）。
    # llvm-tools は cargo-llvm-cov が内部で使う。
    cargo-llvm-cov
    llvmPackages.bintools
    # タスクランナー（Zettai 連載 Rust 版 Unit 1 で採用。ADR-026）。
    # ホストにも入っていることがあるが、CI では devShell からしか来ない。
    just
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    echo "Rust development environment activated"
    echo "  - rustc: $(rustc --version)"
    echo "  - cargo: $(cargo --version)"
    echo "  - cargo-llvm-cov: $(cargo llvm-cov --version 2>/dev/null || echo 'n/a')"
    echo "  - just: $(just --version 2>/dev/null || echo 'n/a')"
    # cargo-llvm-cov は rustup の llvm-tools-preview を探しに行くが、
    # Nix には無い。LLVM の版が rustc と一致していれば bintools のもので足りる。
    export LLVM_COV="$(command -v llvm-cov)"
    export LLVM_PROFDATA="$(command -v llvm-profdata)"
  '';
}
