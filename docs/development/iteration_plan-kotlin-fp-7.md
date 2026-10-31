---
type: Plan
title: "Unit 7 の Bolt 計画 - Zettai 連載（Kotlin FP 版）"
description: "Kotlin FP 版 Unit 7 の構造化ロギング、関数型 JSON とプロファンクタ、データベース呼び出しのロギング、関数型アーキテクチャの総括、第 12〜13 章、連載全体の品質ゲートを定義する Bolt 計画。"
tags: [development, plan, ai-dlc, zettai, kotlin, arrow, logging, json, unit-7]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T12:37:18Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-31T12:21:58Z }
---

# Unit 7 の Bolt 計画 - Zettai 連載（Kotlin FP 版）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Unit | 7：監視と総括 |
| Bolt | 7-1 第 12 章「監視と関数型JSON」 / 7-2 第 13 章「関数型アーキテクチャの設計」 |
| 対象ストーリー | US-012（構造化ロギングと関数型 JSON、プロファンクタ）、US-013（設計判断の総括） |
| 前提 | [Unit 6 の Bolt 終了報告](bolt_report-kotlin-fp-6.md)。新しい module `zettai-step7-observability` を Unit 6 の snapshot から作る |
| 局面 | 終盤（アウトサイドイン）。最後の Unit |
| アプローチ | 運用で見たい事実（何が起きたか、機密が漏れていないか）を受け入れ側から書き、内側へ進める。第 13 章は実装を足さず、既存の判断をテストと文書で裏付ける |
| テスト戦略 | 性質テスト（converter の恒等則と合成則、往復）、decorator のテスト（ログに載るもの、載らないもの）、既存の契約テストと Cucumber の 3 経路は変えない |
| ゲート密度 | **疎**（停止 3 箇所 + 条件付き 1） |
| 見積もり | US-012: 5 SP / US-013: 3 SP、フィーチャバッファ 30% |

ゲート密度の根拠（Kotlin FP 版固有）：Unit 6 は、スキーマ変更、画面、新しい ADR が重なったので中でした。Unit 7 は、スキーマの変更も新しい集約も無く、既存の Hub と store を包む decorator と、新しい変換の層だけです。疎に下げられます。ただし、取り戻せない判断が 2 つあります。(1) 新しい依存の追加と、承認済みの ADR-040 の部分的な見直し（停止 1）、(2) ログに載せる個人情報の範囲（停止 2）です。この 2 つと、記事の全文（停止 3）に絞り、第 13 章は主張が裏付けられないときだけ止まります（条件付き停止 4）。なでしこ3 版の疎の根拠は流用していません。

## ゴール

Hub と event store を包む decorator で、機密を含まない構造化ログを出し、辞書と JSON の双方向変換を純粋な converter として作り、第 12 章として公開します。続けて、第 1〜12 章の設計判断を関数型アーキテクチャとして総括し、既存 3 版（Kotlin、Rust、なでしこ3）と並べて、第 13 章として公開します。最後に、連載全体（全 13 章、全 module）の品質ゲートを確かめます。

## 設計上の問い

Unit 6 の持ち越しと開発戦略から、この Unit が答える問いは 3 つです。

| 問い | この Unit での答えの出し方 |
| :--- | :--- |
| 副作用（ログ）を、domain に持ち込まずに足せるか | Hub と store を包む decorator にする。domain と application の import は変えない（`BoundaryTest` で固定） |
| ログに載ってはならないものは何か | 載せるもの（command の種類、結果の型、所要時間、stream 数）と、載せないもの（項目の説明、`PersistenceFailure.reason`、利用者の入力そのもの）を、先に表にして、テストで固定する |
| 辞書と JSON の変換を、副作用なしに合成できるか | `Converter<A, B>`（`to` と `from`）を純粋な値にし、`dimap` で入力と出力を写せることを、法則（`dimap id id = id`、`dimap (f ∘ g) (h ∘ k) = dimap g h ∘ dimap f k`）で確かめる。既存 Kotlin 版は `map` と `contramap` を「厳密なプロファンクタではない」と断っている。この版は `dimap` を持つ形にして、断りが要らないかを法則で確かめる |

