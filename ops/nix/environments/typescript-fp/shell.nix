{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
  # 共通 shell の Node を外し、Node 24 だけにする。
  # buildInputs の要素は dev 出力になり、packages.nodejs_22 との比較は一致しないため、名前で外す。
  isNode = p: packages.lib.getName p == "nodejs";
  baseInputs = builtins.filter (p: !(isNode p)) baseShell.buildInputs;
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseInputs ++ [ packages.nodejs_24 ];
  shellHook = ''
    ${baseShell.shellHook}
    echo "TypeScript FP development environment activated"
    echo "  - Node.js: $(node --version)"
    echo "  - npm: $(npm --version)"
  '';
}
