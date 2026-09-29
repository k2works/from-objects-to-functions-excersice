#!/usr/bin/env python3
"""justfile の check-all と CI の検査が一致しているかを確かめる。

Unit 2 のゲート 1 で「CI は just を通さず cargo を直接呼ぶ」と決めた。
速いが（525 秒 → 17 秒）、**検査の定義が 2 か所に分かれる**。
段を足したときの直し忘れを、人の注意ではなく検査で止める。

**Unit 7 でジョブの setup も見るようにした。** Unit 6 で `cov` を別ジョブへ
移したとき、`cargo-llvm-cov` のインストールを移し忘れて CI が落ちた。
コマンドの一致だけを見ていたので止められなかった。

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
# justfile の変数（`name := "value"`）。**すべて展開して比べる。**
#
# Unit 2 では cov_min だけを決め打ちで展開していた。Unit 3 で cov_skip を
# 足したときに、検査が「ずれている」と言って落ちた。**落ちたのは正しいが、
# 理由が二重管理ではなく検査の手抜きだった。** 変数を一般に読む形に直した。
ASSIGN = re.compile(r'^([a-z_]+) := "([^"]*)"$', re.M)
# 各レシピの cargo コマンド（{{run}} と justfile の変数は展開して比べる）
RECIPE = re.compile(r"^([a-z-]+):\n(?:\s+#.*\n)*\s+\{\{run\}\} (cargo .+)$", re.M)
# workflow の run:（working-directory は defaults で効く）
WORKFLOW_RUN = re.compile(r"^\s+run: (cargo .+)$", re.M)

# ジョブごとの塊（`  <name>:` で始まり、次の同じ深さまで）
JOB = re.compile(r"^  ([a-z0-9-]+):\n(.*?)(?=^  [a-z0-9-]+:\n|\Z)", re.M | re.S)

# cargo のサブコマンドが要る道具。
#
# **Unit 6 でここが抜けて CI が落ちた。** `cargo llvm-cov` を動かすジョブに
# `cargo-llvm-cov` のインストールが無くても、コマンドの一致だけを見る検査は
# 止められなかった。
NEEDS = {
    "cargo llvm-cov": ["taiki-e/install-action@cargo-llvm-cov", "llvm-tools"],
    "cargo fmt": ["rustfmt"],
    "cargo clippy": ["clippy"],
}


def check_job_setup(flow: str) -> list[str]:
    """各ジョブが、自分の走らせる cargo コマンドに要る道具を持っているか。"""
    problems: list[str] = []
    for name, body in JOB.findall(flow):
        for command, tools in NEEDS.items():
            if f"run: {command}" not in body:
                continue
            for tool in tools:
                if tool not in body:
                    problems.append(
                        f"ジョブ `{name}` が `{command}` を走らせるのに `{tool}` が無い"
                    )
    return problems


def main() -> int:
    just = JUSTFILE.read_text(encoding="utf-8")
    flow = WORKFLOW.read_text(encoding="utf-8")

    variables = dict(ASSIGN.findall(just))

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
        cmd = recipes[name]
        for key, value in variables.items():
            cmd = cmd.replace("{{" + key + "}}", value)
        if "{{" in cmd:
            print(f"NG {name} に展開できない変数が残っている: {cmd}")
            return 1
        expected.append(cmd.strip())

    actual = [c.strip() for c in WORKFLOW_RUN.findall(flow)]

    setup_problems = check_job_setup(flow)
    if setup_problems:
        print("NG ジョブに要る道具が足りない")
        for problem in setup_problems:
            print(f"   {problem}")
        print()
        print("コマンドをジョブ間で動かしたら、道具も動かす（Unit 6 で踏んだ）。")
        return 1

    if expected == actual:
        print(f"OK justfile の check-all と CI が一致（{len(expected)} 段）")
        print("   ジョブの setup も一致")
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
