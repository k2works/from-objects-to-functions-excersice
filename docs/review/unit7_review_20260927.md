---
type: Review
title: "Unit 7 開発成果物レビュー（マルチパースペクティブ）- Zettai 連載（Kotlin 版）"
description: "Zettai 連載 Kotlin 版 Unit 7（第 12〜13 章）の成果物を 5 つの XP 視点（programmer / tester / architect / technical-writer / user-representative）で並列レビューした統合レポート。高優先度 9 件・中 7 件・低 6 件の指摘と対応方針、修正内容、次のリリースへ回した項目を記録する。"
tags: [review, development, zettai, kotlin, unit-7]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-27T07:21:44Z }
---

# Unit 7 開発成果物レビュー（マルチパースペクティブ）

## レビュー対象

| 項目 | 内容 |
| :--- | :--- |
| Unit | Unit 7 監視とアーキテクチャ総括（最終） |
| 実装 | `apps/kotlin/zettai/zettai-step5-monitoring/`（`fp/Converter.kt`・`json/`・`logger/`） |
| 記事 | [第 12 章](../article/zettai/kotlin/chapter12.md)・[第 13 章](../article/zettai/kotlin/chapter13.md) |
| 報告 | [Bolt 終了報告](../development/bolt_report-7.md)・[リリース完了報告 v1.0.0](../development/release_report-1.0.0.md) |
| 差分 | `de2bde3..HEAD` |
| 視点 | xp-programmer / xp-tester / xp-architect / xp-technical-writer / xp-user-representative（並列実行） |

## 総合評価

5 視点の結論は一致しています。**設計そのものは健全で、問題は「主張と実装の距離」に集中していました。** 依存の向き（アダプタ → ドメイン）は実測で違反 0 件、`TxContext` を空にした判断と ADR-011 の記録は本 Unit で最も質が高い部分、第 13 章の判断フローは読者が実務に持ち帰れる形、節構成はマインドマップと完全一致、文体規約違反 0 件でした。

一方で、本 Unit の看板だった「ログは横断関心事なので包むだけで足した（波及 0 ファイル）」は、**`logged` に呼び出し元もテストも 1 つも無く、検証されていませんでした**（architect・programmer・tester が独立に同じ結論）。「波及 0」は「未配線」と同義になっていました。加えて `Converter.parse` が `Outcome` を返すと宣言しながら例外で落ちる経路があり、第 7 章の主張と矛盾していました。

**Unit 7 が「検査は参照を守るが主張は守らない」と学んだその場所で、同じ形の欠陥が実装側にも残っていました。**

## 改善提案（重要度順）

