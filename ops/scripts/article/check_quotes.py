#!/usr/bin/env python3
"""章をまたぐ引用が、引用元に実在するかを検査する。

Kotlin 版 Unit 7 で、第 9 章と第 13 章が別の章に無い文を引用の形で書いて
いるのが 3 件見つかった。要約や別文書の文を、引用として書いてしまう誤り。
ふりかえり Try 1 に従い、機械で止める。

検出するのは「第 N 章で『…』と書きました」の形だけ。地の文の鉤括弧は
対象にしない。引用の形で書いた以上、その文字列が引用元に実在することを
求める。

使い方:
    python ops/scripts/article/check_quotes.py [記事のパス...]
"""

from __future__ import annotations

import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ARTICLE_DIR = ROOT / "docs/article/zettai"

# 「第 N 章で…『引用』と書きました／と述べました／とあります」
QUOTE = re.compile(
    r"第\s*([0-9０-９]+)\s*章[^。「』]{0,30}?[「『]([^」』]{10,})[」』]\s*と(?:書き|述べ|示し|あり)"
)


def normalize(text: str) -> str:
    return unicodedata.normalize("NFKC", text).replace(" ", "").replace("　", "")


def check(article: Path) -> list[str]:
    violations: list[str] = []
    body = article.read_text(encoding="utf-8")

    for match in QUOTE.finditer(body):
        chapter = int(unicodedata.normalize("NFKC", match.group(1)))
        quoted = match.group(2)
        source = article.parent / f"chapter{chapter:02d}.md"

        if source == article:
            continue
        if not source.exists():
            violations.append(f"{article.name}: 第 {chapter} 章を引用しているが {source.name} が無い")
            continue
        if normalize(quoted) not in normalize(source.read_text(encoding="utf-8")):
            violations.append(
                f"{article.name}: 第 {chapter} 章に無い文を引用している\n    「{quoted}」"
            )
    return violations


def main(argv: list[str]) -> int:
    targets = [Path(a) for a in argv[1:]] or sorted(ARTICLE_DIR.glob("*/chapter*.md"))
    violations: list[str] = []

    for article in targets:
        found = check(article)
        violations.extend(found)

    for violation in violations:
        print(f"NG {violation}")

    print(f"\n章 {len(targets)} ファイル / 違反 {len(violations)} 件")
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
