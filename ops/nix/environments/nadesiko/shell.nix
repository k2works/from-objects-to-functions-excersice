{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    # なでしこ3 の Node 実装（cnako3）は package.json で engines に node >= 22 を要求する。
    nodejs_22
    gnumake
  ]);
  shellHook = ''
    ${baseShell.shellHook}

    # cnako3 は npm パッケージ nadesiko3 に同梱される。バージョンは
    # apps/nadesiko/zettai/package.json の 1 箇所で固定する。
    if [ -d "$(pwd)/apps/nadesiko/zettai" ] && [ ! -d "$(pwd)/apps/nadesiko/zettai/node_modules" ]; then
      echo "Installing nadesiko3 (cnako3) ..."
      (cd "$(pwd)/apps/nadesiko/zettai" && npm install --no-audit --no-fund) \
        || echo "  (nadesiko3 の導入に失敗しました。ネットワークを確認してください)"
    fi

    echo "Nadesiko3 development environment activated"
    echo "  - Node.js: $(node --version)"
    if [ -x "$(pwd)/apps/nadesiko/zettai/node_modules/.bin/cnako3" ]; then
      echo "  - cnako3: $("$(pwd)/apps/nadesiko/zettai/node_modules/.bin/cnako3" --version)"
    fi
    echo "  使い方: cd apps/nadesiko/zettai && make check"
  '';
}
