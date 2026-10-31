---
type: Release Report
title: "リリース完了報告書 v1.0.0 - Zettai 連載（Kotlin FP 版）"
description: "Zettai 連載 Kotlin FP 版 v1.0.0 の完了報告。全 13 章の公開、7 Unit の完了、8 件の ADR、ライブラリへ委ねる設計の観察と、確かめていないことを記録する。"
tags: [development, report, release, zettai, kotlin, arrow]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T13:24:35Z }
---

# リリース完了報告書 v1.0.0 - Zettai 連載（Kotlin FP 版）

**報告日**: 2026-10-31

## 概要

Zettai 連載の 4 対象目、Kotlin FP 版が、全 13 章を書き終えました。既存の Kotlin 版と同じ題材・同じ 13 章を、Kotlin の標準機能、Arrow、kotlinx.coroutines などで実装し、自前の抽象のかわりにライブラリへ委ねる設計を、章ごとに示しました。

| 項目 | 内容 |
| :--- | :--- |
| 対象 | `apps/kotlin-fp/zettai/`（7 module）、`docs/article/zettai/kotlin-fp/`（索引と 13 章） |
| 期間 | 2026-10-30 〜 2026-10-31（git の履歴から） |
| Unit | 7 / 7 完了 |
| 章 | 13 / 13 |
| ADR | 8 件（ADR-038 〜 ADR-045） |
| テスト（最終 module） | 220 件 |
| Bolt 終了報告 / 開発成果物レビュー | 7 件 / 7 件 |

この報告書は、リリース（GitHub Release、バージョンタグ）そのものを作る作業ではありません。タグの作成は外向きで戻しにくい操作なので、人が判断します。

## Intent への答え

> 既存 Kotlin 版と同じ Zettai・同じ 13 章を、ライブラリへ委ねて書いたとき、設計の違いを、読者が手元で再現できるか。

手元で再現できる形にできました。`apps/kotlin-fp/zettai/` で `gradlew check` を実行すると、単体、性質、Cucumber の 3 経路、Testcontainers の結合テストが通ります。記事のコード例は、検査スクリプトで実装との一致を確かめています。

4 本目の問い、「ライブラリへ委ねるほど、設計の意図は明確になるのか」には、次のように答えました（[第 13 章](../article/zettai/kotlin-fp/chapter13.md)）。

- 名前が概念と一致していて、読む人がその名前を知っているもの（`Either`、`zipOrAccumulate`）は、委ねて明確になった。
- transaction をどこで切るか、ログに何を載せないかのような境界は、委ねる先が無く、型とテストで自分で表した。
- これは 1 つの題材、1 つの実装での観察で、一般論の根拠はない。

## 達成した条件

| 成功条件（リリース計画） | 結果 |
| :--- | :--- |
| `gradlew check` で単体・性質・Cucumber・Testcontainers のテストが通る | 達成。全 module の `clean check` が成功（1 分 49 秒） |
| 索引と全 13 章があり、コード例が実装と一致する | 達成。記事コード・節構成・数値・引用・ADR 参照の検査が、全 13 章で違反 0 |
| JDK 25、Gradle Wrapper、依存バージョンが固定される | 達成。`gradle/libs.versions.toml` に集約 |
| 既存 Kotlin 版との選択差を各章で説明する | 達成。第 13 章と横断比較に整理 |

## 数字

| module | 章 | テスト件数 | 本番コードの行数 | `.kt` ファイル数 |
| :--- | :--- | :--- | :--- | :--- |
| `zettai-step1-foundation` | 1 | 7 | 30 | 1 |
| `zettai-step2-http-domain` | 2〜3 | 9 | 84 | 4 |
| `zettai-step3-domain-events` | 4〜5 | 17 | 218 | 8 |
| `zettai-step4-command-errors` | 6〜7 | 30 | 472 | 12 |
| `zettai-step5-cqrs-persistence` | 8〜9 | 89 | 776 | 15 |
| `zettai-step6-transaction-validation` | 10〜11 | 165 | 1253 | 19 |
| `zettai-step7-observability` | 12〜13 | 220 | 1599 | 24 |

数え方は、[第 13 章](../article/zettai/kotlin-fp/chapter13.md)と同じです（2026-10-31）。

