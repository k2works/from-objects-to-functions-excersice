---
type: Review
title: "Kotlin FP 版 Unit 4 開発成果物レビュー"
description: "Kotlin FP 版 Unit 4 の command ADT、状態遷移、Arrow typed error、第 6〜7 章を 5 観点でレビューした結果。"
tags: [review, development, zettai, kotlin, arrow, unit-4]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-31T05:40:00Z }
---

# Kotlin FP 版 Unit 4 開発成果物レビュー

## レビュー対象

- `apps/kotlin-fp/zettai/zettai-step4-command-errors/`
- `docs/article/zettai/kotlin-fp/chapter06.md`
- `docs/article/zettai/kotlin-fp/chapter07.md`
- `docs/design/architecture_backend.md`、`docs/design/domain-model.md` の差分
- `docs/development/iteration_plan-kotlin-fp-4.md`

## 総合評価

xp-programmer、xp-tester、xp-architect、xp-technical-writer、xp-user-representative の 5 観点でレビューしました。依存方向、純粋な command handler、検証後だけの永続化、網羅 `when` による error mapping は計画どおりで、記事のコード例は実装と全件一致します。一方で、エラー response の Content-Type、受入条件の片側しか見ていないテスト、記事中の計画用語と既存 Kotlin 版との選択差の不足が残ります。重複を統合した結果は高 6 件、中 9 件、低 12 件です。

品質ゲート（レビュー前）：`gradlew.bat clean check --no-daemon` 成功、記事検査 6 種違反 0、OKF ERROR 0（既存 WARN 10）、`mkdocs build --strict` 成功。

## 改善提案（重要度順）

### 高（Unit 4 クローズ前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
| :--- | :--- | :--- | :--- | :--- |
| H1 | エラー response に `text/plain; charset=utf-8` と `X-Content-Type-Options: nosniff` を付け、header をテストする | `web/ZettaiHttpApp.kt:51,55` | programmer | path 由来の名前を Content-Type なしで返しており、MIME sniffing による反射型 XSS の余地がある。成功側は escape 済みで非対称 |
| H2 | `InvalidTransition` などで拒否したとき persist 0 回を Hub レベルで検証する（4 error を表駆動） | `ToDoListHubTest.kt:39-54` | tester | 現状は `ListNotFound` の短絡だけで、章の主題である遷移拒否の経路を見ていない |
| H3 | 本文の「Unit 4/5/6」を章番号か「後の章で」に置き換える | `chapter06.md:16,213`、`chapter07.md:201` | user-rep | 読者は Unit を知らない。第 1 章にも同じ表現がある |
| H4 | port の型変更（`ToDoList?` → `Either<ZettaiError, ToDoList>`）を Before / After で示し、波及ファイル数を既存 Kotlin 版と比べる | `chapter07.md:131-138` | user-rep | step3 と step4 を比べる読者が差分を探す羽目になる |
| H5 | Arrow Raise の説明を「内部では非局所脱出、外から見える契約は `Either`」に直し、`Throwable` を catch すると raise を握りつぶす注意を入れる | `chapter07.md:170` | writer | 「例外による非局所的な脱出ではなく」は不正確 |
| H6 | 既存 Kotlin 版の `Outcome` と Arrow `Either` の対応表（`map`、`flatMap`↔`bind`、`onFailure`↔`fold`、共変、変更ファイル数）を置く | `chapter07.md:142-144` | writer、user-rep | 成功条件「選択差を各章で説明する」と計画「自前抽象との比較」を満たしていない |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
| :--- | :--- | :--- | :--- | :--- |
| M1 | `ToDoListState.from` に可変参照を渡す handler の aliasing test を足す（使わないなら削除） | `CommandHandlerTest.kt`、`ToDoListState.kt:25-31` | tester、programmer | 計画 Bolt 4-1 のタスクが未実施で、`from` は dead code |
| M2 | error mapping を 4 種すべて表駆動でテストする | `ZettaiHttpAppTest.kt:41-71` | tester、programmer | `ItemNotFound` / `ListAlreadyExists` の写像が未検証 |
| M3 | `ChangeItemStatus` × `ListNotFound` の分岐をテストする | `CommandHandler.kt:40-42` | programmer | 唯一の未テスト分岐 |
| M4 | 成功テストの fake で受け取った `(user, listName)` を assert する | `ZettaiHttpAppTest.kt:27-29` | tester | path 抽出の取り違えを検出できない |
| M5 | BoundaryTest に `web ↛ adapter`、`adapter ↛ web / http4k`、application の `kotlinx.coroutines.*` 禁止を足す | `smoke/BoundaryTest.kt` | tester、architect | 計画の「driving / driven の矢印を分ける」が未反映 |
| M6 | `EitherFunctorLawTest` の生成器を強める（4 error の `Arb.choice`、関数の生成、`Arb.int()`） | `EitherFunctorLawTest.kt:18-35` | tester | 法則は任意の関数・値について成り立つべき |
| M7 | 第 6 章で `either` / `ensure` / `ensureNotNull` を 1〜2 文説明し、既存 Kotlin 版（空 list で拒否）との違いを比較表で示す | `chapter06.md:113-135` | writer、user-rep | 説明が第 7 章まで無く、第 7 章冒頭の動機が FP 版読者に届かない |
| M8 | description の重複をどう扱うか決める（禁止するか、前提として明記するか）。あわせて「新しい項目を作る」規則の記述を直す | `CommandHandler.kt:32-44`、`chapter06.md:44-48,82` | 全 5 観点 | 同じ description の 2 件目は状態を変えられず、記事の業務規則と矛盾する |
| M9 | 記事の細部：`bind` は DSL の `flatMap` と明記、末尾 lambda の問題が compile error で検出されたと明記、第 7 章に実行コマンドを追加、`handle` を 1 ブロックで掲載 | `chapter06.md:115-163,209`、`chapter07.md:172,251-257` | writer、user-rep | outline の成功条件（`map` / `flatMap`）と再現性 |

