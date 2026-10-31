---
type: Bolt Report
title: "Unit 5 の Bolt 終了報告 - Zettai 連載（Kotlin FP 版）"
description: "Kotlin FP 版 Unit 5 の射影と CQRS、PostgreSQL の event store、永続化の typed error、第 8〜9 章の完了結果を記録する。"
tags: [development, report, zettai, kotlin, arrow, postgresql, unit-5]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T06:45:16Z }
---

# Unit 5 の Bolt 終了報告 - Zettai 連載（Kotlin FP 版）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Unit | 5：射影と永続化 |
| Bolt | 5-1 第 8 章 / 5-2 第 9 章 |
| 実施日 | 2026-10-31 |
| 見積もり | US-008: 5 SP / US-009: 8 SP |
| 状態 | **完了** |

## 成果

- port を集約単位に絞りました。command 側は `(User, ListName)`、query 側は `User` で読み、全状態を replay する形を廃止しました。
- イベントから表示用の `ToDoListRow` を作る射影 `projectFrom` を、`replayFrom` と同じ `fold` で実装しました。command 側の状態から数えた行と一致することを property test で守っています。
- Hub を command 側（`execute`）と query 側（`getList`、`listRows`）の別の経路に分け、`ToDoListFetcher` を廃止しました。
- `todo_list_event` に追記する PostgreSQL の event store を、JDBC で実装しました。`jsonb_build_object` と bind parameter で payload を組み立て、`entity_id` は区切り文字を含む名前でも衝突しません。
- 読み出しと保存の失敗を `Either<PersistenceFailure, A>` で port の型に出しました。壊れた行は黙って捨てず、全体を `Left` にします。500 の本文は原因を含みません。
- `flatMap` の三法則と、Raise の `bind` の連鎖が `flatMap` と同じ結果になることを property test で確かめました。
- Cucumber の同じ scenario を、domain、HTTP、PostgreSQL の 3 経路で動かしました。
- 第 8〜9 章、ADR-038〜041、設計 3 文書、索引と nav を実装と同期しました。

## 品質結果

| ゲート | 結果 |
| :--- | :--- |
| `gradlew.bat clean check --no-daemon`（全 module） | 成功 |
| Unit 5 のテスト | 89 件成功（Kotest、Cucumber、Testcontainers 結合テストを含む。Unit 4 は 30 件） |
| step5 の `check` の 3 回連続 | 成功（37〜38 秒。最後のコード変更の後に再実行） |
| Docker を使わない絞り込み | 66 件成功（10 秒） |
| 記事検査（コード・コード形式・節構成・数値・引用・ADR 参照） | 違反 0 |
| OKF | ERROR 0、既存 WARN 10 |
| `mkdocs build --strict` | 成功（Docker 上で実行） |
| 5 観点レビュー | 重複を含めて高 8 / 中 28 / 低 24、統合後 高 7 / 中 19。修正後の高・中の残件 0（再レビューで確認）。並行実行の競合は第 10 章へ |
| 変異による確認 | `entity_id` の escape、Cucumber の PostgreSQL 経路、batch の原子性で確認 |
| CI（push 後の GitHub Actions） | 成功。Kotlin FP Zettai は `check`（Testcontainers の結合テストを含む）と article の 2 job、Deploy MkDocs は strict ビルド。Kotlin Zettai とナデシコ版の workflow も成功（run 41177663644 ほか） |

カバレッジ計測は本 project で未設定です。Unit 2 の画面と HTTP 契約を変更していないため、UI / manual の更新は対象外です。

## 受入条件とテストの対応

計画の「受入条件と検証の対応」の各行が、どのテストで検証されているかです。

| 条件 | 成功側 | 失敗側 |
| :--- | :--- | :--- |
| 射影 | `ProjectionTest`（項目数と完了数）、`ProjectionLawsTest`（replay との一致） | 未作成リストへの event、存在しない項目、重複した `ListCreated` |
| port の絞り込み | `ToDoListHubTest`（指定した集約だけを読む） | `CommandIsolationTest`（他の集約を途中に混ぜても判断が変わらない） |
| 追記と読み出し | `PostgresEventStoreTest`（順序、複数回の追記、特殊な文字、再実行） | 別の集約、区切り文字を含む名前、特殊な名前の property test |
| 永続化の失敗 | 追記と `createSchema` が `Right` を返す | 接続失敗、テーブル無し、壊れた行 5 種、途中で失敗した batch |
| Hub の保存 | `ToDoListHubTest`（検証後の event を 1 回保存） | 拒否 4 種、保存失敗、読み出し失敗 |
| HTTP の写像 | `ZettaiHttpAppTest`（200、404、400、500 の表と header） | app 経由の 500 で原因を漏らさない |
| 3 経路 | Cucumber の 2 scenario × 3 経路 | 500 と読み出し失敗を「見つからない」に紛れさせない |

## 承認ゲートについて

計画は停止 6 箇所を定めましたが、`/goal` に従って自律的に進め、人の判断を同期的には求めませんでした。計画書は承認済みで、スキーマは既存 Kotlin 版と同じ振る舞いを保ち、ADR は実測した事実だけを書きました。ADR-038〜041 は、Unit のクローズ後に人（kakimomokuri）が承認し、`verified` に記録しました。データモデルの差分は `draft` のままです。

## 第 10 章（Unit 6）への引き継ぎ

- 版の確認と transaction の境界。並行する `CreateToDoList` が `ListCreated` を重複させ、replay で項目が消える経路を、テストで再現してから直します。スキーマの変更を伴うので確認必須です。
- port の Left の型を、`PersistenceFailure` から広げます（`ConcurrentModification` を足すため）。
- `postgresEventStore(connect)` の「操作ごとに接続を開く」形の見直しと、`Resource` の採否。
- in-memory adapter の同期（`Mutex`）。
- 5-1.0（`gulp mkdocs:build` を CI と同じ判定にする）は、Unit のクローズ後に実施した。`ops/scripts/mkdocs.js` に `--strict` を足した。
- Docker が無い環境で、失敗がどう見えるかの確認。

## 関連ドキュメント

- [Unit 5 の Bolt 計画](iteration_plan-kotlin-fp-5.md)
- [Unit 5 のふりかえり](retrospective-kotlin-fp-5.md)
- [第 8 章](../article/zettai/kotlin-fp/chapter08.md)
- [第 9 章](../article/zettai/kotlin-fp/chapter09.md)
- [開発成果物レビュー](../review/kotlin-fp-unit5_review_20261031.md)