### 高（クローズ前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 対応 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | `logged` に呼び出し元もテストも無く「波及 0 ファイル」が検証されていない | `logger/LoggedAction.kt:12` | architect・programmer・tester | **修正**。`PostgresActions.hub()` に配線し、シナリオが 1 文字も変わらないことで主張を実証。`LoggedActionTest` 8 件を追加 |
| 2 | `logged` が結果を読まず常に `LogSuccess` を書くため、失敗が INFO で残る | `logger/LoggedAction.kt:16` | architect・programmer・writer | **修正**。`loggedOutcome`（`Outcome` を要求する版）を追加し、第 12 章に「包めるのは結果を読まない範囲まで」の節を新設 |
| 3 | `Converter.parse` が例外で落ちる経路が 4 つあり、`Outcome` を返す契約を破る | `json/EventConverter.kt:60-88` | programmer・architect | **修正**。`fromJsonObject` を包んで `Failure` にし、`EventConverterFailureTest` 6 件を追加 |
| 4 | プロファンクタ則の `contramap` 側で `parse` を検証していない（法則の半分が未検証） | `json/ConverterProfunctorTest.kt:44,79` | tester・architect | **修正**。恒等則・合成則に `parse` のアサートを追加し、合成則の変換を可逆な足し算に変更 |
| 5 | `DomainBoundaryTest` は検査対象が 0 件でも green（番人が黙る） | `smoke/DomainBoundaryTest.kt:19` | tester | **修正**。「検査対象のファイルが存在する」テストを 5 モジュールに追加 |
| 6 | 境界の検査がフレームワーク 4 件のみで、ドメインが `zettai.logger` を import しても通る | `smoke/DomainBoundaryTest.kt:16` | architect | **修正**。アダプタ 5 パッケージを検査対象に追加（5 モジュール）。第 10・13 章の記述も更新 |
| 7 | 第 13 章に第 4 章からの引用として、第 4 章に無い文があった（**捏造 3 例目**。出典はジャーナル） | `chapter13.md:74-76` | writer | **修正**。引用をやめて地の文にした |
| 8 | リリース完了報告に「ADR 全件に再検討の条件」が残っていた（第 13 章では 6 件に修正済み） | `release_report-1.0.0.md:178` | writer | **修正**。「入れない」と決めた 6 件に限定 |
| 9 | JSON エスケープ不在が第 13 章の一覧にしか無く、第 12 章だけ読む読者に届かない | `chapter12.md`・`json/JsonValue.kt` | writer・user | **修正**。第 11 章と同じ「扱わなかったこと」節を第 12 章に新設し、`JsonValue.kt` の KDoc にも明記。**限界をテストに書いた**（`引用符を含む説明は往復できない`） |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 対応 |
| :--- | :--- | :--- | :--- | :--- |
| 10 | 「どんな出来事でも往復できる」が実態より強い（生成は 2 種類のみ・`dueDate` は常に null） | `EventGenerator.kt:26`・`chapter12.md:255` | tester・programmer・user | **修正**。4 種類すべてを生成し `dueDate`・`status` も振るようにし、テスト名を「生成した出来事が往復できる」に改めた |
| 11 | 見出し「2 つの性質」に対し本文は「合計 4 つの法則」（章間の規約とずれる） | `chapter12.md:345` | writer | **修正**。「4 つの性質」に改めた |
| 12 | `Converter` は厳密なプロファンクタより制約が強い（`map`・`contramap` が両方向を要求＝不変） | `fp/Converter.kt:17`・`chapter12.md` | architect・programmer | **修正**。第 12 章に「名前は当てはめる前に、どこまで当てはまるかを確かめる」として 1 段落追記 |
| 13 | ADR 分類表が ADR-008 を二重計上し ADR-006 が漏れて実体 11 件 | `release_report-1.0.0.md:161` | writer | **修正**。006 を「設計の境界を決める」に入れ、008 の数え方を注記 |
| 14 | 提案 3 が「節構成の検査」のみで、本 Unit 最大の学び（引用の実在検査）が落ちていた | `release_report-1.0.0.md:273` | writer | **修正**。検査 2 つに改め、引用検査を優先と明記 |
| 15 | `Instant.now()` が埋め込まれ、クロックを注入できないため `at` を検証できない | `logger/Logger.kt:24`・`LoggedAction.kt` | architect | **保留**。記事のスニペットに波及するため次のリリースの Try に回す（[ふりかえり](../development/retrospective-7.md)） |
| 16 | README のモジュール一覧が step1 の 1 行のままで step2〜5 が未反映 | `apps/kotlin/zettai/README.md` | user | **修正**。5 モジュールに揃え、連載完結と**教材であり実務投入不可（認証不在）**の警告を追加 |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 対応 |
| :--- | :--- | :--- | :--- | :--- |
| 17 | 入口の技術一覧が「JSON \| Kondor」（ADR-012 で不採用） | `article/zettai/kotlin/index.md:26` | user・writer | **修正** |
| 18 | 執筆計画の第 12 章の要素が「Kondor による JSON」のまま | `article/outline.md:74` | writer | **修正** |
| 19 | 第 9 章の前方参照「第 12 章で Kondor に置き換えます」が事実と異なる | `chapter09.md:455` | 本レビューで併せて検出 | **修正**。「双方向変換の型 `Converter` に置き換えます」 |
| 20 | 第 7 章のヘルパー名は `repeatWithRandomOutcomes` で `forAllRandom` は登場しない | `chapter12.md:391` | writer | **修正**。括弧で補足 |
| 21 | Bolt 終了報告 7 件の Unit 7 だけリンクが無い | `release_report-1.0.0.md:282` | writer | **修正** |
| 22 | 読者が「自分向けか」を判断する 2 行（想定読者・読了後に得るもの）が入口に無い | `article/zettai/kotlin/index.md` | user | **修正**。想定読者と読了後に得られるものを冒頭に置き、教材である旨の警告も入口に出した。章の表に「この章の焦点」列（`outline.md` の情報）を移し、途中の章から入れるようにした |

### 追加ラウンド（要約で届いた中・低）

