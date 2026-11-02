---
type: Article
title: "第 1 章 新しいアプリケーションを準備する"
description: "Zettai 連載 Scala 版の第 1 章。Scala 3 と Cats Effect の sbt プロジェクトを作り、純粋関数、値としての失敗、値としての副作用（IO）をテストファーストで導く。Flix と違い、純粋さを型検査で強制できないことも確かめる。"
tags: [article, zettai, scala, cats-effect, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T18:00:00Z }
---

# 第 1 章 新しいアプリケーションを準備する

この連載は同じ 13 章を複数の言語で書き起こしています。Scala は **5 言語目**です。

Flix は副作用を言語の効果として型に載せました。Scala にはその仕組みがありません。代わりに **副作用を `IO` という値にする**ライブラリ、Cats Effect を使います。言語が与えるのではなく、ライブラリが与える形です。

## サンプルアプリケーションを定義する

題材は他の対象と同じ **Zettai** という ToDo リストアプリケーションです。Uberto Barbini の『From Objects to Functions』から借りています。13 章をかけて、HTTP でリストを見せるところから、イベントソーシング、CQRS、永続化、バリデーション、監視までを作ります。**設計は他の対象と同じで、変わるのは実現手段だけです。**

この版で確かめることは次の 3 つです。

- 計算は、副作用を持たない純粋関数に置く。ただし Scala は**純粋さを強制しない**ので、規律とテストで守る
- 失敗は例外ではなく値にする
- 副作用は `IO` の値として組み立て、実行は境界（第 2 章以降の `IOApp` とテスト）に寄せる

## Zettai: イノベーティブな ToDo リストアプリケーション

Zettai は複数の ToDo リストと、その中の項目を扱います。項目には説明、任意の期限、状態があります。

最初の例は単語の得点です。文字数を返すだけの関数ですが、純粋関数の性質を確かめるには十分です。

```scala
  def wordScore(word: String): Int = word.length
```

同じ文字列には常に同じ整数を返し、外部状態を読み書きしません。`wordScore("Scala")` はいつでも `5` に置き換えられます。この置き換えでプログラムの意味が変わらない性質を**参照透過性**と呼びます。

## テストに開発をガイドさせる

この連載では Red → Green → Refactor の順序を固定します。

1. 望む振る舞いをテストに書く
2. 失敗を確認する
3. 通すための最小実装を書く
4. green のまま名前と構造を整える

最初に書いたテストは次のとおりです。まだ `Foundation.wordScore` はありません。

```scala
package zettai

import munit.CatsEffectSuite

class FoundationSuite extends CatsEffectSuite:

  test("同じ単語には、いつも同じ得点を返す") {
    assertEquals(Foundation.wordScore("Scala"), 5)
    assertEquals(Foundation.wordScore("Scala"), 5)
  }
```

`sbt test` の Red は、実行の失敗ではなく**コンパイルの失敗**として現れます（パスは省略しています）。

```bash
[error] -- [E006] Not Found Error: src/test/scala/zettai/FoundationSuite.scala:8:17
[error] 8 |    assertEquals(Foundation.wordScore("Scala"), 5)
[error]   |                 ^^^^^^^^^^
[error]   |                 Not found: Foundation - did you mean Function?
[error] (Test / compileIncremental) Compilation failed
```

Green にする最小実装は次のとおりです。

```scala
package zettai

object Foundation:

  def wordScore(word: String): Int = word.length
```

`sbt test` は `Passed: Total 1, Failed 0, Errors 0, Passed 1` になります。

## プロジェクトをセットアップする

実行環境は JDK 21、Scala 3.3.9、sbt 1.10.11 に固定します。Scala 3.3 系は長期サポート（LTS）の系列です。sbt の版は `project/build.properties` に書きます。

```bash
sbt.version=1.10.11
```

依存ライブラリと Scala の版は `build.sbt` に書きます。ライブラリは Cats、Cats Effect、テストの MUnit と munit-cats-effect です。

```scala
ThisBuild / scalaVersion := "3.3.9"
ThisBuild / version      := "0.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "zettai",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core"           % "2.13.0",
      "org.typelevel" %% "cats-effect"         % "3.7.1",
      "org.scalameta" %% "munit"               % "1.3.6"  % Test,
      "org.typelevel" %% "munit-cats-effect"   % "2.2.1"  % Test
    ),
    scalacOptions ++= Seq("-deprecation", "-feature", "-Werror")
  )
```

`-Werror` は、警告をエラーにします。連載を通して警告を残さないための設定です。

Nix を使う場合は、devShell に入ってから `sbt` を実行します。devShell は JDK 21、Scala 3、sbt を提供します。

```bash
nix develop .#scala
cd apps/scala/zettai
sbt test
```

Nix を使わない場合は、JDK 21 以降と sbt を自分で用意して、同じ `sbt test` を実行してください。初回は依存ライブラリの取得で時間がかかります。

