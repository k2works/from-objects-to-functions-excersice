{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
  # Flix は nixpkgs の標準配布に確認できていないため、JDK だけを提供し、
  # 公式リリースの flix.jar を初回に取得するラッパー方式にする。
  flixVersion = "0.79.0";
  # 保存先は、カレントディレクトリに依存しないキャッシュ置き場（FLIX_JAR で上書きできる）。
  # シェル関数ではなく実行可能なスクリプトにする。関数は対話シェルの中でしか使えず、
  # `nix develop --command flix test` や CI のような非対話の使い方で見つからないため。
  flix = packages.writeShellScriptBin "flix" ''
    jar="''${FLIX_JAR:-''${XDG_CACHE_HOME:-$HOME/.cache}/flix/flix-${flixVersion}.jar}"
    if [ ! -f "$jar" ]; then
      mkdir -p "$(dirname "$jar")"
      ${packages.curl}/bin/curl -sSL -o "$jar" \
        "https://github.com/flix/flix/releases/download/v${flixVersion}/flix.jar"
    fi
    exec ${packages.jdk21}/bin/java -jar "$jar" "$@"
  '';
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ [
    packages.jdk21
    packages.curl
    flix
  ];
  JAVA_HOME = "${packages.jdk21}";
  FLIX_VERSION = flixVersion;
  shellHook = ''
    ${baseShell.shellHook}
    echo "Flix development environment activated"
    echo "  - JDK: $(java -version 2>&1 | head -n 1)"
    echo "  - Flix: v${flixVersion} (flix コマンドが初回に flix.jar を取得する)"
  '';
}