### ログに載せるもの、載せないもの

| 載せる | 載せない |
| :--- | :--- |
| command または query の種類 | 項目の説明、期日 |
| 結果の型（成功、`ListNotFound`、`ConcurrentModification` など） | `PersistenceFailure.reason`（接続先を含みうる） |
| 利用者名とリスト名（識別子） | 入力された新しい名前の誤った値 |
| 所要時間（ミリ秒） | 例外のスタックトレース |
| store の操作（読み出し、追記）と stream の数、event の件数 | event の payload |

利用者名とリスト名を載せるかどうかは、業務上の判断です。計画では「載せる」とし、停止 2 で人が確認します（個人を特定しうるため）。

### 既存コードの前提（実コードで確認済み）

- `ToDoListHub` は `getList`、`listRows`、`execute` の 3 つの関数を持つ data class。decorator は、この 3 つを包む。command または query の種類は、`execute` が受ける command の型名と、`getList`、`listRows` という関数の別から決める。
- `EventStore<Tx>` は interface ではなく、`readAggregate`、`readUser`、`transaction`、`readStream`、`append` を持つ data class。`Tx` は総称なので、store の decorator も `<Tx>` を持つ。包む対象は、transaction の外の `readAggregate`、`readUser` と、transaction の中の `readStream`、`append`。`transaction` 自体は包まない。
- decorator は新しい package `zettai.adapter.logging` に置く。依存の向きは adapter → application / domain で、既存の依存方向に合う。`zettai.adapter.postgres`、`zettai.adapter.inmemory`、`zettai.web` には依存しない。HTTP の request log の filter は web に置く。application と domain は decorator を知らない（組み立ては、アプリの組み立て箇所とテストで行う）。

## JSON の道具の決定（停止 1 に出す比較）

承認済みの [ADR-040](../adr/ADR-040-jdbc-blocking-boundary.md) は、「kotlinx.serialization などの JSON ライブラリは採らない」と決めました。理由は「新しい依存は確認が必要で、SQL 側で payload を組み立てられるため要らない」でした。第 12 章は用途が違います。ログの行を組み立てるだけでなく、`Converter` の `from`（JSON から値へ）で、壊れた入力を `Left` にする読み取りが要ります。理由が変わるので、ADR-040 を部分的に見直す新しい ADR を作り、人が承認します。置き換えではなく、「DB の payload は SQL 側で組む」という ADR-040 の決定は残し、「ログと converter の JSON」だけに別の決定を足します。

| 案 | 内容 | 判断 |
| :--- | :--- | :--- |
| A. `kotlinx-serialization-json`（`JsonElement` の木だけ。注釈と plugin は使わない） | parser と printer が揃う。特殊文字の escape を任せられる | **既定**。`from` に parser が要るので |
| B. 自前の JSON の ADT と printer（parser なし） | 依存が増えない | `from` を持てず、converter が双方向にならない。採らない |
| C. 自前の ADT、printer、parser | 依存が増えない | 過剰。escape と数値の扱いを自前で守ることになる。採らない |
| D. http4k の JSON module | http4k に寄る | web 層の依存を増やす。logging の decorator が web に依存してしまう。採らない |

版と変更の手順（7-1.3 のスパイクで確かめる）：Kotlin 2.4.21 と整合する版を選び、`gradle/libs.versions.toml` に足す。依存は `zettai-step7-observability` だけに足す（他の module の依存は変えない）。root の `build.gradle.kts` の共通設定は変えない。

## Unit 6 からの引き継ぎ

[Unit 6 の Bolt 終了報告](bolt_report-kotlin-fp-6.md)、[ふりかえり](retrospective-kotlin-fp-6.md)、[開発成果物レビュー](../review/kotlin-fp-unit6_review_20261031.md)の持ち越しを、次のステップへ割り当てます。