`nix develop .#scala` は、Docker 上の Nix で確かめました（2026-11-02）。JDK は 21.0.9 で、`sbt test` は 3 本が通りました。確かめたのは Linux の Nix で、Windows や macOS の Nix では確かめていません。devShell を開くたびに Metals の版を問い合わせる出力が出るため、起動は少し遅くなります。

品質ゲートは `sbt test` の 1 つです。Scala ではコンパイルがテストに含まれるので、型の検査もここで走ります。

## ユニットテストを関数型にする

純粋関数のテストには、共有する可変の fixture も mock も要りません。入力を用意して出力と比較するだけです。

### 純粋さは型では強制されない

`wordScore` の本体に `println` を入れると何が起きるでしょうか。次のコードは**わざと壊したもの**です。

<!-- code-check: ignore わざと壊したコード -->
```scala
object Broken:

  def wordScore(word: String): Int =
    println("scoring")
    word.length
```

`sbt compile` は `[success]` で終わります。Scala は、この関数が副作用を持つことを拒否しません。Flix は同じ変更を型エラーにしました。

ここがこの対象の出発点です。**Scala の型は、`IO` を返さない副作用を止めません。** 純粋さは、`IO` を使うという規律とテストで守ります。

### 失敗も値にする

失敗も値にすると、同じ形でテストできます。空の名前を `Left`、利用できる名前を `Right` にします。

テストを先に書きます。

```scala
  test("空の名前は、例外ではなく値として失敗を返す") {
    assertEquals(Foundation.nonBlankName(""), Left("名前は空にできません"))
    assertEquals(Foundation.nonBlankName("仕事"), Right("仕事"))
  }
```

`nonBlankName` が無い間の Red は、`wordScore` のときと同じ名前の解決エラーです。最小実装は次のとおりです。

```scala
  def nonBlankName(name: String): Either[String, String] =
    if name.trim.isEmpty then Left("名前は空にできません")
    else Right(name)
```

`Either` は Scala の標準にあります。Kotlin 版のように `Outcome` を自作する必要はありません。第 7 章で改めて、使う判断と作る判断を記録します。

### 副作用を値にする

最後に、副作用を `IO` の値として扱います。`IO[String]` は「実行すると文字列を返す手順」で、**作っただけでは何も実行されません**。

```scala
  test("IO は作っただけでは実行されず、実行したときに初めて効果が起きる") {
    var calls = 0
    val load: IO[String] = IO { calls += 1; "こんにちは" }
    val program = Foundation.loadGreeting(load)
    assertEquals(calls, 0)
    program.map { greeting =>
      assertEquals(greeting, "こんにちは")
      assertEquals(calls, 1)
    }
  }
```

このテストは `IO` を返します。munit-cats-effect の `CatsEffectSuite` が、返された `IO` を実行して結果を判定します。`IO` を実行する場所がテストの境界です。

最小実装は次のとおりです。

```scala
  def loadGreeting(load: IO[String]): IO[String] = load
```

`assertEquals(calls, 0)` は、`loadGreeting(load)` を呼んでも `load` の中身が実行されないことを示します。実行されるのは、`CatsEffectSuite` がテストの戻り値の `IO` を走らせたときです。

テスト内の `var calls` は、実行の有無を数えるためだけに置いた可変状態です。本番のコードには置きません。

## まとめ

この章では、Scala 3.3.9 と Cats Effect 3.7.1 の最小の品質ゲートを作り、次を確認しました。

- 純粋関数は同じ入力に同じ出力を返し、参照透過である
- `println` を入れても `sbt compile` は通る。Scala の型は純粋さを強制しない
- 失敗は `Either` の値で表せる。`Either` は標準にあり、自作は不要
- `IO` は副作用を値にする。作っただけでは実行されず、テストの境界で実行される
- Red は実行の失敗ではなく、コンパイルの失敗として現れる

つまずきとして、Windows の日本語環境では、sbt の出力に含まれる日本語が文字化けしました。ソースは UTF-8 で、テストの判定には影響しません。

次章では、純粋な核と HTTP の殻を分けたまま、Zettai の最小の縦串を通します。HTTP サーバーは http4s の Ember で起動します。**`IO` を実行する境界を `IOApp` に置く**形を確かめます。

## この章で書いたコード

- `apps/scala/zettai/build.sbt`
- `apps/scala/zettai/project/build.properties`
- `apps/scala/zettai/src/main/scala/zettai/Foundation.scala`
- `apps/scala/zettai/src/test/scala/zettai/FoundationSuite.scala`

## 参照

- [Scala 3 公式ドキュメント](https://docs.scala-lang.org/scala3/book/introduction.html)
- [Cats Effect 公式ドキュメント](https://typelevel.org/cats-effect/)
- [MUnit](https://scalameta.org/munit/)
