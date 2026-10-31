---
type: Article
title: "第 13 章 関数型アーキテクチャの設計"
description: "Zettai Kotlin FP 版の最終章。13 章で積み上げた設計判断を、5 つの代数構造と対応するテスト、effect の境界、8 件の ADR、採用しなかった道具として整理する。ライブラリへ委ねるほど設計の意図は明確になるのか、という問いに、実装から言えることと、言えないことを分けて答える。"
tags: [article, zettai, kotlin, functional-programming, arrow, architecture]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T13:17:18Z }
---

# 第 13 章 関数型アーキテクチャの設計

12 章かけて Zettai を作りました。この章では、コードをほとんど書きません。

書いたものが全体として何だったのかを、実装とテストの裏付けのある範囲で整理します。裏付けの無いことは、無いと書きます。

この版には、既存の 3 版と違う問いがあります。

> ライブラリへ委ねるほど、設計の意図は明確になるのか

最後の節で、この問いに答えます。

## シンプルさを追い求めて

### 積み上がった量

13 章は、7 つの module に分けて積み上げました。各 module は、前の module のコピーから始め、その章で足したものだけを変えています。

| module | 章 | テスト件数 | 本番コードの行数 | 本番コードの `.kt` ファイル数 |
| :--- | :--- | :--- | :--- | :--- |
| `zettai-step1-foundation` | 1 | 7 | 30 | 1 |
| `zettai-step2-http-domain` | 2〜3 | 9 | 84 | 4 |
| `zettai-step3-domain-events` | 4〜5 | 17 | 218 | 8 |
| `zettai-step4-command-errors` | 6〜7 | 30 | 472 | 12 |
| `zettai-step5-cqrs-persistence` | 8〜9 | 89 | 776 | 15 |
| `zettai-step6-transaction-validation` | 10〜11 | 165 | 1253 | 19 |
| `zettai-step7-observability` | 12〜13 | 220 | 1599 | 24 |

2026-10-31 に数えました。テスト件数は Gradle の `test-results` の合計で、Cucumber の scenario を含みます。行数は、`git ls-files` で得た `src/main` の `.kt` ファイルの改行の数で、テンプレートなどの資源は含みません。既存 Kotlin 版は、最終章で「714 のテスト」と書いています。数え方が同じとは確かめていないので、並べて比べることはしません。

コードは増え続けました。素朴な実装との比較は、していません。

### 何をシンプルと呼んだか

少ないことを、シンプルと呼びませんでした。この連載で、シンプルと呼んだのは次のことです。

| シンプルと呼んだもの | 具体例 |
| :--- | :--- |
| 1 つの場所で決まっていること | transaction の境界は `inTransaction` の 1 か所（第 10 章） |
| 取り違えられないこと | 失敗は `Either`、複数の誤りは `NonEmptyList`（第 7 章、第 11 章） |
| 後から足せること | ログは Hub と store を包むだけで足せた（第 12 章）。domain と application は変えていない |
| 何を載せないかが型で決まっていること | `LogRecord` に、項目の説明の持ち場所が無い（第 12 章） |

どれも、コードの量では測れません。変更が楽で安全か、という見方です。

## システム全体の設計

### 層と依存の向き

依存の向きは、境界テストで固定しています。

```text
web ──────────▶ application ──▶ domain
adapter.* ────▶ application ──▶ domain
web / adapter.logging ──▶ logging ──▶ json
```

`web` は adapter に依存できません。`logging` と `json` は、どちらからも使える中立の場所です。第 12 章で、filter の置き場所を間違えたことから、この形になりました。

### effect の境界

副作用は、決まった場所にだけ置きました。境界は、テストで確かめています。

| 守っていること | テスト |
| :--- | :--- |
| domain は application、adapter、HTTP、coroutine の実行器、JDBC に依存しない | `domain は application と adapter と HTTP と coroutine runner に依存しない` |
| application は adapter、HTTP、coroutine の実行器、JDBC に依存しない | `application は driving adapter と driven adapter と coroutine runner に依存しない` |
| `runBlocking` は web の最外周だけ | `domain と application と adapter に runBlocking が現れない` |
| driven adapter は HTTP と `runBlocking` に依存しない | `driven adapter は driving adapter と HTTP と runBlocking に依存しない` |
| 2 つの store は互いに依存しない | `in-memory の adapter は PostgreSQL の adapter と JDBC に依存しない`、`PostgreSQL の adapter は in-memory の adapter に依存しない` |
| ログの型は中立 | `ログの記録の型は、adapter と web と HTTP と JDBC に依存しない（どちらからも使える中立の場所）` |
| domain の中に循環を作らない | `domain.errors は validation と commands に依存しない（domain の中に循環を作らない）` |