| # | 引き継ぎ | 消化するステップ |
| :--- | :--- | :--- |
| 1 | Gradle の実行は `apps/kotlin-fp/zettai` で行い、出力を絞るときは `BUILD` の行が無ければ失敗として扱う | 全 Bolt の作業規則 |
| 2 | ファイルを書き換えるスクリプトは、バックスラッシュを `chr(92)` で組み立てるか、編集ツールを使う。実行後に書き換えたファイルを確かめる | 全 Bolt の作業規則 |
| 3 | `sealed` の制約（同じ package と module）を、型を移す前に確かめる | 全 Bolt の作業規則 |
| 4 | 小さな関数でも、テストを先に走らせて Red を確認する | 全 Bolt の作業規則 |
| 5 | 受入条件の表は、実装中に分かったことで直し、Bolt の終了報告で最終版にする。文言はテスト名で言い直せるものだけにする | 受入条件表・7-2.6 |
| 6 | レビューの指摘は、「直す」「持ち越す」を決める基準（利用者に見えるか、データが壊れるか）を先に持つ | 7-2.5 |
| 7 | 低の持ち越し：`ZettaiHttpApp.kt` の `showList` と `showRenameForm` が、`User(request.path("user").orEmpty())` と `ListName(request.path("list").orEmpty())` を繰り返している（Unit 6 のレビューは `Request.listKey()` と呼んだが、その関数は実在しない）。`private fun Request.userAndList()` に寄せる | 7-1.1（request log の filter が同じ path を読むので、先に直す） |
| 8 | 低の持ち越し：名前変更の成功の知らせ | 含めない（理由は下の表） |
| 11 | ふりかえり Try：失敗応答（404、500）を HTML にするかは、「次の Unit の最初に決める」。Kotlin FP 版の契約は、フォームを伴わない失敗をプレーンテキストで返す（`ZettaiHttpAppTest`）。request log が 404、400、409、500 を扱うので、先に決める | 7-1.1。**決定（7-1.1）**：フォームを伴わない失敗は、プレーンテキストのままにする。request log はステータスだけを記録するので、本文の形式に依らない。HTML にするのは、利用者が入力を直せる名前変更のフォームだけ |
| 12 | 低の持ち越し：`rename` の分岐が 3 種類の error 型を列挙し、`describeError` の `else` が実質使われない | 7-1.1 |
| 9 | HTTP と PostgreSQL の組み合わせが、どの経路でも検証されていない | 7-2.4（第 13 章で「確かめていない組み合わせ」として整理する。検証は含めない） |
| 10 | `Transaction<Tx>` の戻り値が固定されている。command 以外の用途が現れたら総称にする | 含めない。ロギングの decorator は `Transaction` を包まず、`readStream` と `append` を包むので、必要が現れない |

次の項目は、理由を付けてこの Unit に含めません。

| 項目 | 扱い |
| :--- | :--- |
| 名前変更の成功の知らせ | 含めない。画面の改善は第 11 章で一区切りにした。総括の章に、実装を足さない |
| 利用者のリスト一覧の画面 | 含めない。第 8 章の `listRows` を使う画面は連載の範囲外 |
| 分散トレース、メトリクスの収集基盤 | 含めない。構造化ログまでを扱う。収集基盤は運用環境の話で、連載の範囲外 |
| 既存 3 版の記事の修正 | 含めない。比較は第 13 章と横断比較に書き、既存の記事は変えない |
| GitHub Release、バージョンタグの作成 | 含めない。外向きで戻しにくい操作なので、人が判断する（連載の完了報告書は作る） |

## 見積もり

| ストーリー | SP | 見積もりの根拠 |
| :--- | :--- | :--- |
| US-012 | 5 | decorator 2 層（Hub、store）、converter と法則、新しい依存、HTTP の request log、記事 |
| US-013 | 3 | 実装は足さない。対応表の裏付けと、横断比較と、全品質ゲート |

## ユーザーストーリー

### US-012: 運用で何が起きたかを、機密を漏らさずに知る

運用者として、command と store の操作が何をして、どう終わったかを、構造化されたログで知りたいです。それにより、障害の原因を、利用者の入力を見ずに追えます。

