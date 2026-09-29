#!/usr/bin/env python3
"""justfile の check-all と CI の検査が一致しているかを確かめる。

Unit 2 のゲート 1 で「CI は just を通さず cargo を直接呼ぶ」と決めた。
速いが（525 秒 → 17 秒）、**検査の定義が 2 か所に分かれる**。
段を足したときの直し忘れを、人の注意ではなく検査で止める。

使い方:
    python ops/scripts/check_rust_ci_parity.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JUSTFILE = ROOT / "apps/rust/zettai/justfile"
WORKFLOW = ROOT / ".github/workflows/rust-zettai.yml"

# justfile の check-all が呼ぶ子レシピ
CHECK_ALL = re.compile(r"^check-all:\n\s+\{\{run\}\} just (.+)$", re.M)
# 各レシピの cargo コマンド（{{run}} と {{cov_min}} は展開して比べる）
RECIPE = re.compile(r"^([a-z-]+):\n(?:\s+#.*\n)*\s+\{\{run\}\} (cargo .+)$", re.M)
# workflow の run:（working-directory は defaults で効く）
WORKFLOW_RUN = re.compile(r"^\s+run: (cargo .+)$", re.M)


def main() -> int:
    just = JUSTFILE.read_text(encoding="utf-8")
    flow = WORKFLOW.read_text(encoding="utf-8")

    cov_min = re.search(r'cov_min := "(\d+)"', just).group(1)

    m = CHECK_ALL.search(just)
    if not m:
        print("NG justfile に check-all が見つからない")
        return 1
    wanted = m.group(1).split()

    recipes = {name: cmd for name, cmd in RECIPE.findall(just)}
    expected = []
    for name in wanted:
        if name not in recipes:
            print(f"NG justfile の check-all が呼ぶ {name} が見つからない")
            return 1
        expected.append(recipes[name].replace("{{cov_min}}", cov_min).strip())

    actual = [c.strip() for c in WORKFLOW_RUN.findall(flow)]

    if expected == actual:
        print(f"OK justfile の check-all と CI が一致（{len(expected)} 段）")
        for cmd in expected:
            print(f"   {cmd}")
        return 0

    print("NG justfile の check-all と CI がずれている")
    print(f"   justfile: {expected}")
    print(f"   workflow: {actual}")
    print("\n段を足したときは両方を直す（Unit 2 のゲート 1 の代償）。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
