#!/usr/bin/env python3
"""記事に載せるコードの読みやすさを検査する。

ktlint / detekt のような汎用の静的解析は入れない（ADR-004）。代わりに、
本プロジェクトで実際に問題になった 2 点だけを検査する。

1. 行が長すぎる — 記事のコードブロックが折り返して読みにくくなる
2. import の並びが崩れている — 章をまたいで差分を見せるときにノイズになる

汎用ツールを入れると、引数を強制的に改行するなどの整形が入り、
教材コードが縦長になって記事の読みやすさが下がる。目的に対して副作用が大きい。

使い方:
    python ops/scripts/article/check_code_style.py [対象ディレクトリ]
"""

from __future__ import annotations

import sys
from pathlib import Path

MAX_LINE_LENGTH = 120


def check(path: Path) -> list[str]:
    violations: list[str] = []
    lines = path.read_text(encoding="utf-8").splitlines()

    for number, line in enumerate(lines, start=1):
        if len(line) > MAX_LINE_LENGTH:
            violations.append(f"{path}:{number}: 行が長い（{len(line)} 文字 > {MAX_LINE_LENGTH}）")

    imports = [line.rstrip() for line in lines if line.startswith("import ")]
    if imports != sorted(imports):
        violations.append(f"{path}: import の並びがアルファベット順でない")

    return violations


def main(argv: list[str]) -> int:
    root = Path(argv[1]) if len(argv) > 1 else Path(__file__).resolve().parents[3] / "apps"
    sources = sorted(p for p in root.rglob("*.kt") if "/build/" not in str(p))

    if not sources:
        print(f"検査対象がありません: {root}")
        return 0

    violations = [v for path in sources for v in check(path)]

    for violation in violations:
        print(f"NG {violation}")

    print(f"\n検査 {len(sources)} ファイル / 違反 {len(violations)} 件")
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