- [ ] Hub を包む decorator が、command または query ごとに 1 行の構造化ログ（JSON）を出す。載せるものは上の表のとおり
- [ ] ログに、項目の説明、`PersistenceFailure.reason`、例外のスタックトレースが載らない。機密を含む入力で、テストする
- [ ] 失敗（`Left`）も、成功と同じく 1 行で出る。結果の型が載る
- [ ] event store を包む decorator が、読み出しと追記を、stream の数と event の件数と所要時間で記録する。payload は載せない
- [ ] 時刻は引数で与え、テストは決定的に書ける
- [ ] decorator は domain と application の import を増やさない（`BoundaryTest`）。decorator は adapter 側に置く
- [ ] 辞書と JSON の変換を `Converter<A, B>` で表し、`dimap` を持つ。恒等則と合成則、往復（`from(to(x)) == x`）を property test で満たす
- [ ] HTTP の request log（method、path、status、所要時間）を出す。body と query は載せない
- [ ] 節構成が既存 Kotlin 版の第 12 章（アプリケーションを監視する／構造化されたロギング／JSONを関数型にする／プロファンクタとの出会い／データベース呼び出しのロギング／まとめ）と一致する

### US-013: 設計判断を総括する

読者として、13 章で積み上げた設計判断を、ひとつの見取り図として知りたいです。それにより、自分のコードに、どの判断を持ち帰れるかを決められます。

- [ ] 第 13 章の主張を、対応するテストとコードの箇所で裏付ける。5 つの代数構造（モノイド、ファンクタ、モナド、アプリカティブ、プロファンクタ）ごとに、どの章のどのテストが対応するかを表にする。対応が無いものは「無い」と書く
- [ ] effect の境界（`runBlocking` は web だけ、JDBC は adapter だけ）を、`BoundaryTest` のテスト名で示す
- [ ] 採用しなかった道具（`Resource`、Arrow の optics、既製の template エンジン、悲観ロックなど）を、理由つきで列挙する。確かめていないことは、確かめていないと書く
- [ ] 既存 3 版との比較（横断比較）に、Kotlin FP 版を加える。「実測」は、章数、module ごとのテスト件数（Gradle の `test-results` の XML の合計）、本番コードの `.kt` の行数（`git ls-files` で数える）と定め、数えた日付とコミットを書く
- [ ] 節構成が既存 Kotlin 版の第 13 章（シンプルさを追い求めて／システム全体の設計／コードへの翻訳／最後の考察）と一致する（章末の「連載を終えて」は検査の対象外）
- [ ] 連載全体の品質ゲート（全 13 章の記事検査、全 module の `clean check`、`mkdocs build --strict`、OKF、CI）が green

### 新規ファイルの扱い

CLAUDE.md は新規ファイルの作成を確認必須としています。この計画の承認で、次の新規ファイルの作成を承認したものとします。承認の範囲外が出た場合は、その時点で止まります。

- `apps/kotlin-fp/zettai/zettai-step7-observability/`（module 一式）と、`settings.gradle.kts` の `include`
- `docs/article/zettai/kotlin-fp/chapter12.md`、`chapter13.md`
- 新しい ADR（ログの decorator、JSON の依存と converter）。ADR-040 の部分的な見直しを含む
- Bolt 終了の文書（レビュー、ふりかえり、Bolt 終了報告）と、連載の完了報告書

依存の追加と、ADR-040 の見直しは、これとは別に停止 1 で承認を求めます。

### 受入条件と検証の対応

条件ごとに、成功側、失敗側、端の経路のテストを書きます。テスト名は実装時に記入し、Bolt 終了報告で突合します。実装中に分かったことで、この表を直します。

