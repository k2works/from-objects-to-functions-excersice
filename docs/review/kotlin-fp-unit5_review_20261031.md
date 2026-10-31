---
type: Review
title: "Kotlin FP 版 Unit 5 開発成果物レビュー"
description: "Kotlin FP 版 Unit 5 の射影と CQRS、PostgreSQL の event store、永続化の typed error、第 8〜9 章を 5 観点でレビューした結果。"
tags: [review, development, zettai, kotlin, arrow, postgresql, unit-5]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T06:37:42Z }
---

# Kotlin FP 版 Unit 5 開発成果物レビュー

## レビュー対象

- `apps/kotlin-fp/zettai/zettai-step5-cqrs-persistence/`
- `docs/article/zettai/kotlin-fp/chapter08.md`、`chapter09.md`
- `docs/design/architecture_backend.md`、`domain-model.md`、`data-model.md` の差分
- `docs/adr/ADR-038`〜`ADR-041`
- `docs/development/iteration_plan-kotlin-fp-5.md`

## 総合評価

xp-programmer、xp-tester、xp-architect、xp-technical-writer、xp-user-representative の 5 観点でレビューしました。SQL インジェクション、リソースの閉じ漏れ、キャンセルの握りつぶし、500 の本文からの情報漏えいは、どの観点からも見つかりませんでした。一方で、受入条件の片側しか検証していない箇所、偽 green になる経路、記事の主張が実装や章どうしで食い違う箇所、Docker 前提の案内不足が残っていました。

5 つの報告の指摘は、重複を含めて高 8 件、中 28 件、低 24 件でした。重複を統合すると、高 7 件、中 19 件です。高と中をすべて修正し、並行実行の問題だけは第 10 章へ送り、現状と原因を記録しました。

## 高（修正済み）

| # | 指摘 | 指摘元 | 対応 |
| :--- | :--- | :--- | :--- |
| H1 | Cucumber の HTTP 経路が 200 以外をすべて「見つからない」にする。500 でも scenario が通る | tester | 404 だけを「見つからない」にし、それ以外は失敗にした |
| H2 | `persist` と `createSchema` の成功側を一度も assert していない | tester | 成功を直接 assert するテストを追加し、`reset` でもスキーマ作成の失敗を見逃さない |
| H3 | `append` の失敗時に rollback が明示されておらず、途中で失敗した batch のテストも無い | architect、programmer、tester、writer | 失敗時に明示的に rollback して投げ直す。2 件目の INSERT が失敗する batch を作り、1 件も残らないことをテストにした |
| H4 | 並行実行で状態が壊れる具体的な経路が記録されていない | architect | ADR-040 の結果と第 9 章の限界に、重複した `ListCreated` と `ChangeItemStatus` の経路を書いた。推論でありテストでは再現していないことも書いた。対策は第 10 章 |
| H5 | 第 8 章の実行コマンドが、実際には Docker を必要とする | user-rep | Docker なしで動かせる絞り込みコマンドを併記し、実際に実行して通ることを確かめた |
| H6 | 第 9 章に、Docker が必要なことの案内が章末の 1 文しかない | user-rep | 確認方法（`docker version`）、Docker Desktop の起動、初回の image 取得、絞り込みの逃げ道を書いた。シリーズ索引にも書いた |
| H7 | 第 8 章が query は `Either` を返すと書きながらコードは `List` を返す。第 9 章は query の変更を書いていない | writer | 第 8 章は「第 9 章で `Either` になる」と先に書き、第 9 章に `getList` と `listRows` のコードと説明を足した |

## 中（修正済み）

