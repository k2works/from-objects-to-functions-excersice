{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
  # Flix は nixpkgs の標準配布に確認できていないため、JDK だけを提供し、
  # 公式リリースの flix.jar を初回に取得するラッパー方式にする。
  flixVersion = "0.79.0";
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    jdk21
    curl
  ]);
  JAVA_HOME = "${packages.jdk21}";
  FLIX_VERSION = flixVersion;
  shellHook = ''
    ${baseShell.shellHook}
    export FLIX_JAR="''${FLIX_JAR:-$PWD/apps/flix/zettai/.flix/flix-${flixVersion}.jar}"
    flix() {
      if [ ! -f "$FLIX_JAR" ]; then
        mkdir -p "$(dirname "$FLIX_JAR")"
        curl -sSL -o "$FLIX_JAR" \
          "https://github.com/flix/flix/releases/download/v${flixVersion}/flix.jar"
      fi
      java -jar "$FLIX_JAR" "$@"
    }
    echo "Flix development environment activated"
    echo "  - JDK: $(java -version 2>&1 | head -n 1)"
    echo "  - Flix: v${flixVersion} (flix コマンドが初回に flix.jar を取得する)"
  '';
}
