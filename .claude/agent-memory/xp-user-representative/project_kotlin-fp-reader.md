---
name: project-kotlin-fp-reader
description: Zettai Kotlin FP 連載の「利用者」= 手元で動かす読者。レビューで繰り返し見るべき再現性・導線の論点
metadata:
  type: project
---

Zettai Kotlin FP 版連載（docs/article/zettai/kotlin-fp/）の利用者は、Kotlin で関数型設計を学び手元でコードを動かす読者。

**Why:** 読者ストーリー（US-00x）は「読者として〜を理解できる」形で書かれ、価値は「記事を読んで手元で再現できるか」で決まる。

**How to apply:**
- 章末の実行コマンドが、その章の時点で必要な前提（Docker など）を満たすか確認する。step5 module は第 8・9 章で共有され、module 全体の test は Testcontainers（Docker）を要する。第 5・7 章は `--tests` で絞る実行例を出していた（Docker 不要の経路案内の先例）。
- 受け入れテスト（Cucumber）は第 3 章（step2）由来。step3/step4 module には無い（第 7 章に明記）。
- 「確かめました」と書く手作業の検証（実装を壊す等）は読者が再現できるか見る。
- 用語の先出し（例: 集約）と、記事内コード断片の未説明の名前（生成器、typealias）を確認する。
- 2026-10-31 時点で step5 に main（実アプリ起動）は無く、「再起動しても残る」は受け入れテストの Hub 組み直しで示している。

関連: [[feedback-review-readonly]]
