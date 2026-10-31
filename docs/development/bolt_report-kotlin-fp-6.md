---
type: Bolt Report
title: "Unit 6 の Bolt 終了報告 - Zettai 連載（Kotlin FP 版）"
description: "Kotlin FP 版 Unit 6 の transaction と楽観ロック、複数 stream の原子的な保存、名前変更、蓄積する検証、テンプレート描画、第 10〜11 章の完了結果を記録する。"
tags: [development, report, zettai, kotlin, arrow, postgresql, applicative, unit-6]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T11:54:52Z }
---

# Unit 6 の Bolt 終了報告 - Zettai 連載（Kotlin FP 版）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Unit | 6：transaction と検証 |
| Bolt | 6-1 第 10 章 / 6-2 第 11 章 |
| 実施日 | 2026-10-31 |
| 状態 | **実装・執筆・レビュー対応は完了。ADR とデータモデルは承認済み。push 済みで CI も成功** |

## 成果

- command 1 つの処理（読み出し、判断、保存）を 1 つの transaction に入れ、環境を型引数 `Tx` と関数の引数で通しました（ADR-042）。
- stream ごとの版と `UNIQUE (entity_id, version)` で並行競合を検出し、`ConcurrentModification` にしました。HTTP は 409 です（ADR-043）。
- 並行する `CreateToDoList` が event を重複させる問題を、待ち合わせで再現してから直しました。
- 複数の stream への保存を 1 つの transaction で確定させ、in-memory と PostgreSQL が同じ契約テストを満たします。
- 名前変更を、旧い stream の `ListRenamed` と、新しい stream の `ListCreated`・`ItemAdded` の組で表しました。
- 入力の誤りを `Either.zipOrAccumulate` で止めずにすべて集める検証を作り、蓄積する合成に求める性質を property test で確かめました。
- 自前のテンプレート関数（値の無いタグは描画の失敗）で、名前を変える画面と、誤りの一覧を作りました。
- `CatchAll` の応答が内部情報を本文に出す漏れを見つけ、固定の本文にしました。
- 第 10〜11 章、ADR-042〜043、設計文書、索引と nav を実装と同期しました。

## 品質結果

| ゲート | 結果 |
| :--- | :--- |
| `gradlew.bat clean check --no-daemon`（全 module） | 成功（1 分 29 秒。その後、テストの行の折り返しだけを変更し、step6 の `check` を再実行して成功） |
| Unit 6 のテスト | 162 件成功（Kotest、Cucumber 6 scenario × 3 経路、Testcontainers 結合テストを含む） |
| step6 の `check` の 3 回連続 | 成功（31〜45 秒。最後のコード変更の後に再実行） |
| 記事検査（コード・コード形式・節構成・数値・引用・ADR 参照） | 違反 0 |
| OKF | ERROR 0、既存 WARN 10 |
| `mkdocs build --strict` | 成功（最後のドキュメント変更の後に実行） |
| 5 観点レビュー | 高 8 / 中 約 18 / 低 約 18。高の残件 0。中の持ち越し 5 件（[レビュー](../review/kotlin-fp-unit6_review_20261031.md)） |
| 変異による確認 | 版をでたらめにすると PostgreSQL の並行テストが失敗することを確認。名前変更の並行テスト 2 件は変異を行っていない |
| CI（push 後の GitHub Actions） | 成功。`1388f24` に対し Kotlin FP Zettai、Kotlin Zettai、Nadesiko3 Zettai、Deploy MkDocs の 4 本（run 41198684123 ほか） |

Docker を使わない絞り込み（`web`、`domain`、`smoke`）は 11 秒で成功しました。

カバレッジ計測は本 project で未設定です。

## 受入条件とテストの対応

