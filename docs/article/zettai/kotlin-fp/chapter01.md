---
type: Article
title: "第 1 章 新しいアプリケーションを準備する"
description: "Zettai Kotlin FP 版の第 1 章。JDK 25 の実行環境を用意し、純粋関数、参照透過性、式本体関数、val を Kotest の TDD から導く。"
tags: [article, zettai, kotlin, arrow, functional-programming]
status: draft
generated: { by: openai-codex/gpt-5, at: 2026-10-30T00:00:00Z }
---

# 第 1 章 新しいアプリケーションを準備する

この連載では ToDo リストアプリケーション **Zettai** を一から作りながら、functional core と effectful shell の境界を探ります。既存 Kotlin 版が関数型の道具を自前で組み立てるのに対し、この版は Kotlin の標準機能、Arrow、kotlinx.coroutines を使います。

## サンプルアプリケーションを定義する

題材には、状態、外部システム、業務ルールがあり、それでいて短く説明できる大きさが必要です。小さすぎれば設計上の判断が現れず、大きすぎれば関数型設計へ入る前に本題が埋もれます。

Zettai では、変更しやすさを次の分離で確かめます。

- 計算は、入力から出力を返す純粋関数に置く
- 失敗は例外ではなく値にする
- HTTP、時刻、データベースなどの effect は境界に置く
- 境界の内側は、同じ入力なら同じ出力になるよう保つ

## Zettai: イノベーティブな ToDo リストアプリケーション

Zettai は複数の ToDo リストと、その中の項目を扱います。項目には説明、任意の期限、状態があります。後の章では HTTP と PostgreSQL を加えますが、ドメインの計算までそれらへ依存させません。

最初の例は単語の得点です。文字数を返すだけの関数ですが、関数型コードの重要な性質をすべて観察できます。

```kotlin
fun wordScore(word: String): Int = word.length
```

同じ文字列には常に同じ整数を返し、外部状態を読み書きしません。`wordScore("Kotlin")` はいつでも `6` へ置き換えられます。この置き換えでプログラムの意味が変わらない性質を **参照透過性** と呼びます。

## テストに開発をガイドさせる

この連載では Red → Green → Refactor の順序を固定します。

1. 望む振る舞いをテストに書く
2. 未実装による失敗を確認する
3. 通すための最小実装を書く
4. green のまま名前と構造を整える

Unit 1 でも、プロダクションコードを置く前に次のテストを実行し、`wordScore` が未解決でコンパイルに失敗する Red を確認しました。

```kotlin
test("純粋関数は同じ入力に同じ出力を返す") {
    wordScore("Kotlin") shouldBe 6
    wordScore("Kotlin") shouldBe 6
}
```

テストの 2 行は値として同じです。現在時刻や乱数へ隠れて依存する関数なら、この単純さは失われます。

## プロジェクトをセットアップする

実行環境は JDK 25 に固定します。Gradle 自体を JDK 25 で実行できるのは Gradle 9.1.0 以降なので、Wrapper は 9.7.0、Kotlin は 2.4.21 を採用しました。ローカルの JVM toolchain と CI 設定も 25 です。

```bash
nix develop .#kotlin-fp
cd apps/kotlin-fp/zettai
bash ./gradlew check
```

Nix を使わない場合は `java -version` が 25 を示すこと、`docker version` で daemon に接続できることを先に確認してください。初回実行では Gradle の依存関係と PostgreSQL image を取得するため、ネットワーク接続が必要です。Windows では `gradlew.bat check` を使います。

依存バージョンは `gradle/libs.versions.toml` に集めます。Unit 1 では Arrow Core / Fx、kotlinx.coroutines、Kotest、Cucumber、Testcontainers PostgreSQL、http4k の共存を実際の `check` で確認しました。

品質ゲートには次を含めています。

- Kotest による純粋関数、`Either`、`suspend` 関数のテスト
- 同じ Gherkin scenario を domain / HTTP の 2 経路で動かす Cucumber テスト
- 動的な接続情報だけで PostgreSQL へ接続する Testcontainers 結合テスト
- 同期 http4k handler が effectful boundary を呼ぶ位置のスパイク

Testcontainers は固定ポートや GitHub Actions の PostgreSQL service を使いません。container が公開する JDBC URL、ユーザー、パスワードをテストへ注入します。

## ユニットテストを関数型にする

純粋関数のテストには、共有する可変 fixture も mock も要りません。入力を用意して出力と比較するだけです。

失敗も値にすると同じ形でテストできます。Arrow の `Either` を使い、空の名前を `Left`、利用可能な名前を `Right` にします。

```kotlin
fun nonBlankName(name: String): Either<String, String> =
    if (name.isBlank()) "名前は空にできません".left() else name.right()
```

```kotlin
test("Either は失敗を値として返す") {
    nonBlankName("") shouldBe Either.Left("名前は空にできません")
    nonBlankName("仕事") shouldBe Either.Right("仕事")
}
```

`Either` を使ったから関数型になるわけではありません。重要なのは、関数の結果だけで成功と失敗を推論できることです。例外や共有状態という見えない経路を増やさないために使います。

`suspend` も同様です。非同期にするためではなく、effect を実行できる境界を型に表します。この段階では渡された関数を一度呼ぶだけです。

```kotlin
suspend fun loadGreeting(load: suspend () -> String): String = load()
```

テストは `runTest` の管理下で決定的に実行します。http4k の `HttpHandler` は同期 API なので、`runBlocking` は handler の境界 1 箇所に閉じ込めました。内側の関数からはブロッキングの都合が見えません。

`val` と式本体関数も目的ではなく手掛かりです。再代入を避け、関数全体を 1 つの式として読めるようにすると、入力と出力の対応が見えやすくなります。ただし式本体でも時刻を読めば不純です。純粋さは構文ではなく振る舞いで決まります。

## まとめ

この章では、JDK 25 のビルド基盤と最小の品質ゲートを作り、次を確認しました。

- 純粋関数は同じ入力に同じ出力を返し、参照透過である
- 式本体関数と `val` は、値の変換を読みやすくする
- Arrow `Either` により、失敗をテスト可能な値として扱える
- `suspend` の effect は境界へ押し出し、同期 HTTP との接続位置を 1 箇所にする
- Cucumber と Testcontainers も `./gradlew check` の一部として実行できる

次章では、純粋な core と HTTP shell を分けたまま、Zettai の最小の縦串を通します。

## この章で書いたコード

- `apps/kotlin-fp/zettai/zettai-step1-foundation/src/main/kotlin/zettai/Foundation.kt`
- `apps/kotlin-fp/zettai/zettai-step1-foundation/src/test/kotlin/zettai/FunctionalCoreTest.kt`
- `apps/kotlin-fp/zettai/zettai-step1-foundation/src/test/kotlin/zettai/EffectBoundaryTest.kt`
- `apps/kotlin-fp/zettai/zettai-step1-foundation/src/test/kotlin/zettai/PostgreSqlContainerTest.kt`
- `apps/kotlin-fp/zettai/zettai-step1-foundation/src/test/kotlin/zettai/acceptance/FoundationSteps.kt`
- `apps/kotlin-fp/zettai/zettai-step1-foundation/src/test/resources/features/foundation.feature`

## 参照

- [Kotlin release process](https://kotlinlang.org/docs/releases.html)
- [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html)
- [Arrow](https://arrow-kt.io/)
- [Cucumber-JVM](https://cucumber.io/docs/installation/java/)
- [Testcontainers for Java](https://java.testcontainers.org/)