### 5 つの代数構造

連載で名前を付けた代数構造は、5 つです。それぞれを、どの章のどのテストが確かめているかを、実装から数えました。

| 構造 | 章 | 対応するテスト | 注意 |
| :--- | :--- | :--- | :--- |
| モノイド | 5 | `StateTransitionMonoidTest`（`状態変換の合成には結合律が成り立つ`、`identityTransition は左右の単位元である`） | `zettai-step3-domain-events` にある。step4 以降の module には持ち越していない |
| ファンクタ | 7、8 | `EitherFunctorLawTest`（`Either map の恒等則が成り立つ`、`Either map の合成則が成り立つ`）、`ProjectionLawsTest`（`クエリ結果の map は恒等則を満たす`、`クエリ結果の map は合成則を満たす`） | |
| モナド | 8、9 | `EitherMonadLawTest`（`左恒等則：pure(a).flatMap(f) は f(a) と同じ`、`右恒等則：m.flatMap(pure) は m と同じ`、`結合律：flatMap を続けても、中にまとめても同じ`） | `Raise` の `bind` の連鎖が `flatMap` と同じ結果になることも確かめた（`Raise の bind の連鎖は flatMap と同じ結果になる`） |
| アプリカティブ | 11 | `AccumulationLawsTest`（`結合律：どこから合成しても、成功の値も、誤りの並びも同じ`、`単位元：成功する Unit と合成しても、結果は変わらない`） | アプリカティブ則そのものを一般の形でテストしたのではなく、`zipAll` に求める性質を確かめた（第 11 章） |
| プロファンクタ | 12 | `ConverterLawsTest`（`Encoder の dimap は恒等則を満たす`、`Encoder の dimap は合成則を満たす`、`Decoder の dimap は恒等則を満たす`、`Decoder の dimap は合成則を満たす`） | この版では第 12 章で初めて作った。束ねた `Converter` には一般の `dimap` が無い |

確かめていないことがあります。既存の 3 版は、法則を壊して、何回検出するかを数えています。この版では、法則を壊した実装で何回失敗するかの率を、数えていません。法則が通ることは示しましたが、テストがどれだけ法則を見ているかの数字は、ありません。

## コードへの翻訳

### 型で表した判断

設計の判断は、型と関数の形になりました。Hub は、3 つの関数の束です。

```kotlin
data class ToDoListHub(
    val getList: ListQuery,
    val listRows: RowsQuery,
    val execute: CommandExecutor,
)
```

store は、5 つの関数の束です。

```kotlin
data class EventStore<Tx>(
    val readAggregate: AggregateEventReader,
    val readUser: UserEventReader,
    val transaction: Transaction<Tx>,
    val readStream: StreamReader<Tx>,
    val append: StreamAppender<Tx>,
)
```

どちらも、interface ではなく、関数を持つ data class です。関数を 1 つずつ包めるので、ログの decorator は、この形のおかげで足せました（第 12 章）。

### 8 件の ADR

この版で決めたことは、8 件の ADR に記録しました。

| ADR | 決めたこと | 章 |
| :--- | :--- | :--- |
| [ADR-038](../../../adr/ADR-038-arrow-core-for-typed-errors.md) | typed error に Arrow を使う範囲 | 7 |
| [ADR-039](../../../adr/ADR-039-suspend-function-ports.md) | port は `suspend` の関数型で表し、`runBlocking` は web の最外周に置く | 4、9 |
| [ADR-040](../../../adr/ADR-040-jdbc-blocking-boundary.md) | JDBC を直接使い、blocking は `Dispatchers.IO` に閉じ込め、`use` で閉じる | 9 |
| [ADR-041](../../../adr/ADR-041-testcontainers-for-integration-tests.md) | 結合テストの DB は Testcontainers で用意する | 9 |
| [ADR-042](../../../adr/ADR-042-transaction-scope-and-environment.md) | transaction の境界を command 1 つにし、環境を型引数で通す | 10 |
| [ADR-043](../../../adr/ADR-043-optimistic-locking-by-version.md) | 楽観ロックを stream ごとの版と一意制約で行う | 10 |
| [ADR-044](../../../adr/ADR-044-json-tree-for-logs-and-converters.md) | ログと converter の JSON に `JsonElement` の木だけを使う | 12 |
| [ADR-045](../../../adr/ADR-045-logging-as-decorator-with-typed-record.md) | ログは decorator で足し、載せるものを `LogRecord` の型で決める | 12 |