| 条件 | 成功側 | 失敗側 | 端の経路 |
| :--- | :--- | :--- | :--- |
| 並行する command | `PostgreSQL: 読み出しが交差した並行する CreateToDoList は、後から書く側が ConcurrentModification で拒否される` | 同左（失敗側の型） | HTTP の 409: `競合は 409 で、開き直す案内を同じ画面に出す`。in-memory は直列になり、`in-memory: 並行する CreateToDoList は、…ListAlreadyExists で拒否される`。両 store で event は重複せず、更新は失われない |
| transaction | `StoreContractTest`（版の前進） | 複数 stream の一部が古い版のとき何も残らない。`PostgresEventStoreTest` の途中で失敗した batch | 複数 stream の原子性は `StoreContractTest` を両 store で実行 |
| store の契約 | `StoreContractTest`（期待する版での追記） | 古い版での拒否、期待する版が無い stream | in-memory と PostgreSQL が同じテストを通る |
| 名前変更 | `名前を変えると、旧い stream に ListRenamed、新しい stream に作成と項目の引き継ぎを返す`、Cucumber `名前を変えても項目は引き継がれる` | `元のリストが無ければ ListNotFound`、`変名先がすでにあれば ListAlreadyExists`、`同じ名前への変更は…` | `名前を変えて戻しても、項目が二重にならない`、Cucumber `名前を変えると旧い名前は見つからない`、並行 2 件（上記） |
| 蓄積する検証 | `正しい名前は通る`、`ちょうど 40 文字の名前は通る`、`正しい入力は RenameToDoList になる` | `1 つの入力の複数の規則の誤りを、規則の順にすべて集める`、`複数の入力にまたがる誤りを、入力の順にすべて集める`、Cucumber `複数の誤りは止まらずにすべて示す` | `AccumulationLawsTest`（順序、結合律、単位元、個数）、`同じ規則を flatMap で繋ぐと、最初の失敗で止まり…`、制御文字、空白だけ |
| テンプレート | `{{x}} は HTML エスケープして埋める`、`{{{x}}} は生のまま埋める` | `埋めていないタグがあれば、名前を集めて失敗する` | `埋めた値の中のタグは、もう一度は解釈しない`、`生で埋めた値の中のタグも…` |
| 画面 | `成功は 302 で、百分率符号化した新しい名前の画面へ移る` | `検証の誤りは 400 で、すべての理由を並べ、入力を HTML エスケープして保持する` | `変名先がすでにあれば 400、元が無ければ 404`、`form に newName が無い POST は…`、`予期しない例外の本文に、内部の情報を出さない` |

## Unit 5 からの持ち越しの結果

| # | 内容 | 結果 |
| :--- | :--- | :--- |
| 1 | 並行する `CreateToDoList` の重複 | 待ち合わせで再現してから直した（第 10 章） |
| 2 | 版の確認と transaction の境界 | `version` 列と一意制約。スキーマ変更は人が承認 |
| 3 | port の Left の型を広げる | `StoreError`（`PersistenceFailure`、`ConcurrentModification`） |
| 4 | 接続の見直しと `Resource` の採否 | `use` で足り、`Resource` は使わない（ADR-042）。承認済み |
| 5 | in-memory の同期 | `Mutex` |
| 6 | 受入条件の 3 列 | 計画の表と本報告の対応表に記入 |
| 7 | 記事の断定はテスト名つき | 両章で実施。レビューで弱めた箇所あり |
| 8 | 後半で変わる型の一覧 | 6-1.1 で実施 |
| 9 | 「再現できない」で止まらない | 実施（変異で確かめた） |
| 10 | スキーマと新しい ADR の承認記録 | スキーマと計画は承認済み。ADR-042/043 も承認済み |
| 11 | 一時スクリプトはファイルに書く | 実施したが、ファイルの内容が壊れる問題が残った（ふりかえり参照） |
| 12 | データモデルの Kotlin FP 版の差分（`draft`） | 承認済み |
| 13 | `CatchAll` の本文 | 内部情報が出ていたため、固定の本文 `Internal server error` にした |

## 未了と持ち越し

- ADR-042、ADR-043、データモデルの Kotlin FP 版の差分は、人（kakimomokuri）が承認し、`stable` にしました。
- レビューの中の持ち越し 5 件（`MAX(version)`、`createSchema` の列確認、途中例外の rollback テスト、失敗応答の HTML 化、404 の導線）があります。
- 修正後の再レビューは実施していません。

## 関連ドキュメント

- [Unit 6 の Bolt 計画](iteration_plan-kotlin-fp-6.md)
- [ふりかえり](retrospective-kotlin-fp-6.md)
- [開発成果物レビュー](../review/kotlin-fp-unit6_review_20261031.md)
