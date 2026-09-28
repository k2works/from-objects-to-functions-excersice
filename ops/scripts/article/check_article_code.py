#!/usr/bin/env python3
"""記事のコードブロックが、サンプル実装に存在するかを検査する。

記事のコード例は動作確認済みの実装から転記する、という執筆規約
（docs/article/outline.md）を機械的に担保するための検査。

逐語の転記ではないコードブロックは、直前に除外マーカーを置いて明示する。

    <!-- code-check: ignore 理由 -->

使い方:
    python ops/scripts/article/check_article_code.py [記事のパス...]

引数を省略すると docs/article/zettai/ 配下の chapter*.md をすべて検査する。
"""

from __future__ import annotations

import glob
import re
import sys
from pathlib import Path

# 実装の探索先。ここに無いコードは記事に載せられない。
SOURCE_GLOBS = [
    "apps/**/*.kt",
    "apps/**/*.kts",
    "apps/**/*.toml",
    "apps/**/*.nako3",
    "apps/**/*.rs",
    "apps/**/justfile",
    "apps/**/Makefile",
    "apps/**/package.json",
    "ops/nix/environments/**/*.nix",
    ".github/workflows/*.yml",
    "docker-compose.yml",
    "ops/scripts/**/*.py",
]

# 検査対象の言語。出力例やシェルの実行例は対象外。
CHECKED_LANGUAGES = {"kotlin", "nix", "toml", "yaml", "python", "nako3", "makefile", "json", "rust", "just"}

IGNORE_MARKER = re.compile(r"<!--\s*code-check:\s*ignore(.*?)-->", re.I)
BLOCK = re.compile(r"^```(\w+)\n(.*?)^```", re.M | re.S)


def source_lines(root: Path) -> set[str]:
    lines: set[str] = set()
    for pattern in SOURCE_GLOBS:
        for path in glob.glob(str(root / pattern), recursive=True):
            for line in Path(path).read_text(encoding="utf-8").splitlines():
                lines.add(line.strip())
    return lines


def is_skippable(line: str) -> bool:
    """コメント・区切り・省略記号は照合しない。"""
    return (
        not line
        or line.startswith(("//", "/*", "*", "#", "<!--"))
        or line in {"{", "}", "(", ")", "[", "]"}
        or "..." in line
    )


def check(article: Path, known: set[str]) -> list[tuple[int, str]]:
    text = article.read_text(encoding="utf-8")
    violations: list[tuple[int, str]] = []

    for match in BLOCK.finditer(text):
        language, code = match.group(1), match.group(2)
        if language not in CHECKED_LANGUAGES:
            continue

        # ブロック直前の 3 行以内に除外マーカーがあれば飛ばす
        preceding = text[: match.start()].splitlines()[-3:]
        if any(IGNORE_MARKER.search(line) for line in preceding):
            continue

        start_line = text[: match.start()].count("\n") + 1
        for offset, line in enumerate(code.splitlines()):
            stripped = line.strip()
            if is_skippable(stripped):
                continue
            if stripped not in known:
                violations.append((start_line + offset + 1, stripped))

    return violations


def main(argv: list[str]) -> int:
    root = Path(__file__).resolve().parents[3]
    articles = [Path(a) for a in argv[1:]] or [
        Path(p) for p in sorted(glob.glob(str(root / "docs/article/zettai/*/chapter*.md")))
    ]

    if not articles:
        print("検査対象の記事がありません")
        return 0

    known = source_lines(root)
    total = 0

    for article in articles:
        violations = check(article, known)
        total += len(violations)
        rel = article.relative_to(root) if article.is_absolute() else article
        if violations:
            print(f"NG {rel}: {len(violations)} 件")
            for line_no, line in violations:
                print(f"   {rel}:{line_no}: 実装に存在しない: {line}")
        else:
            print(f"OK {rel}")

    if total:
        print(f"\n違反 {total} 件。記事のコード例は実装から転記してください。")
        print("逐語の転記でない場合は、コードブロックの直前に次を置いてください。")
        print("    <!-- code-check: ignore 理由 -->")
        return 1

    print("\n違反 0 件")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