8 件の承認の記録は、各 ADR の `verified` にあります。

### 既存 Kotlin 版との違い

同じ Zettai を、既存 Kotlin 版は自前の抽象で、この版は Arrow などのライブラリで書きました。違いは、次のとおりです。

| 論点 | 既存 Kotlin 版 | この版 |
| :--- | :--- | :--- |
| 失敗の型 | 自前の `Outcome` | Arrow の `Either`（第 7 章、ADR-038） |
| 文脈の持ち回り | 自前の `ContextReader`、文脈は不透明な型 | 型引数 `Tx` と関数の引数（第 10 章、ADR-042） |
| 複数の誤り | 自前の `Validation` | `Either.zipOrAccumulate`（第 11 章） |
| JSON | 手書きから自前の `Converter` に発展。Kondor は入れない | `JsonElement` の木と、自前の `Encoder` / `Decoder`（第 12 章、ADR-044） |

この表は、既存 Kotlin 版の記事と横断比較に書かれたことに基づきます。既存 Kotlin 版のコードを、この版のために読み直して確かめたものではありません。

### 採用しなかった道具

採用しなかった道具を、理由とともに挙げます。「比べていない」ものは、比べていないと書きます。

| 道具 | 扱い | 理由、または比べていない旨 |
| :--- | :--- | :--- |
| Arrow の `Resource` | 採らなかった | connection の寿命が 1 つの関数に収まり、`use` と結果が同じだった（ADR-040、ADR-042） |
| `ContextReader` | 採らなかった | 型引数 `Tx` と関数の引数で足りた（ADR-042） |
| 悲観ロック、`SERIALIZABLE` | 採らなかった | まだ無い stream をロックできない。失敗を型で扱えない（ADR-042、ADR-043） |
| ORM、DB の payload のための JSON ライブラリ | 採らなかった | SQL 側で組み立てられた（ADR-040） |
| Kondor | 試していない | `JsonElement` の木で足りた（ADR-044）。Kondor との比較はしていない |
| ログの枠組み（SLF4J など） | 試していない | 出力先を引数で渡して足りた（ADR-045）。比較はしていない |
| 既製の template エンジン | 比べていない | 値を埋める、エスケープする、値の無いタグを見逃さない、の 3 つに絞った（第 11 章） |
| Arrow の optics | 検討していない | この連載では扱っていない |

## 最後の考察

### 扱わなかったこと

この設計を、そのまま実務に出すことはできません。扱わなかったことを挙げます。

| # | 扱わなかったこと | 章 | 状態 |
| :--- | :--- | :--- | :--- |
| 1 | 認証・認可 | 全章 | 扱っていない。`User` を path に含めているだけで、本人確認をしていない |
| 2 | CSRF 対策 | 11 | 扱っていない。名前を変える `POST` にトークンは無い |
| 3 | 読み取りモデルの永続化、スナップショット | 8、9 | 扱っていない。読むたびに event を畳み込む |
| 4 | 競合したときの自動の再試行 | 10 | 扱っていない。`ConcurrentModification` を返すところまで（ADR-043） |
| 5 | ログの保管期間とアクセス権 | 12 | 扱っていない。利用者名とリスト名が載るので、運用で守る必要がある（ADR-045） |
| 6 | 分散トレース、メトリクスの収集基盤 | 12 | 扱っていない |
| 7 | HTTP と PostgreSQL の組み合わせの受け入れ確認 | 11 | 確かめていない。HTTP の経路は in-memory の store で動く |
| 8 | 失敗応答（404、500 など）の形式 | 11 | プレーンテキストのまま。HTML にしたのは、名前変更のフォームだけ |
| 9 | 相関 ID（request ID） | 12 | 扱っていない。1 回の request で出る複数の行は、時刻と利用者名でしか結べない |
| 10 | 識別子の扱い | 12 | リスト名は利用者が書く内容で、個人的な情報を含みうる。path にも利用者名とリスト名が入るので、request の行にも載る。載せる判断は人が承認したが、保管と閲覧の管理は運用の課題 |
| 11 | ログの行区切りによる偽装 | 12 | 確かめていない。値に `U+2028` などが入ったときの、読む道具の側の扱い |
| 12 | 例外と rollback の記録 | 12 | decorator を通らない経路の例外は記録されない。`append` の行は transaction の中で書かれ、その後の rollback を反映しない |
| 13 | 起動する `main` | 12 | 連載には無い。組み立ては `observedApp` の関数で示し、結合して確かめた |