| # | 提案 | 箇所 | 指摘元 | 対応 |
| :--- | :--- | :--- | :--- | :--- |
| 23 | `TestDatabase.reset()` が `Outcome` を捨て、スキーマ作成の失敗を黙って通す | `TestDatabase.kt:21`（3 モジュール） | tester | **修正**。失敗したら `error()` で落とす（「skip にしない」方針と一貫させた） |
| 24 | `eventType` をクラス名から作るため、改名すると保存済みデータが読めなくなる（往復テストは green のまま） | `json/EventConverter.kt:54` | tester | **修正**。保存する文字列を固定するテストと、保存済みの 4 種類を読み戻すテストを追加 |
| 25 | `forAllRandom` がランダムのみで、範囲の端を踏まない | `property/PropertyTest.kt:18` | tester | **修正**（ユーザー承認済み）。ランダムの前に範囲の最小・最大・0 を試す `EdgeRandom` を足した（4 モジュール）。**呼び出し側は 1 文字も変わらない**。端を踏んでいること自体を `PropertyTestSelfTest` 3 件で検査し、第 5 章に節を追加 |
| 26 | `zettai.fp.Failure` が完全修飾で `Success` と揃わない | `json/EventConverter.kt:94` | tester | **修正**。import に揃えた |

## 許容・保留とした指摘

| 指摘 | 判断 | 理由 |
| :--- | :--- | :--- |
| JSON エスケープを実装していない（`"` で壊れた JSON を JSONB に書き込む） | **許容（記録済み）** | [ADR-012](../adr/ADR-012-own-json-converter.md) の「失うもの」と第 13 章の「扱わなかったこと」に記録済み。今回さらに第 12 章・KDoc・テストの 3 箇所に限界を明示した。実装すると「扱わなかったこと」の記録と齟齬が出るため、次のリリースの候補（提案 2）として残す |
| `fun main` と `./gradlew run` が無く、アプリを起動してログを見られない | **修正**（ユーザー承認済み） | `zettai-step5-monitoring` に application プラグインと `Main.kt`（インメモリの組み立て＋`stdoutLogger`）を追加。実際に起動して画面とログ 1 行 1 JSON を確認し、第 12 章・Kotlin 版索引・README に手順を記載 |
| ログに利用者名・リスト名を載せる例があり、実務で PII が流れうる | **修正** | 第 12 章に「何をログに載せないか」の節を追加（載せないもの 4 種・判断の基準・`LogContext` を型で縛る選択肢）。ユーザーの判断で第 13 章の 6 件目ではなく章内に置いた |
| `logger/JsonLogger.kt` で `context.detail` が `level`・`message` を上書きできる | **許容** | 教材の範囲。予約キーの分離は次のリリースの候補 |
| `check_adr_references.py:62` の `filename[:7]` 決め打ち | **許容** | ADR のファイル名規約が変わったときに気づく想定。正規表現化は次の連載で |
| `zettai-step1-http/build/test-results/` の残存 | **確認済み** | `.gitignore` 対象で追跡されていない |

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 判断 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | tester・programmer: JSON エスケープは「壊れた JSON を書かない」までは守るべき（実装で直す） | 第 13 章・ADR-012: 扱わないことを記録した限界 | 実装するか、限界として記録するか | **記録したうえで、限界をテストで可視化する**。黙って壊れる状態（roundTrip が Success を返す）は解消し、実装は次のリリースの判断に委ねた |
| 2 | architect: 「波及 0 ファイル」は配線されるまで比較表から外すべき | 記事・ADR・報告の 4 箇所に転記済み | 記述を弱めるか、実装を追いつかせるか | **実装を追いつかせた**（配線とテスト）。記述を弱めるより安い |

## 検証（修正後）

| 項目 | 結果 |
| :--- | :--- |
| `./gradlew clean check`（5 モジュール） | **BUILD SUCCESSFUL**（テスト 714 件。修正前は 690 件） |
| 追加したテスト | `LoggedActionTest` 8 件・`EventConverterFailureTest` 8 件・`DomainBoundaryTest` の検査対象アサート 5 件 |
| `check_article_code.py` | 違反 0 件 |
| `check_code_style.py` | 190 ファイル / 違反 0 件 |
| `check_adr_references.py` | ADR 12 件 / 章 13 件 / 違反 0 件 |
| 章をまたぐ引用の実在（使い捨てスクリプト） | 実在しない引用 **0 件**（クローズ前は 1 件） |
| DDT シナリオの変更 | **0 行**（`logged` の配線は組み立て側のみ） |

## 関連ドキュメント

- [Unit 7 の Bolt 計画](../development/iteration_plan-7.md) / [Bolt 終了報告](../development/bolt_report-7.md)
- [Unit 7 のふりかえり（KPT）](../development/retrospective-7.md)
- [リリース完了報告 v1.0.0](../development/release_report-1.0.0.md)
- [ADR-012 自前の JSON Converter](../adr/ADR-012-own-json-converter.md)
