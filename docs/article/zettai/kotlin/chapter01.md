---
type: Article
title: "第 1 章 新しいアプリケーションを準備する"
description: "Zettai 連載 Kotlin 版の第 1 章。題材となる ToDo リストアプリケーション Zettai を定義し、テストに開発をガイドさせるという連載の前提を置く。Nix devShell と Gradle マルチプロジェクトでプロジェクトをセットアップし、ボウリングの得点計算を題材にユニットテストを関数型にする過程を TDD で示す。"
tags: [article, zettai, kotlin, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-26T13:17:46Z }
---

# 第 1 章 新しいアプリケーションを準備する

この連載では、ToDo リストアプリケーション **Zettai** を一から作ります。作りながら、オブジェクト指向で書いていた設計を関数型へ移していきます。

最初の章では、コードをほとんど書きません。代わりに、これから 13 章かけて何を作るのかを決め、どう作るのかの前提を置き、手元で動かせる環境を用意します。最後に、関数型のユニットテストがオブジェクト指向のそれと何が違うのかを、小さな例で確かめます。

## サンプルアプリケーションを定義する

技術書のサンプルアプリケーションには、よくある失敗があります。題材が小さすぎて、実務で出会う問題が現れないことです。電卓や FizzBuzz は、関数の書き方を説明するには足りますが、「状態をどこに置くか」「外部システムとどう向き合うか」という設計上の判断が出てきません。

かといって題材が大きすぎると、本題に入る前に読者が力尽きます。

そこで必要なのは、次の条件を満たす題材です。

- **状態を持つ。** 何かが作られ、変わり、保存される
- **外部システムと話す。** HTTP でリクエストを受け、データベースに書く
- **業務ルールがある。** 「この条件では許されない」という判断が要る
- **それでいて、ルールを説明するのに 1 段落で済む**

最後の条件が効きます。題材の説明に紙面を使うと、設計の説明が薄くなります。

## Zettai: イノベーティブな ToDo リストアプリケーション

条件を満たす題材として、ToDo リストを選びます。名前は **Zettai**（絶対）です。

ToDo リストは、一見すると平凡です。しかし上の 4 条件をすべて満たします。リストと項目という状態があり、HTTP で操作され、永続化され、「同じ名前のリストは作れない」といったルールがあります。そして説明は 1 行で済みます。

Zettai がやることは次のとおりです。

- 利用者は複数の ToDo リストを持つ
- それぞれのリストは名前を持ち、複数の項目を持つ
- 項目は説明と、任意の期限を持つ
- 項目には状態がある（未着手・進行中・完了・ブロック中）

この章では、まだこれを実装しません。13 章かけて、この小さな仕様を関数型で組み上げていきます。

平凡な題材を選ぶことには、もう 1 つ利点があります。**仕様が自明なので、読者が設計だけに集中できます。** 「この業務ルールは何だったか」を思い出す必要がありません。

## テストに開発をガイドさせる

この連載の前提を 1 つ置きます。**テストを先に書きます。**

「テストを書く」ではなく「先に書く」ことが重要です。順序が変わると、テストの役割が変わるからです。

| 書く順序 | テストの役割 |
| :--- | :--- |
| 実装のあと | 書いたコードが壊れていないことの確認 |
| 実装の前 | **これから書くコードが満たすべき条件の宣言** |

後から書くテストは、すでにある実装をなぞります。実装が持っている前提を、テストも引き継いでしまいます。先に書くテストは、実装がまだ無いので、実装の都合を知りません。知っているのは「何ができてほしいか」だけです。

だから、先に書くテストは設計を導きます。テストから見て使いにくいインターフェースは、他のコードから見ても使いにくいからです。

進め方は Red-Green-Refactor の 3 ステップです。

1. **Red**: 失敗するテストを書く。**失敗することを目で確かめる**
2. **Green**: テストを通す最小限の実装を書く
3. **Refactor**: テストを green に保ったまま、設計を改善する

1 番目の「失敗を目で確かめる」を飛ばさないでください。テストが最初から通っていたら、そのテストは何も確かめていません。