| # | 指摘 | 指摘元 | 対応 |
| :--- | :--- | :--- | :--- |
| M1 | `decode` が列番号で SQL に結合している | programmer | SQL に別名を付け、名前で読む |
| M2 | `PostgresEventStore` と `InMemoryEventStore` が同じ形の型で重複 | programmer | `EventStore` を application に 1 つだけ置く |
| M3 | 状態と射影の「最初に一致した項目を変える」規則の重複 | programmer | `withStatusChanged` に抜き出して共有。畳み込みの骨格は 2 か所に残り、property test で一致を守る |
| M4 | `listRows` を本物の store で通した検証が無い | tester | in-memory と PostgreSQL で、command で書き込んだ結果を `getList` と `listRows` が別の経路で同じ内容として返すことを確かめるテストを追加 |
| M5 | 「他の集約が混ざっても判断が変わらない」が実際には混ぜていない | tester | 他の集約のイベントを途中に挟む property test を追加 |
| M6 | 結合 property の生成器が偏っている | tester | `/`、`%`、空文字、日本語を含む名前の生成器と、全集約・全ユーザーの読み戻しを追加 |
| M7 | `arbError` に `PersistenceFailure` がなく、error の生成器が重複 | tester | `PersistenceFailure` を足し、生成器を 1 つにした |
| M8 | 境界テストが JDBC の閉じ込めと adapter 同士の独立を守らない | architect、tester | domain と application から JDBC を、adapter 同士の依存を、`runBlocking` の出現を禁止するテストを追加 |
| M9 | 計画の受入条件と実装の型が不一致（`EventPersister` の失敗型） | architect、user-rep | 型を狭めた判断として計画に記録した |
| M10 | アーキテクチャ設計に古い記述が残る | architect | package 表に `adapter.postgres`、境界テストの範囲、port 表の注記を直した |
| M11 | ドメインモデル設計の `ZettaiError` の表と図に `PersistenceFailure` がない | architect | 表と図に足し、orphan event を無視する規則を残す理由を記録した |
| M12 | ADR-039 の例が廃止済みの名前のまま | architect | 第 4〜7 章の形であることと、第 8・9 章での変更を注記した |
| M13 | 第 8 章が Cucumber の出どころを「第 5・6 章」と誤記 | user-rep、writer | 「第 3 章」に直した |
| M14 | 「集約」の定義が無く、型の別名や生成器が説明なしに出る | user-rep | 定義と、型の別名の説明を足した。一致の property test を連続した断片にした |
| M15 | 第 9 章に Testcontainers を採る理由が無い | writer | 既存 Kotlin 版が退けた 3 つの理由への答えを表で書き、ADR-041 を作った |
| M16 | 「接続できないとき失敗」「何も残りません」「再起動してもリストが残る」が強すぎる | writer、user-rep | 確かめた範囲に合わせて書き直した。アプリケーションの起動時の配線は扱わないと明記した |
| M17 | jsonb の正規化の説明が不正確で、FP 版に当てはまらない | writer | 「一致するとは限らない」に弱め、FP 版が影響を受けない理由を書いた |
| M18 | 選択差の表に、一致する項目を変える範囲の違いが無い | writer | 既存 Kotlin 版は一致する項目すべて、FP 版は最初の 1 件、と表に足した |
| M19 | シリーズ索引の代数構造の件数と ADR の件数が古い | writer | 3 と 4 件に直した |

## 保留（第 10 章以降へ引き継ぐ）

| 論点 | 指摘元 | 方針 |
| :--- | :--- | :--- |
| 並行実行時の競合の検出（版の確認） | architect | 第 10 章で transaction と合わせて扱う。スキーマの変更を伴うので確認必須 |
| port の Left の型を `PersistenceFailure` から広げる | architect | 第 10 章の計画に、port の Left の型を広げるタスクを入れる |
| `postgresEventStore(connect)` の操作ごとに接続する形 | architect | 第 10 章で `Resource` の採否と合わせて見直す |
| in-memory adapter の同期 | architect | 第 10 章で `transact` を入れるときに `Mutex` を検討する |
| `PersistenceFailure.reason` の記録 | programmer、architect | 記録の仕組みは扱わない。第 10 章以降で web の最外周に置く案 |
| `CatchAll` が捕まえるその他の例外の本文 | programmer | 第 4 章の時点から続く経路。command route を足す章で見直す |
| ADR-038〜041 の承認 | architect | 4 本が draft のまま。人の承認を得て `verify` する |

## 低優先度・保留

- `events` が空のとき接続を開かない短絡、永続化名としての enum 名の注記、`runTest` の書き方の統一、`postgres:18-alpine` の tag の固定、BoundaryTest の完全修飾名と `import kotlinx.coroutines.*` の検出
- 第 8 章の実装済みの形（`Either` を返す port）への注記は、第 8 章の本文に入れた

## 未確認のこと

- **Docker が無い環境で、失敗がどう見えるか。** Testcontainers は成功した接続方法を `~/.testcontainers.properties` に記憶するため、環境変数では再現できなかった。利用者の設定ファイルを書き換えてまでは試していない。
- **push 後の CI。** まだ push していない。ローカルでは `clean check` と、step5 の `check` の 3 回連続の成功を確かめた。

## 検証結果

- `gradlew.bat clean check --no-daemon`: 成功
- Unit 5 のテスト: レビュー前 78 件、修正後 89 件
- Docker を使わない絞り込みの実行: 成功（66 件）
- step5 の `check` の 3 回連続: 成功（最後のコード変更の後に再実行）
- 記事コード・コード形式・節構成・数値・引用・ADR 参照検査: 違反 0
- OKF: ERROR 0（既存 template 警告 10）
- MkDocs strict build: 成功

## 判定

高・中重要度の指摘（H1〜H7、M1〜M19）は、すべて修正済みです。独立した再レビュー（読み取り専用のエージェント 1 体）が、26 件の解消を実物で確認しました。再レビューで新たに出た指摘（第 9 章の `getList` と `listRows` の説明の不正確さ、他の集約を混ぜるテストの置き場所、ADR-041 の断定の強さ、表のセルの文体など）も修正しました。

残るのは、第 10 章へ送る並行実行の競合と、人の判断が要る ADR-038〜041 の承認、push 後の CI、Docker が無い環境での失敗の見え方です。Unit 5 の完了条件を満たします。