HTML のエスケープと JSON のエスケープは、扱いました。入力の値は、テンプレートでエスケープして埋めます（第 11 章）。ログの JSON は、木から文字列にして、手で連結しません（第 12 章）。

1 番目が、いちばん重大です。認証が無いので、Zettai は、誰でも誰のリストでも操作できるアプリケーションです。

### 問いに答える

> ライブラリへ委ねるほど、設計の意図は明確になるのか

実装から言えることを、2 つに分けます。

委ねて、意図が明確になった場所があります。

- 失敗の型を `Either` に、複数の誤りの集め方を `zipOrAccumulate` にしたところです。名前が、そのまま概念を指します。読む人が Arrow を知っていれば、実装を読まずに意図が分かります。この前提を満たさない読者には、自前の型と同じく、定義を読む必要があります。
- 委ねた先の型が法則を満たすかを、使う側から確かめられたところです。`EitherFunctorLawTest` と `EitherMonadLawTest` が、それを確かめています。これは、法則を確かめられることの根拠です。意図が明確になったことの根拠は、前の項の、名前が概念と一致していることのほうです（自前の型にも、法則のテストは書けます）。

委ねても、意図が明確にならなかった場所があります。

- 境界の置き方です。transaction をどこで切るか、ログを何に載せないかは、ライブラリを使っても、自分で決める必要がありました。決めたことを記録したのは、ADR とテストです。
- ログの枠組みや template エンジンのように、委ねる先を比べていないものがあります。比べていないので、委ねたほうがよかったかは、この連載から言えません。

要するに、こう言えます。委ねるほど意図が明確になるとは限りません。名前が概念と一致していて、読む人がその名前を知っている場合は、委ねて明確になりました。何を載せるか、どこで切るかという、設計者が決める境界は、委ねる先が無いので、型とテストで自分で表しました。

これは、1 つの題材、1 つの実装での観察です。一般論として言える根拠は、ありません。

## 連載を終えて

13 章、7 つの module、8 件の ADR、220 件のテスト（最終 module）。

この版で、最後に残る問いは、既存の 3 版と同じです。言語やライブラリが与えるほど、設計は楽になるのか。この版の実装から言えるのは、決めることが変わる、ということです。失敗の型や複数の誤りの集め方は、決めることが減りました。一方、境界の置き方、載せないものの決め方、何を比べて何を比べなかったかは、減りませんでした。

最後に、この連載で繰り返した言葉を置きます。

> 必要な強さを選ぶ。

## この章で書いたコード

この章は、実装を足していません。

- 境界のテスト: `apps/kotlin-fp/zettai/zettai-step7-observability/src/test/kotlin/zettai/smoke/BoundaryTest.kt`
- Hub と store の型: `apps/kotlin-fp/zettai/zettai-step7-observability/src/main/kotlin/zettai/application/ToDoListHub.kt`
- ファンクタとモナドの法則: `apps/kotlin-fp/zettai/zettai-step7-observability/src/test/kotlin/zettai/domain/errors/`
- アプリカティブの性質: `apps/kotlin-fp/zettai/zettai-step7-observability/src/test/kotlin/zettai/domain/validation/AccumulationLawsTest.kt`
- プロファンクタの法則: `apps/kotlin-fp/zettai/zettai-step7-observability/src/test/kotlin/zettai/json/ConverterLawsTest.kt`

`apps/kotlin-fp/zettai/` で、全 module のテストを実行します。結合テストを含むため、Docker daemon が必要です（第 9 章を参照）。

Unix / Nix shell:

```text
bash ./gradlew check
```

Windows PowerShell:

```text
.\gradlew.bat check
```

## 参照

- Uberto Barbini『From Objects to Functions』第 13 章
- [横断比較](../comparison/index.md)
- [ADR-038](../../../adr/ADR-038-arrow-core-for-typed-errors.md) から [ADR-045](../../../adr/ADR-045-logging-as-decorator-with-typed-record.md)
