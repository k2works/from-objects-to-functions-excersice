#!/usr/bin/env python3
"""ADR と記事の対応を検査する。

連載が 13 章・ADR 12 件に達し、相互参照を目で追うのが難しくなった。
Unit 6 の学び「懸念を書くだけでは防げない。検査が止める」に従い、
機械で確かめられる部分を検査に移す。

確かめること:

1. 記事が参照する ADR ファイルが存在する
2. ADR が参照する章のファイルが存在する
3. ADR 索引に全 ADR が載っている
4. 索引に載っている ADR のファイルが存在する

論の一貫性（記事の主張と ADR の決定が矛盾しないか）は機械では測れない。
それは人が読む。この検査は「参照が切れていないこと」だけを見る。

使い方:
    python ops/scripts/article/check_adr_references.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ADR_LINK = re.compile(r"\]\((?:[./]*)(?:adr/)?(ADR-\d{3}[\w.-]*\.md)\)")
CHAPTER_LINK = re.compile(r"\]\((?:[./]*)(?:article/zettai/\w+/)?(chapter\d{2}\.md)\)")
INDEX_ROW = re.compile(r"\|\s*\[(ADR-\d{3})\]\(([\w.-]+\.md)\)")


def main() -> int:
    root = Path(__file__).resolve().parents[3]
    adr_dir = root / "docs/adr"
    # 対象言語のディレクトリをすべて見る。章のファイル名は言語をまたいで共通。
    chapters = sorted(root.glob("docs/article/zettai/*/chapter*.md"))

    adr_files = {p.name for p in adr_dir.glob("ADR-*.md")}
    chapter_files = {p.name for p in chapters}
    violations: list[str] = []

    # 1. 記事が参照する ADR が存在するか
    for article in chapters:
        for name in ADR_LINK.findall(article.read_text(encoding="utf-8")):
            if name not in adr_files:
                violations.append(f"{article.parent.name}/{article.name} が参照する {name} が存在しない")

    # 2. ADR が参照する章が存在するか
    for adr in sorted(adr_dir.glob("ADR-*.md")):
        for name in CHAPTER_LINK.findall(adr.read_text(encoding="utf-8")):
            if name not in chapter_files:
                violations.append(f"{adr.name} が参照する {name} が存在しない")

    # 3・4. 索引と実ファイルの対応
    index_rows = dict(INDEX_ROW.findall((adr_dir / "index.md").read_text(encoding="utf-8")))

    for number, filename in index_rows.items():
        if filename not in adr_files:
            violations.append(f"索引の {number} の行が指す {filename} が存在しない")

    for filename in sorted(adr_files):
        number = filename[:7]
        if number not in index_rows:
            violations.append(f"{filename} が索引に載っていない")

    for violation in violations:
        print(f"NG {violation}")

    print(f"\nADR {len(adr_files)} 件 / 章 {len(chapters)} ファイル / 違反 {len(violations)} 件")
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main())
