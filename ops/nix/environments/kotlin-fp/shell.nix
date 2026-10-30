{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    jdk25
  ]);
  JAVA_HOME = "${packages.jdk25}";
  shellHook = ''
    ${baseShell.shellHook}
    echo "Kotlin FP development environment activated"
    echo "  - JDK: $(java -version 2>&1 | head -n 1)"
    echo "  - Gradle Wrapper: apps/kotlin-fp/zettai/gradlew"
  '';
}