## 承認ゲートについて

人の判断を求めた記録は、Unit ごとの計画と ADR にあります。Unit 5 以降の記録を挙げます。Unit 1〜4 の記録は、この報告書では集計していません。

| Unit | 人が承認したもの |
| :--- | :--- |
| 5 | 計画、ADR-038〜041 |
| 6 | 計画、スキーマ変更、ADR-042、ADR-043、データモデルの Kotlin FP 版の差分 |
| 7 | 計画、JSON の依存と ADR-044、ログの内容、第 12 章の全文、ADR-045 |

Unit 7 は、計画したゲートが、停止 3 箇所と条件付き 1 箇所でした。停止 1〜3 は実際に止まり、承認を得ました。条件付きの停止 4（第 13 章の主張が実装で裏付けられない場合）は、裏付けのない主張を「無い」「確かめていない」と書いたため、発動しませんでした。

## 既製品の判断

この版が入れたもの、入れなかったもの、比べていないものです。

| 区分 | 道具 |
| :--- | :--- |
| 入れた | Arrow（`Either`、`Raise`、`zipOrAccumulate`）、kotlinx.coroutines、kotlinx-serialization-json（`JsonElement` の木だけ）、Kotest、http4k、Cucumber、Testcontainers、PostgreSQL の JDBC ドライバ |
| 入れなかった | Arrow の `Resource`、悲観ロック、DB の payload のための JSON ライブラリ、`ContextReader` |
| 比べていない | Kondor、ログの枠組み（SLF4J など）、既製の template エンジン、Arrow の optics |

Rust 版のように、入れた場合と入れない場合の依存の数やビルド時間は、測っていません。

## 正直に記録した問題

| 問題 | 内容 |
| :--- | :--- |
| 検出率を数えていない | 他の 3 版は、法則を壊して検出率を数えている。この版は、法則が通ることだけを示した |
| 設計を 2 回間違えた（第 10 章） | 待ち合わせの位置が、変えた口に追いついていなかったこと。stream ごとにまとめて挿入して順序を崩したこと |
| 計画の誤り | 実在しない関数名（`Request.listKey()`）を持ち越した。承認済みの ADR-040 との衝突を、計画の独立した検証で見つけた |
| 置き場所の誤り | request の filter を adapter に置き、既存の境界テストに拒まれた |
| 外から見える設計 | 初版の「例外は記録しない」は、運用では使えなかった。5 観点のうち 3 つが指摘した |
| 数値の再現性 | 第 13 章の行数が、末尾の空要素を含む数え方だった。レビューで直した |
| 環境の問題 | コマンド中のバックスラッシュが畳まれ、スクリプトの内容が壊れた。Gradle を誤ったディレクトリで実行して出力が空になった |

## 扱っていないこと

認証・認可、CSRF 対策、読み取りモデルの永続化、競合の自動の再試行、ログの保管とアクセスの管理、相関 ID、分散トレース、メトリクスの収集基盤、HTTP と PostgreSQL の組み合わせの受け入れ確認、失敗応答の HTML 化は、扱っていません（[第 13 章](../article/zettai/kotlin-fp/chapter13.md)）。1 番目の認証が、いちばん重大です。このまま実務に出すことはできません。

## 次の連載への持ち込み

- 外に見える設計（ログ、エラー応答）は、書いた直後に運用者の目で読み返す。
- 新しい decorator や filter を足したら、本番の組み立て箇所と、それを通す結合テストを同時に作る。
- 数値を記事に書くときは、数え方（コマンド）を残す。
- 比べる軸（検出率など）は、章の計画の時点で、やる・やらないを決める。
- 承認済みの ADR と矛盾する新しい決定は、見直す範囲を比較表にして停止点に出す。

## 関連ドキュメント

- [リリース計画](release_plan-kotlin-fp.md)
- [Unit 7 の Bolt 終了報告](bolt_report-kotlin-fp-7.md)
- [Unit 7 のふりかえり](retrospective-kotlin-fp-7.md)
- [横断比較](../article/zettai/comparison/index.md)
- [Kotlin FP 版の索引](../article/zettai/kotlin-fp/index.md)
