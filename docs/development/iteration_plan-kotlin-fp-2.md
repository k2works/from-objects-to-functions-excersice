---
type: Bolt Plan
title: "Unit 2 の Bolt 計画 - Zettai 連載（Kotlin FP 版）"
description: "Kotlin FP 版 Unit 2 の HTTP ウォーキングスケルトン、domain / HTTP の Cucumber 2 経路、第 2〜3 章を定義する Bolt 計画。"
tags: [development, plan, ai-dlc, bolt, zettai, kotlin, unit-2]
status: stable
generated: { by: openai-codex/gpt-5, at: 2026-10-30T05:24:30Z }
---

# Unit 2 の Bolt 計画 - Zettai 連載（Kotlin FP 版）

## 概要

| 項目 | 内容 |
| :--- | :--- |
| Unit | 2：ウォーキングスケルトンとドメイン分離 |
| Bolt | 2-1 第 2 章 / 2-2 第 3 章 |
| アプローチ | 外側の受入テストから始めるアウトサイドイン TDD |
| テスト戦略 | Kotest の HTTP / 境界テストと Cucumber の domain / HTTP 2 経路 |

## ユーザーストーリーと受入条件

### US-002: ToDo リストを HTTP で見る

読者として、HTTP リクエストから HTML までの最小実装を再現したいです。それにより functional core / effectful shell の境界を理解できます。

- [x] `GET /todo/{user}/{list}` が存在するリストを HTML で返す
- [x] 存在しないリストは 404 を返す
- [x] ドメインは http4k と web adapter に依存しない
- [x] `suspend` と同期 http4k の接続は最外周の `runBlocking` だけで行う

### US-003: 同じ受入仕様を 2 経路で検証する

読者として、HTTP 詳細を業務シナリオから分離したいです。それにより adapter の変更と業務の変更を区別できます。

- [x] Gherkin feature は ToDo リスト閲覧を業務の言葉で表す
- [x] 同じ Gherkin scenario の各操作が domain / HTTP の両方で通る
- [x] 経路選択は test code にだけ存在する
- [x] HTTP 経路は実際の URL と HTML 変換を通る

## Bolt 2-1: 第 2 章

- [x] HTTP の失敗する受入テストで Red を確認する
- [x] `User`、`ListName`、`ToDoItem`、`ToDoList` を値として定義する
- [x] `ToDoListFetcher` を `suspend` 関数型として定義する
- [x] http4k の `HttpHandler` と HTML 変換を実装する
- [x] import を検査するドメイン境界テストを通す
- [x] 第 2 章を実装済みコードから執筆する

## Bolt 2-2: 第 3 章

- [x] ToDo リスト閲覧の Gherkin scenario と DataTable を作る
- [x] domain / HTTP の `ZettaiActions` adapter を作る
- [x] Cucumber の step definitions から 2 つの関数型 adapter を実行する
- [x] 2 経路の scenario を同じ `test` task で通す
- [x] 第 3 章で Pesticide ではなく Cucumber を選ぶ差を説明する

## 設計整合

- アーキテクチャ: domain の内側へ http4k を入れず、関数型ポートを web adapter へ渡します。
- ドメインモデル: 読み取り対象の `User`、`ListName`、`ToDoItem`、`ToDoList` だけを導入します。
- データモデル: Unit 2 はインメモリの `Map` のため、永続化モデルと ER 図は追加しません。
- UI: リスト名と項目を含む最小 HTML のみです。テンプレート化は第 11 章へ送ります。

## リスクと対策

| リスク | 対策 |
| :--- | :--- |
| coroutine の実行方法が core へ漏れる | `runBlocking` を同期 HTTP adapter の最外周に限定する |
| scenario が HTTP 詳細へ結合する | `ZettaiActions` をドメイン型だけで定義する |
| 2 経路の選択が本番コードへ入る | Cucumber step definitions でだけ選択する |
| パッケージ規約だけでは依存逆流を見逃す | import を走査する境界テストを `check` に含める |

## 完了条件

- [x] `gradlew.bat clean check --no-daemon` が green
- [x] 記事コード、節構成、数値、ADR 参照の検査が green
- [x] `mkdocs build --strict` が green
- [x] 第 2〜3 章が索引と nav から到達可能
- [x] 5 観点の開発成果物レビューで重大指摘がない

## 関連ドキュメント

- [リリース計画](release_plan-kotlin-fp.md)
- [執筆計画](../article/outline.md)
- [第 2 章](../article/zettai/kotlin-fp/chapter02.md)
- [第 3 章](../article/zettai/kotlin-fp/chapter03.md)