| 条件 | 成功側 | 失敗側 | 端の経路 |
| :--- | :--- | :--- | :--- |
| Hub のログ | command の成功が 1 行で出る | `Left` も 1 行で出る。結果の型が載る | query も出る。時刻は決定的 |
| 機密の除外 | 載せるものが載る | 項目の説明、`reason`、スタックトレースが載らない | 入力に JSON の特殊文字が入っても、ログが 1 行の正しい JSON のまま |
| store のログ | 読み出しと追記が記録される | 追記の失敗（`ConcurrentModification`）も記録される | payload が載らない。stream の数が複数（名前変更）でも 1 行 |
| converter | `to` と `from` が往復する | 壊れた JSON は `Left`（例外を投げない） | 恒等則と合成則。`dimap` の入出力の写し |
| request log | method、path、status、所要時間が出る | 404、400、409、500 も出る | body と query が載らない |
| 境界 | decorator が adapter 側にある | domain と application が新しい import を持たない | 既存の `BoundaryTest` が green のまま |
| 総括 | 対応表の各行にテスト名がある | 対応が無い行は「無い」と明記 | 横断比較の数が実測と一致する |

## スケジュール

```mermaid
gantt
    title Kotlin FP Unit 7
    dateFormat  YYYY-MM-DD
    axisFormat  %m-%d
    section Bolt 7-1
    module 新設・持ち越し・ログの決め事         :a1, 2026-11-01, 1d
    依存のスパイク・承認・converter             :a2, after a1, 1d
    Hub と store の decorator・request log      :a3, after a2, 2d
    第 12 章（構成と全文は停止 3 で確認）       :a4, after a3, 2d
    section Bolt 7-2
    対応表の裏付け・横断比較                   :b1, after a4, 1d
    第 13 章                                   :b2, after b1, 1d
    全品質ゲート・レビュー・Bolt 終了           :b3, after b2, 1d
```

## タスク

### Bolt 7-1：第 12 章「監視と関数型JSON」

- [x] **7-1.1** `zettai-step7-observability` module を新設する。Unit 6 の production code と test を独立した snapshot としてコピーする。第 12 章で変わる型（`ToDoListHub`、`EventStore<Tx>`、`ZettaiHttpApp`）を一覧し、decorator で包む点を決める。あわせて、持ち越し 7（`userAndList` に寄せる）、11（失敗応答を HTML にするかを決め、`ZettaiHttpAppTest` と計画に残す）、12（`rename` の分岐と `describeError` の `else`）を片づける
- [x] **7-1.2** ログに載せるもの、載せないものの表（本計画）をテストにする（ローカルで Red を確認）。決定的な時刻、1 行の JSON、機密の除外
- [x] **7-1.3** 新しい依存を決める（**停止 1**：新しい依存の追加と、承認済みの ADR-040 の部分的な見直しは確認必須）。上の比較表の案 A を既定として提示する。スパイクで、Kotlin 2.4.21 と整合する版、`JsonElement` の往復、特殊文字（引用符、バックスラッシュ、改行、絵文字）の escape を確かめ、ADR にする。承認が得られないときは、案 B〜D の理由を示して止まる
- [x] **7-1.4** `Converter<A, B>` と `dimap` を作り、法則（`dimap id id = id`、`dimap (f ∘ g) (h ∘ k) = dimap g h ∘ dimap f k`、往復）を property test で確かめる。壊れた入力は `Left`。法則を満たさない部分があれば、第 12 章に「厳密なプロファンクタではない」理由として書く
- [x] **7-1.5** Hub の decorator を `zettai.adapter.logging` に作る（Red → Green）。ログの出力先は `(String) -> Unit`、時刻は `() -> Instant` の引数で与える。1 行を 1 回の呼び出しで書き、並行しても行が混ざらないことをテストする。載せるものを確かめ、載せないものが載らないことをテストする。利用者名とリスト名を載せるかを確認する（**停止 2**：ログの内容の承認）
- [x] **7-1.6** `EventStore<Tx>` の decorator を `<Tx>` を持つ関数として作る。包むのは `readAggregate`、`readUser`、`readStream`、`append` で、`transaction` は包まない（読み出し、追記、失敗の記録。payload は載せない）
- [ ] **7-1.7** HTTP の request log の filter を作る。body と query は載せない。404、400、409、500 も記録する
- [ ] **7-1.8** `BoundaryTest` に、`zettai.adapter.logging` が `zettai.adapter.postgres`、`zettai.adapter.inmemory`、`zettai.web` に依存しないことを足す。domain と application が `zettai.adapter.logging` を import しないことは、既存の禁止リストが `zettai.adapter` 全体を禁じているので、追加の検査は要らない
- [ ] **7-1.9** 設計文書（アーキテクチャ設計）に、前半の判断を反映する。開発戦略の Unit 7 の行（「`logger/`、Kondor による JSON」。Kondor は既存 Kotlin 版で採った道具で、この版では採らない）を、実際の到達点に直す
- [ ] **7-1.10** 第 12 章を実装済みコードから執筆する（**停止 3**：記事の構成と全文）。第 2 見出しは既存 Kotlin 版と同じ並び。断定する表現は、対応するテスト名を示せるものだけに使う

