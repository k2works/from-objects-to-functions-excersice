---
type: Article
title: "第 1 章 新しいアプリケーションを準備する"
description: "Zettai 連載 Flix 版の第 1 章。Flix 0.79.0 のプロジェクトを作り、純粋関数、値としての失敗、効果多相をテストファーストで導く。副作用が型の一部になる言語で、第 1 章に何が増えるのかを確かめる。"
tags: [article, zettai, flix, effect-system, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T14:00:00Z }
---

# 第 1 章 新しいアプリケーションを準備する

この連載は同じ 13 章を複数の言語で書き起こしています。Flix は **4 言語目**です。

Kotlin 版は関数型の道具を自前で組み立て、Rust 版は `Result` や `enum` を言語から受け取りました。Flix にはこれまでの対象に無かったものがあります。**副作用が型の一部になる**ことです。

Zettai の設計は、純粋な計算を核に置き、副作用を境界に寄せるという分離でした。Kotlin FP 版ではそれを設計の規律として守り、Rust 版では所有権が設計を押しました。Flix は **規律そのものを型検査に移せる**かを問います。

## サンプルアプリケーションを定義する

題材は 3 対象と同じ **Zettai** という ToDo リストアプリケーションです。Uberto Barbini の『From Objects to Functions』から借りています。

13 章をかけて、HTTP でリストを見せるところから、イベントソーシング、CQRS、永続化、バリデーション、監視までを作ります。**設計は他の対象と同じで、変わるのは実現手段だけです。**

この版で確かめることは次の 3 つです。

- 計算は、効果を持たない純粋関数に置く。純粋かどうかを**コンパイラが検査する**
- 失敗は例外ではなく値にする
- HTTP やデータベースの効果は、Java 相互運用の境界に置く（第 2 章以降）

## Zettai: イノベーティブな ToDo リストアプリケーション

Zettai は複数の ToDo リストと、その中の項目を扱います。項目には説明、任意の期限、状態があります。

最初の例は単語の得点です。文字数を返すだけの関数ですが、純粋関数の性質を確かめるには十分です。

```flix
pub def wordScore(word: String): Int32 = String.length(word)
```

同じ文字列には常に同じ整数を返し、外部状態を読み書きしません。`wordScore("Flix")` はいつでも `4` に置き換えられます。この置き換えでプログラムの意味が変わらない性質を**参照透過性**と呼びます。

他の対象では、この性質は書き手の注意とテストで守っていました。Flix では、シグネチャに効果の注釈が無い関数は**効果を持たないもの**として扱われます。

## テストに開発をガイドさせる

この連載では Red → Green → Refactor の順序を固定します。

1. 望む振る舞いをテストに書く
2. 失敗を確認する
3. 通すための最小実装を書く
4. green のまま名前と構造を整える

最初に書いたテストは次のとおりです。まだ `Foundation.wordScore` はありません。

```flix
mod TestFoundation {

    @Test
    def wordScoreReturnsTheSameValueForTheSameWord(): Unit \ Assert = {
        Assert.assertEq(expected = 4, Foundation.wordScore("Flix"));
        Assert.assertEq(expected = 4, Foundation.wordScore("Flix"))
    }

}
```

`flix test` の Red は、実行の失敗ではなく**コンパイルの失敗**として現れます。

```bash
-- Resolution Error [E2136] --------------------------- test/TestFoundation.flix

>> Undefined name 'Foundation.wordScore'.

5 |         Assert.assertEq(expected = 4, Foundation.wordScore("Flix"));
                                          ^^^^^^^^^^^^^^^^^^^^
                                          name not found
```

テストの `\ Assert` は、このテストが `Assert` という効果を使うという宣言です。テストの検査も効果で表されています。

Green にする最小実装が、先ほどの `wordScore` です。

```flix
mod Foundation {

    pub def wordScore(word: String): Int32 = String.length(word)

}
```

## プロジェクトをセットアップする

実行環境は JDK 21 と Flix 0.79.0 に固定します。Flix は JVM 上で動き、公式ドキュメントでは Java 21 以降が必要です。Flix の版は `flix.toml` で固定します。

```toml
[package]
version = "0.1.0"
flix    = "0.79.0"
```

Nix を使う場合は、devShell に入ってから `flix` を実行します。`flix` は devShell が定義するシェル関数で、初回に公式リリースの `flix.jar` を取得します。

```bash
nix develop .#flix
cd apps/flix/zettai
flix test
```

Nix を使わない場合は、`flix.jar` を自分で取得します。取得先は公式リリースの `https://github.com/flix/flix/releases/download/v0.79.0/flix.jar` です。

```bash
curl -sL -o flix.jar https://github.com/flix/flix/releases/download/v0.79.0/flix.jar
java -jar flix.jar test
```

この記事は Nix の入っていない環境で書いたため、`nix develop .#flix` は未検証です。確認できているのは、`java -jar` で同じコマンドを実行した結果です。