この連載では、この順序を 13 章すべてで守ります。記事に載せるコードは、すべてこの順序で書かれ、実際に動いたものです。

## プロジェクトをセットアップする

環境を用意します。読者が手元で同じものを動かせることを最優先にします。

### 実行環境

開発環境は [Nix](https://nixos.org/) の devShell で定義します。リポジトリのルートで次を実行すると、JDK・Kotlin・Gradle が揃った環境に入れます。

```bash
nix develop .#kotlin
```

devShell の定義は `ops/nix/environments/kotlin/shell.nix` です。

```nix
{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    kotlin
    jdk21
    gradle
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    echo "Kotlin development environment activated"
    echo "  - JDK: $(javac -version 2>&1)"
    echo "  - Kotlin: $(kotlinc -version 2>&1)"
    echo "  - Gradle: $(gradle -version | grep Gradle)"
  '';
}
```

共通の `baseShell` を継承して、Kotlin 関連のパッケージを足しているだけです。

Nix を使わない場合は、JDK 21 を用意してください。Gradle は Wrapper が入っているので別途インストールする必要はありません。

### ビルド構成

ビルドは Gradle のマルチプロジェクトにします。章が進むごとにモジュールが増えていく構成です。

```text
apps/kotlin/zettai/
├── settings.gradle.kts          # モジュールを include
├── build.gradle.kts             # 全モジュール共通の設定
├── gradle/
│   └── libs.versions.toml       # 依存バージョンの集中管理
└── zettai-step1-http/           # 最初のモジュール
    └── src/
        ├── main/kotlin/zettai/
        └── test/kotlin/zettai/
```

依存ライブラリのバージョンは `gradle/libs.versions.toml` の 1 箇所にまとめます。

```toml
[versions]
kotlin = "2.2.0"
junit = "5.12.2"
strikt = "0.35.1"

[libraries]
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
strikt-core = { module = "io.strikt:strikt-core", version.ref = "strikt" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

モジュールが増えたときにバージョンがずれないようにするためです。`build.gradle.kts` にバージョンの文字列を直接書きません。

<details>
<summary>ルートの build.gradle.kts</summary>

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    dependencies {
        "testImplementation"(rootProject.libs.junit.jupiter)
        "testImplementation"(rootProject.libs.strikt.core)
        "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
        }
    }
}
```

</details>

テストには JUnit 5 と [Strikt](https://strikt.io/) を使います。Strikt は Kotlin 向けのアサーションライブラリで、`expectThat(x).isEqualTo(y)` のように書けます。

ビルドとテストは 1 つのコマンドにまとめます。

```bash
cd apps/kotlin/zettai
./gradlew check
```

### バージョンについての注意

原著『From Objects to Functions』は Kotlin 1.8.20 と JDK 11 を使っていますが、本連載は **Kotlin 2.2 / JDK 21** を使います。2026 年の読者が手元で書くコードに合わせるためです。原著と挙動や API が異なる箇所は、その章で注記します。

判断の経緯は [ADR-001](../../../adr/ADR-001-kotlin-toolchain.md) に記録しています。

## ユニットテストを関数型にする

環境ができたので、テストを 1 本書きます。題材は Zettai ではなく、ボウリングの得点計算にします。仕様が閉じていて、関数型とオブジェクト指向の違いだけを見られるからです。

### オブジェクト指向でよくある形

ボウリングの得点計算をオブジェクト指向で書くと、だいたいこうなります。

```kotlin
val game = BowlingGame()
game.roll(5)
game.roll(5)
game.roll(3)
// ... 17 回続く
game.score()   // 16
```

`BowlingGame` は内部に投球の履歴を持ち、`roll` を呼ぶたびに状態が変わります。

```kotlin
class BowlingGame {
    private val rolls = mutableListOf<Int>()

    fun roll(pins: Int) {
        rolls.add(pins)
    }

    fun score(): Int = scoreOf(rolls)
}
```

テストはこうなります。

```kotlin
class BowlingGameOOTest {
    private val game = BowlingGame()

    private fun rollMany(times: Int, pins: Int) {
        repeat(times) { game.roll(pins) }
    }

    @Test
    fun `スペアは次の 1 投を加算する`() {
        game.roll(5)
        game.roll(5)
        game.roll(3)
        rollMany(17, 0)

        expectThat(game.score()).isEqualTo(16)
    }
}
```

このテストには、確かめたいこと以外のものが混ざっています。

- `game` というフィールドがあり、**テストメソッドの間で共有されうる**。JUnit はテストごとにインスタンスを作り直しますが、それは JUnit の仕様を知っていて初めて安全だと分かることです
- `rollMany` は戻り値を持たず、`game` を書き換えます。**何を渡すと何が返るか**がシグネチャから読み取れません
- 「5 と 5 と 3 を投げた」という入力が、4 行に散らばっています

### 関数型で書く

同じことを、入力から出力への関数として書きます。

まずテストです。実装はまだありません。

```kotlin
class BowlingGameTest {

    @Test
    fun `スペアは次の 1 投を加算する`() {
        expectThat(scoreOf(listOf(5, 5, 3) + rolls(0, times = 17))).isEqualTo(16)
    }

    private fun rolls(pins: Int, times: Int): List<Int> = List(times) { pins }
}
```

違いは見た目より大きいです。

- **フィールドがありません。** テストの間で共有される状態が無いので、実行順序を気にする必要がありません
- `rolls` は `List<Int>` を返すだけの関数です。何も書き換えません
- 「どの投球をしたか」が 1 つの式にまとまっています。入力が一目で分かります

この状態で実行します。

```text
e: BowlingGameTest.kt:11:20 Unresolved reference 'scoreOf'.
```

**Red です。** `scoreOf` がまだ無いので、コンパイルも通りません。これでいいのです。先にテストを書くとは、こういうことです。

テストを増やします。仕様を宣言していきます。

```kotlin
@Test
fun `すべて外したら 0 点`() {
    expectThat(scoreOf(rolls(0, times = 20))).isEqualTo(0)
}

@Test
fun `毎回 1 本なら 20 点`() {
    expectThat(scoreOf(rolls(1, times = 20))).isEqualTo(20)
}

@Test
fun `ストライクは次の 2 投を加算する`() {
    expectThat(scoreOf(listOf(10, 3, 4) + rolls(0, times = 16))).isEqualTo(24)
}

@Test
fun `パーフェクトゲームは 300 点`() {
    expectThat(scoreOf(rolls(10, times = 12))).isEqualTo(300)
}
```

テスト名は日本語で書いています。Kotlin はバッククォートで囲めば関数名に空白や日本語を使えます。テスト名は仕様の文なので、読みやすさを優先します。

### 実装する

Green にします。`scoreOf` は投球の並びを受け取り、点数を返す関数です。

```kotlin
fun scoreOf(rolls: List<Int>): Int = scoreFrames(rolls, remainingFrames = FRAMES_PER_GAME)

private fun scoreFrames(rolls: List<Int>, remainingFrames: Int): Int =
    when {
        remainingFrames == 0 -> 0
        rolls.isStrike() -> rolls.frameScore(rollsUsed = 1, bonusRolls = 2) + rolls.nextFrames(1, remainingFrames)
        rolls.isSpare() -> rolls.frameScore(rollsUsed = 2, bonusRolls = 1) + rolls.nextFrames(2, remainingFrames)
        else -> rolls.frameScore(rollsUsed = 2, bonusRolls = 0) + rolls.nextFrames(2, remainingFrames)
    }
```

ここに 3 つのことが現れています。

**1. ループの代わりに再帰を使っています。** 「10 フレーム分を数える」を、「1 フレームを数えて、残りのフレームを数える」に分解しました。可変のカウンタが要らなくなります。

**2. 投球の並びを消費していきます。** `nextFrames` は `drop` したリストを次に渡します。元のリストは書き換えません。

```kotlin
private fun List<Int>.nextFrames(rollsUsed: Int, remainingFrames: Int): Int =
    scoreFrames(drop(rollsUsed), remainingFrames - 1)
```

**3. 判定が小さな関数に分かれています。**

```kotlin
private fun List<Int>.isStrike(): Boolean = first() == ALL_PINS

private fun List<Int>.isSpare(): Boolean = take(2).sum() == ALL_PINS

/** そのフレームで使う投球数と、ボーナスとして加算する投球数の合計を足す。 */
private fun List<Int>.frameScore(rollsUsed: Int, bonusRolls: Int): Int = take(rollsUsed + bonusRolls).sum()
```

実行すると green になります。

```text
BowlingGameTest > すべて外したら 0 点() PASSED
BowlingGameTest > 毎回 1 本なら 20 点() PASSED
BowlingGameTest > スペアは次の 1 投を加算する() PASSED
BowlingGameTest > ストライクは次の 2 投を加算する() PASSED
BowlingGameTest > パーフェクトゲームは 300 点() PASSED
```

### リファクタリングで気づくこと

最初に書いた実装では、ストライクとスペアの加点が別々の関数になっていました。

```kotlin
private fun List<Int>.strikeScore(): Int = take(3).sum()
private fun List<Int>.spareScore(): Int = take(3).sum()
```

中身が同じです。名前だけが違います。

これは意味が同じだから重複しているのではありません。**たまたま結果が一致しているだけ**です。ストライクは「1 投 + ボーナス 2 投」、スペアは「2 投 + ボーナス 1 投」で、合計がどちらも 3 投になっています。

そこで、その構造を関数のシグネチャに出しました。

```kotlin
private fun List<Int>.frameScore(rollsUsed: Int, bonusRolls: Int): Int = take(rollsUsed + bonusRolls).sum()
```

呼び出し側を見ると、ストライクとスペアの違いが引数の数字として読めるようになります。

```kotlin
rolls.isStrike() -> rolls.frameScore(rollsUsed = 1, bonusRolls = 2) + ...
rolls.isSpare()  -> rolls.frameScore(rollsUsed = 2, bonusRolls = 1) + ...
else             -> rolls.frameScore(rollsUsed = 2, bonusRolls = 0) + ...
```

リファクタリングの間、テストは一度も変えていません。テストが変わらないまま実装を作り替えられるのは、テストが**実装の手順ではなく、外から見た振る舞い**を確かめているからです。

これが「テストを先に書く」ことの見返りです。

## まとめ

この章でやったことを振り返ります。

- **題材を決めました。** ToDo リストアプリケーション Zettai。状態・外部システム・業務ルールを持ちながら、仕様を 1 段落で説明できる大きさです
- **前提を置きました。** テストを先に書き、Red を目で確かめ、最小限の実装で Green にし、テストを保ったままリファクタリングします
- **環境を用意しました。** Nix devShell（JDK 21 / Kotlin / Gradle）と、Gradle マルチプロジェクト。`./gradlew check` の 1 コマンドで確かめられます
- **関数型のユニットテストを書きました。** テストの中に可変のフィールドを持たず、入力から出力への関数として書くと、テストは実行順序から自由になり、実装の作り替えに耐えるようになります

最後の点が、これから 12 章で繰り返し現れます。関数型で書くというのは、突き詰めると **状態をどこに置くかを設計する** ことです。この章では「テストの中に状態を置かない」ところから始めました。

次の章では、いよいよ Zettai の実装に入ります。HTTP のリクエストを受けて ToDo リストを表示する、最小の縦串を通します。

---

## この章で書いたコード

- 実装（関数型）: `apps/kotlin/zettai/zettai-step1-http/src/main/kotlin/zettai/BowlingGame.kt`
- 実装（オブジェクト指向・比較用）: `apps/kotlin/zettai/zettai-step1-http/src/main/kotlin/zettai/BowlingGameOO.kt`
- テスト: `apps/kotlin/zettai/zettai-step1-http/src/test/kotlin/zettai/BowlingGameTest.kt`、`BowlingGameOOTest.kt`
- 環境: `ops/nix/environments/kotlin/shell.nix`、`apps/kotlin/zettai/`

## 参照

- Uberto Barbini『From Objects to Functions』第 1 章。題材の設定とボウリングの例は本書に拠っています。本連載のコードはすべて書き起こした自作実装です
- コンパニオンコード（`references/fotf/bowlingkata`）は参照のみに使い、転載していません
