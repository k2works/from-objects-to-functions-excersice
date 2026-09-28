#!/usr/bin/env python3
"""記事に書いた件数の主張が、実物と合っているかを検査する。

Kotlin 版 Unit 7 で、第 13 章が「ADR には全件、再検討の条件を書いた」と
書いたが実際は一部だった。書いた時点で数えていなかった。
ふりかえり Try 3 に従い、数えられる主張だけを機械で止める。

対象にするのは、実物を数えられる 2 種類だけ。

1. 「ADR N 件」
2. 「全 N 章」
3. 「N 回とも成り立つ」「試行回数は N 回」— 性質テストの試行回数。実装の宣言と突き合わせる

それ以外の数値（テスト数など）は対象にしない。数え方が一意に決まらず、
誤検知で運用できなくなるため。

使い方:
    python ops/scripts/article/check_numbers.py [記事のパス...]
"""

from __future__ import annotations

import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ARTICLE_DIR = ROOT / "docs/article/zettai"
CHAPTER_TOTAL = 13

ADR_COUNT = re.compile(r"ADR\s*([0-9０-９]+)\s*件")
CHAPTER_COUNT = re.compile(r"全\s*([0-9０-９]+)\s*章")
# 「2 回とも起きました」のような回数一般ではなく、性質テストの言い回しだけを対象にする。
TRIAL_COUNT = re.compile(r"([0-9０-９]+)\s*回とも成り立つ|試行回数は\s*([0-9０-９]+)\s*回")
# 実装が宣言する試行回数。言語ごとに書き方が違うので 2 通り見る。
# なでしこ3: 試行回数=200 / Rust: const TRIALS: usize = 200;
TRIAL_DECL = re.compile(r"試行回数\s*=\s*([0-9]+)|TRIALS:\s*usize\s*=\s*([0-9_]+)")


def declared_trials(root: Path, target: str) -> set[int]:
    """その対象の実装が宣言している試行回数を集める。"""
    found: set[int] = set()
    for path in (root / "apps" / target).rglob("*"):
        if path.is_file() and path.suffix in {".nako3", ".kt", ".kts", ".rs"}:
            for a, b in TRIAL_DECL.findall(path.read_text(encoding="utf-8")):
                found.add(int((a or b).replace("_", "")))
    return found


def to_int(text: str) -> int:
    return int(unicodedata.normalize("NFKC", text))


def check(article: Path, adr_total: int, trials: set[int]) -> list[str]:
    violations: list[str] = []
    body = article.read_text(encoding="utf-8")

    for match in ADR_COUNT.finditer(body):
        claimed = to_int(match.group(1))
        if claimed != adr_total:
            violations.append(
                f"{article.parent.name}/{article.name}: 「ADR {claimed} 件」と書いてあるが実物は {adr_total} 件"
            )

    for match in CHAPTER_COUNT.finditer(body):
        claimed = to_int(match.group(1))
        if claimed != CHAPTER_TOTAL:
            violations.append(
                f"{article.parent.name}/{article.name}: 「全 {claimed} 章」と書いてあるが構成は {CHAPTER_TOTAL} 章"
            )
    for match in TRIAL_COUNT.finditer(body):
        claimed = to_int(match.group(1) or match.group(2))
        if trials and claimed not in trials:
            violations.append(
                f"{article.parent.name}/{article.name}: 「{claimed} 回」と書いてあるが"
                f"実装が宣言する試行回数は {sorted(trials)}"
            )

    return violations


def main(argv: list[str]) -> int:
    adr_total = len(list((ROOT / "docs/adr").glob("ADR-*.md")))
    targets = [Path(a) for a in argv[1:]] or sorted(ARTICLE_DIR.glob("*/chapter*.md"))
    violations: list[str] = []
    trials_cache: dict[str, set[int]] = {}

    for article in targets:
        target = article.parent.name
        if target not in trials_cache:
            trials_cache[target] = declared_trials(ROOT, target)
        violations.extend(check(article, adr_total, trials_cache[target]))

    for violation in violations:
        print(f"NG {violation}")

    print(f"\nADR {adr_total} 件 / 章 {len(targets)} ファイル / 違反 {len(violations)} 件")
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