### Bolt 7-2：第 13 章「関数型アーキテクチャの設計」

- [ ] **7-2.1** 5 つの代数構造と、各章のテストの対応表を、実装から作る。確認済みの現状：モノイドは step3 の `StateTransitionMonoidTest` だけで、step4 以降の module には持ち越されていない（表には module を書く）。ファンクタは `EitherFunctorLawTest`、モナドは `EitherMonadLawTest`、アプリカティブは `AccumulationLawsTest`。プロファンクタは step6 までに存在せず、7-1.4 で初めて作る。同じ Unit の成果物が根拠になることを、表に書く
- [ ] **7-2.2** effect の境界を、`BoundaryTest` のテスト名で示す表を作る
- [ ] **7-2.3** 採用しなかった道具を、理由つきで列挙する。確かめていないことは、確かめていないと書く
- [ ] **7-2.4** 横断比較（`docs/article/zettai/comparison/index.md`）に Kotlin FP 版を加える。現在の構造は、列が Kotlin、なでしこ3、Rust の表の連続で、冒頭は「3 対象」と書いている。既存の表は書き換えず、末尾に「Kotlin FP 版を加えた比較」の節を新設し、冒頭の対象数と追記履歴を更新する。数は実測（章数、テスト件数、本番コードの行数）から書く
- [ ] **7-2.5** 第 13 章を執筆する（**条件付き停止 4**：総括の主張が、実装で裏付けられない行が出た場合だけ、人に判断を求める）
- [ ] **7-2.6** 連載全体の品質ゲート：全 module の `clean check`（約 2 分。Testcontainers を含む）、全 13 章の記事検査、`mkdocs build --strict`、OKF、step7 の 3 回連続
- [ ] **7-2.7** 5 観点レビュー、再レビュー、ふりかえり、Bolt 終了報告、連載の完了報告書（`creating-release-report`）。ADR の承認、push、CI は人の承認を得て行う

## 作業規則（ゲート）

| 停止 | 内容 |
| :--- | :--- |
| 1 | 新しい依存（`kotlinx-serialization-json`）と ADR。確認必須 |
| 2 | ログの内容。利用者名とリスト名を載せるか |
| 3 | 第 12 章の構成と全文 |
| 条件付き 4 | 第 13 章の主張のうち、実装で裏付けられない行 |

新しい ADR（ログの decorator、JSON の依存と converter）は、Unit のクローズで人が承認します。承認の記録を残します。

## 完了の定義（DoD）

- [ ] Unit 7 の decorator のテスト、converter の性質テスト、既存の契約テストと Cucumber の 3 経路が green
- [ ] 同じ結合テストを 3 回連続で実行して green
- [ ] Unit 1〜7 の `gradlew.bat clean check --no-daemon` が green
- [ ] 第 12〜13 章のコード例が実装と一致する
- [ ] 記事コード、節構成、件数、引用、ADR 参照の検査が全 13 章で green
- [ ] `mkdocs build --strict` が green
- [ ] 第 12〜13 章が索引と nav から到達可能。横断比較に Kotlin FP 版が載る
- [ ] アーキテクチャ設計に Unit 7 の判断を反映する
- [ ] ADR が作成され、人の承認を得ている
- [ ] 受入条件の表の各セルが、テスト名と 1 対 1 で対応している
- [ ] 5 観点の開発成果物レビューで高・中重要度の残件がない（再レビューで確認）
- [ ] OKF 検査が ERROR 0
- [ ] push 後の CI が green（push は人の承認を得て行う）
- [ ] 連載の完了報告書がある