品質ゲートは次の 2 つです。

- `flix check`: 型と効果の検査
- `flix test`: テストの実行

Flix の `init` が生成する GitHub Actions の定義は、`flix.toml` から版を読んで `flix.jar` を取得し、この 2 つを実行します。本リポジトリの CI も同じ構成にしています。

## ユニットテストを関数型にする

純粋関数のテストには、共有する可変の fixture も mock も要りません。入力を用意して出力と比較するだけです。

### 効果が型に現れる

`wordScore` の本体に `println` を入れると何が起きるでしょうか。次のコードは**わざと壊したもの**です。

<!-- code-check: ignore わざと壊したコード -->
```flix
pub def wordScore(word: String): Int32 = {
    println("scoring");
    String.length(word)
}
```

`flix check` は次のように拒否します。

```bash
-- Type Error [E8752] ------------------------------------------ src/Broken.flix

>> Unexpected effect 'IO' in {} function.

3 |     pub def wordScore(word: String): Int32 = {
                                              ^
                                              function is inferred to be {}

4 |         println("scoring");
            ^^^^^^^^^^^^^^^^^^
            'IO' used here

Explanation: Functions without an explicit effect annotation are
inferred to be {}, meaning they may not perform effects. Since 'IO'
is an effect, it cannot be used in this function. To fix this,
either add the explicit effect annotation in the signature {IO}
or remove the use of 'IO' inside the function.
```

効果の注釈が無い関数は `{}`（効果なし）と推論され、`IO` を使うとコンパイルが通りません。Kotlin FP 版の「式本体でも時刻を読めば不純」という注意は、ここでは**型エラー**になります。

### 失敗も値にする

失敗も値にすると、同じ形でテストできます。空の名前を `Err`、利用できる名前を `Ok` にします。

テストを先に書きます。

```flix
    @Test
    def nonBlankNameReturnsFailureAsValue(): Unit \ Assert = {
        Assert.assertEq(expected = Err("名前は空にできません"), Foundation.nonBlankName(""));
        Assert.assertEq(expected = Ok("仕事"), Foundation.nonBlankName("仕事"))
    }
```

`nonBlankName` が無い間の Red は、名前の解決エラーに加えて `Eq` と `ToString` が型 `t0` に無いという型エラーでも現れます。戻り値の型が決まらないためです。

最小実装は次のとおりです。

```flix
    pub def nonBlankName(name: String): Result[String, String] =
        if (String.isEmpty(String.trim(name)))
            Err("名前は空にできません")
        else
            Ok(name)
```

`Result` は Flix の標準にあります。Kotlin 版のように `Outcome` を自作する必要はありません。第 7 章で改めて、使う判断と作る判断を記録します。

### 効果を引数に取る

最後に、渡された関数を一度呼ぶだけの関数です。Kotlin FP 版では `suspend` で表した「effect を実行できる境界」にあたります。

```flix
    @Test
    def loadGreetingTakesTheEffectOfTheGivenFunction(): Unit \ Assert =
        Assert.assertEq(expected = "こんにちは", Foundation.loadGreeting(() -> "こんにちは"))
```

```flix
    pub def loadGreeting(load: Unit -> String \ ef): String \ ef = load()
```

`\ ef` は**効果多相**です。`loadGreeting` は引数の関数が持つ効果をそのまま引き継ぎます。純粋な関数を渡せば純粋に、`IO` を持つ関数を渡せば `IO` を持つものになります。この段階では純粋な関数だけを渡していますが、第 4 章の関数型 DI で、これが依存の差し替えに使えるかを確かめます。

## まとめ

この章では、Flix 0.79.0 の最小の品質ゲートを作り、次を確認しました。

- 純粋関数は同じ入力に同じ出力を返し、参照透過である
- 効果の注釈が無い関数は `{}` と推論され、`IO` を使うとコンパイルが落ちる
- 失敗は `Result` の値で表せる。`Result` は標準にあり、自作は不要
- `\ ef` による効果多相で、渡された関数の効果を引き継げる
- Red は実行の失敗ではなく、コンパイルの失敗として現れる

つまずきとして、Windows の日本語環境では、`flix` のエラー出力に含まれる日本語が文字化けしました。ソースは UTF-8 で、テストの判定には影響しません。

次章では、純粋な核と HTTP の殻を分けたまま、Zettai の最小の縦串を通します。HTTP は Java 相互運用で JDK を呼び、**効果の保証が境界で切れる場所**を見ていきます。

## この章で書いたコード

- `apps/flix/zettai/flix.toml`
- `apps/flix/zettai/src/Foundation.flix`
- `apps/flix/zettai/test/TestFoundation.flix`

## 参照

- [Flix 公式ドキュメント](https://doc.flix.dev/)
- [Interoperability with Java](https://doc.flix.dev/interoperability.html)
- [Flix v0.79.0 リリース](https://github.com/flix/flix/releases/tag/v0.79.0)
