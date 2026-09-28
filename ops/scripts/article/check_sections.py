#!/usr/bin/env python3
"""記事の節構成が章構成マインドマップと一致するかを検査する。

執筆規約（docs/article/outline.md）は「章内の節は draft.md のマインドマップに
一致させる」としているが、7 Unit を通じて人が目で突合していた。
Kotlin 版 Unit 7 のふりかえり Try 2 に従い、機械に移す。

節名が特定言語のライブラリ名を含む場合は、draft.md の「言語別の読み替え」表を
適用してから比較する。

章末の定型（この章で書いたコード・参照）はマインドマップに無いので除外する。

使い方:
    python ops/scripts/article/check_sections.py [記事のパス...]
"""

from __future__ import annotations

import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
DRAFT = ROOT / "docs/article/draft.md"
ARTICLE_DIR = ROOT / "docs/article/zettai"

# 章末の定型。マインドマップには無い。
TRAILING = {"この章で書いたコード", "参照", "連載を終えて"}

# 記事のディレクトリ名と、draft.md の読み替え表の「対象」列の対応。
TARGET_NAMES = {"kotlin": "Kotlin", "nadesiko": "なでしこ3", "rust": "Rust"}

CHAPTER_LINE = re.compile(r"^\*\* 第([０-９0-9]+)章[　 ]*(.*)$")
SECTION_LINE = re.compile(r"^\*\*\* (.+)$")
HEADING = re.compile(r"^## (.+)$", re.M)
MAPPING_ROW = re.compile(r"^\| 第 (\d+) 章 第 (\d+) 節 \| (.+?) \| (.+?) \| (.+?) \|", re.M)


def normalize(text: str) -> str:
    """日本語と英数字の間の空白や全角・半角の差を吸収する。"""
    return unicodedata.normalize("NFKC", text).replace(" ", "").replace("　", "")


def to_ascii_digits(text: str) -> str:
    return unicodedata.normalize("NFKC", text)


def read_mindmap() -> tuple[dict[int, list[str]], dict[tuple[int, int, str], str]]:
    chapters: dict[int, list[str]] = {}
    current: int | None = None
    body = DRAFT.read_text(encoding="utf-8")

    for line in body.splitlines():
        chapter = CHAPTER_LINE.match(line)
        if chapter:
            current = int(to_ascii_digits(chapter.group(1)))
            chapters[current] = []
            continue
        section = SECTION_LINE.match(line)
        if section and current is not None:
            chapters[current].append(section.group(1).strip())

    # 言語別の読み替え表。3 列目がマインドマップの節名、4 列目が対象、5 列目が読み替え後。
    mapping: dict[tuple[int, int, str], str] = {}
    for m in MAPPING_ROW.finditer(body):
        chapter, index, _original, target, replaced = m.groups()
        mapping[(int(chapter), int(index), target.strip())] = replaced.strip()
    return chapters, mapping


def expected_sections(chapter: int, target: str, chapters, mapping) -> list[str]:
    sections = list(chapters.get(chapter, []))
    for (ch, index, tgt), replaced in mapping.items():
        if ch == chapter and tgt == target and 1 <= index <= len(sections):
            sections[index - 1] = replaced
    return sections


def check(article: Path, chapters, mapping) -> list[str]:
    chapter = int(article.stem.removeprefix("chapter"))
    target = TARGET_NAMES.get(article.parent.name, article.parent.name)
    expected = [normalize(s) for s in expected_sections(chapter, target, chapters, mapping)]
    actual = [
        normalize(h) for h in HEADING.findall(article.read_text(encoding="utf-8"))
        if normalize(h) not in {normalize(t) for t in TRAILING}
    ]

    if expected == actual:
        return []
    return [
        f"{article.parent.name}/{article.name}: 節構成がマインドマップと一致しない\n"
        f"    期待: {expected}\n"
        f"    実際: {actual}"
    ]


def main(argv: list[str]) -> int:
    chapters, mapping = read_mindmap()
    targets = [Path(a) for a in argv[1:]] or sorted(ARTICLE_DIR.glob("*/chapter*.md"))

    violations: list[str] = []
    for article in targets:
        found = check(article, chapters, mapping)
        violations.extend(found)
        print(("NG " if found else "OK ") + str(article.resolve().relative_to(ROOT)))

    for violation in violations:
        print(f"NG {violation}")

    print(f"\n章 {len(targets)} ファイル / 違反 {len(violations)} 件")
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