### 低（改善の余地あり）

- `ToDoListHub.getList` の型を `ToDoListFetcher` alias にする（programmer、architect、writer、user-rep）
- 遷移表テストを `withData` で 16 件の名前付きケースにし、`ToDoStatus.entries.size` と比較する（tester、programmer）
- 遷移表を網羅 `when` にするか、Map を選んだ理由を記事に残す（programmer）
- リスト存在チェックを `Raise<ZettaiError>` 拡張関数に抽出する（programmer）
- `CommandHandlerTest.kt:26` の FQN を import にする（programmer）
- `InvalidTransition` に `user` / `listName` を持たせる（programmer）
- `apply` の防御分岐（孤立 event で `this` を返す）をテストする（tester）
- step4 の `Replay.kt` の補助関数にテストを持ち込むか削除する（tester、programmer）
- `BoundaryTest` の作業ディレクトリ前提を build.gradle.kts の `workingDir` で明示する（tester）
- architecture_backend.md の図に web → domain などの矢印を足し、計画の Arrow 利用範囲に driven adapter を含める（architect）
- 400 写像テストに「command route 接続に備えた変換表の検証」と注記する（programmer、architect、user-rep）
- 記事：`ListAlreadyExists` を 400 にした理由、`ensureNotNull` が値を返すこと、domain は coroutine 自体に依存しないこと、第 8 章予告に「射影」を入れる、frontmatter の `status` と公開表記の整合（writer、user-rep）

## Unit 5 以降への引き継ぎ

Unit 4 の範囲（in-memory、command route 未接続）では顕在化しないため、Unit 5 の計画に明示的なタスクとして入れます。