### 受入条件（検証可能な表現）

- [ ] 機密を含む入力を処理しても、ログに項目の説明、`reason`、スタックトレースが載らない
- [ ] ログの各行が、特殊文字（引用符、バックスラッシュ、改行、絵文字）を含む入力でも、改行を含まず、JSON の parser で読み戻せる 1 行である（テストでは `Json.parseToJsonElement` で判定する）
- [ ] `Converter` が恒等則、合成則、往復を満たす
- [ ] decorator を外しても、Hub の振る舞いが変わらない（任意の command 列を、decorator ありと無しの 2 つの Hub に同じ in-memory の store で流し、結果の `Either` と event の列が一致することを、property test で確かめる）
- [ ] 第 13 章の対応表の各行が、テスト名か「無い」で埋まっている

## リスク

| リスク | 対応 |
| :--- | :--- |
| ログに機密が漏れる | 載せるものを表にして、載せないものが載らないことをテストする。例外のスタックトレースと `reason` を載せない |
| JSON の自前実装が過剰になる | 新しい依存で `JsonElement` だけを使う（停止 1）。自前の parser は作らない |
| 総括の章が、実装で裏付けられない主張を書く | 対応表の各行にテスト名を要求する。裏付けが無い行は「無い」と書く（条件付き停止 4） |
| 連載全体の品質ゲートが、最後に大量の指摘を出す | 7-2.6 を最後に回さず、7-1 の終わりに全 13 章の記事検査を一度走らせる |
| 横断比較の数が古い | 実測から書き、書いた日付とコミットを残す |
| decorator の時刻・出力先が副作用で、テストが不安定になる | 時刻（`() -> Instant`）と出力先（`(String) -> Unit`）を引数で与える。標準出力には書かない |
| 並行する command のログの行が混ざる | 1 行を 1 回の呼び出しで書く。並行の command で、行が壊れないことをテストする |
| 承認済みの ADR-040 と矛盾する | 比較表で理由の違い（読み取りが要る）を示し、部分的な見直しとして、人が停止 1 で承認する |
| 全 module の `clean check` と 3 回連続、CI の時間 | 7-2.6 に約 2 分と見込みを書く。CI の待ちは、監視をバックグラウンドで行う |
| 計画の承認後に、索引の状態が古い | 承認後に、開発の索引とリリース計画の状態を更新する |

## 関連ドキュメント

- [リリース計画](release_plan-kotlin-fp.md)
- [開発戦略](development_strategy.md)
- [Unit 6 の Bolt 計画](iteration_plan-kotlin-fp-6.md)
- [Unit 6 のふりかえり](retrospective-kotlin-fp-6.md)
- [Unit 6 の Bolt 終了報告](bolt_report-kotlin-fp-6.md)
- [Unit 6 の開発成果物レビュー](../review/kotlin-fp-unit6_review_20261031.md)
- [執筆計画](../article/outline.md)

## 変更履歴

| 日付 | 内容 | 作成者 |
| :--- | :--- | :--- |
| 2026-10-31 | 初版。Unit 6 の持ち越しと開発戦略を反映し、ログの内容、依存、停止の位置を定めた | anthropic/claude-sonnet-5-5 |
| 2026-10-31 | 独立した検証（高 4 / 中 6 / 低 4）を反映。ADR-040 との関係と JSON の比較表、実在しない `listKey` の訂正、漏れていた持ち越し 2 件、`EventStore` の実際の型と decorator の置き場所、プロファンクタとモノイドの根拠、横断比較の構造、新規ファイルの扱い、受入条件の検証方法を直した | anthropic/claude-sonnet-5-5 |
| 2026-10-31 | 人（kakimomokuri）が計画を承認した。新規ファイルの扱いを含む。JSON の依存と ADR-040 の部分的な見直しは停止 1 で確認する | human:kakimomokuri |
| 2026-10-31 | 停止 2：人（kakimomokuri）がログの内容を承認した。利用者名とリスト名を識別子として載せる | human:kakimomokuri |