| 論点 | 指摘元 | 方針案 |
| :--- | :--- | :--- |
| `StateFetcher` が全状態を replay する | architect（高） | 集約単位で読む `suspend (User, ListName) -> ToDoList?` に絞る |
| トランザクション境界と `persist` の typed error | architect（高）、programmer、tester | `transact` 関数の注入と `Either<ZettaiError, Unit>`、`ConcurrentModification` の追加を ADR で決める |
| 読み取り側と event store が未接続 | architect（中） | projection と整合性モデルを Unit 5 で決める |
| Kotlin FP 版の ADR が無い（Arrow 採用、関数型 DI、suspend port） | architect（中） | Unit 5 着手前に ADR を作る |
| `runBlocking` と JDBC の blocking I/O | architect | `Dispatchers.IO` に閉じ込めるか同期 port に戻すかを ADR で決める |
| replay が不正な event を黙って捨てる | architect、tester | 永続化導入時に検出方針を決める |
| `ToDoListState.apply` が stdlib の `apply` と同名 | programmer、architect | `evolve` などへの改名を検討する |

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | programmer：`handle` を `Raise<ZettaiError>.handle` にすれば `.bind()` が不要 | writer / user-rep：第 6 章で Either を見せる流れが前提 | 記事の到達順序 | 記事で `Either` を返す形を見せるため現状維持 |
| 2 | user-rep：description 重複は前提の明記で十分 | writer：記事修正か重複チェック追加かは人の判断 | 業務ルール | 人が決める（M8） |

## 対応

人の判断で、高・中の 15 件を Unit 4 のクローズ前に修正し、低は保留としました。M8 はコードを変えず、記事に前提を明記する方針としました。

| # | 対応 |
| :--- | :--- |
| H1 | error response に `text/plain; charset=utf-8` と `nosniff` を付与。header の検証を先に Red にしてから実装した |
| H2 | 4 種の拒否それぞれで persist が 0 回であることを Hub の表駆動テストで固定 |
| H3 | 第 6〜7 章の「Unit N」を「この章で」「後の章で」に置き換え |
| H4 | 第 7 章に port の型の Before / After と、波及ファイル（既存 5 + 新規テスト 1）の表を追加し、既存 Kotlin 版の 6 ファイルと比較 |
| H5 | Raise は内部で非局所脱出するが外から見える契約は `Either` と訂正。`Throwable` / `runCatching` の注意を追加 |
| H6 | 既存 Kotlin 版 `Outcome`（`transform`、拡張関数 `orElse`、`fold`）と Arrow `Either` の対応表、共変で踏んだ失敗との差を追加 |
| M1 | `ToDoListState.from` に可変参照を渡す handler の aliasing test を追加 |
| M2 / M4 | HTTP の error mapping を 4 種の表駆動テストにし、path から取り出した `(user, listName)` を検証 |
| M3 | `ChangeItemStatus` × `ListNotFound` のテストを追加 |
| M5 | BoundaryTest に `web ↛ adapter`、`adapter ↛ web / http4k / coroutines` を追加し、application の coroutine 依存を全面禁止 |
| M6 | 生成器を 4 種の error、`Arb.int()`、生成した関数に拡張し、`collect` で分類を観測 |
| M7 | 第 6 章に `either` / `ensure` / `ensureNotNull` の説明と、既存 Kotlin 版（空 list で拒否）との比較表を追加 |
| M8 | description を識別子とみなし重複は扱わない前提を明記し、規則を「別の description で新しい項目を作る」に修正 |
| M9 | `bind` は DSL の `flatMap` と明記、末尾 lambda が compile error になることを明記、第 7 章に実行コマンドを追加、`handle` を 1 ブロックで掲載 |
| 低（一部） | `ToDoListHub.getList` を `ToDoListFetcher` alias に、`CommandHandlerTest` の FQN を import に、`ListAlreadyExists` を 400 にした理由と、遷移規則が Zettai 固有であることを記事に追記 |

再レビュー（独立エージェント 1 体）で高・中 15 件の解消を確認しました。再レビューで新たに出た中 1 件（波及表の内訳が step3 の実際と不一致）は修正済みで、低 1 件（domain の変更理由の限定）も同じ修正で対応しました。

## 低優先度・保留

上記で対応した項目を除く低優先度の指摘は保留とし、該当する Unit で再検討します。

## 検証結果

- `gradlew.bat clean check --no-daemon`: 成功
- Unit 4: Kotest 30 件が成功（修正前 21 件）
- 記事コード・コード形式・節構成・数値・引用・ADR 参照検査: 違反 0
- OKF: ERROR 0（既存 template 警告 10）
- MkDocs strict build: 成功

## 判定

高・中重要度の指摘はすべて修正済みです。Unit 4 の完了条件を満たします。Unit 5 以降への引き継ぎ事項は、上の表のとおり Unit 5 の計画に入れます。
