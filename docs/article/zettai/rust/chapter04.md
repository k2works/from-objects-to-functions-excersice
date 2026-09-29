---
type: Article
title: "第 4 章 ドメインとアダプタのモデリング"
description: "Zettai 連載 Rust 版の第 4 章。依存を関数値として渡す形を 3 案書いて比べ、第 5 章の畳み込みまで試してから決める。impl Fn は書くたびに別の型になるので並びに入れられない。2 つめの段階を切る手順も決める。"
tags: [article, zettai, rust, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-29T03:10:44Z }
---

# 第 4 章 ドメインとアダプタのモデリング

第 3 章でドメインとインフラを分けました。**ドメインは HTTP を知りません。**

けれど、ドメインはまだ**データの出どころを知っています**。`fetch_list` の中に `"book"` と `"shopping"` が埋め込まれています。

この章で、出どころを外から渡します。オブジェクト指向なら interface を作るところです。**関数型では、関数そのものを渡します。**

## ToDo リストを変更するための新しいストーリーを始める

読むだけでは足りません。**足せるようにします。**

シナリオを業務の言葉で書きます。第 3 章で作った入口（トレイト）に操作を 1 つ足すだけです。

```rust
    /// リストに項目を足す（第 4 章）。
    ///
    /// **業務の言葉のまま。** どこに足すか、誰が持つかは経路が決める。
    fn add_item(&self, user: &str, list_name: &str, description: &str);
```

シナリオ本体です。

```rust
fn uberto_adds_an_item_to_his_book_list(actions: &dyn ZettaiActions) {
    let before = actions.items_of("uberto", "book").expect("book はある");

    actions.add_item("uberto", "book", "review chapter");

    let after = actions.items_of("uberto", "book").expect("book はある");
    assert_eq!(
        after.len(),
        before.len() + 1,
        "{}: 足した分だけ増える",
        actions.route()
    );
```

コンパイルが止まります。

```text
error[E0046]: not all trait items implemented, missing: `add_item`
```

**2 経路とも止まりました。** どちらかを書き忘れることがありません。トレイトにしたことがここで効いています。

## 関数型の「依存性の注入」を使う

ハブを作ります。ハブはユースケースの入口で、**依存を関数値として受け取ります。**

問題は、Rust では関数値の渡し方が 3 通りあることです。

| 案 | 形 |
| :--- | :--- |
| A | `impl Fn` を引数・型引数で受ける（静的） |
| B | `Box<dyn Fn>` を持つ（動的） |
| C | 自前のトレイトとジェネリック型パラメータ |

### 第 5 章まで書いてから決めた

**第 4 章だけを見ると、3 案とも通ります。** どれでも依存は差し替えられます。

そこで 3 案それぞれで、**第 5 章でやること**（状態から状態への関数を合成して畳み込む）まで書きました。

| | 案 A | 案 B | 案 C |
| :--- | :--- | :--- | :--- |
| 依存を差し替える（第 4 章） | できる | できる | できる |
| 2 つの変換を合成する | できる | できる | できる |
| **長さが実行時に決まる列を畳み込む（第 5 章）** | **できない** | **できる** | **できない** |

**2 案が第 5 章で落ちました。** 第 4 章だけを見て決めていたら、次の章で書き直しています。

### つまずき 1: 同じ文字列を 2 回並べて「違う型だ」と言われる

案 A が落ちる理由です。`impl Fn` を返す関数を 2 つ書いて、結果を同じ `Vec` に入れようとしました。

<!-- code-check: ignore つまずきの説明のために、わざと落ちるコードを書いている -->

```rust
fn add(w: &'static str) -> impl Fn(Vec<String>) -> Vec<String> { ... }
fn clear() -> impl Fn(Vec<String>) -> Vec<String> { ... }

let fs = vec![add("x"), clear()];
```

```text
error[E0308]: mismatched types
   = note: expected opaque type `impl Fn(Vec<String>) -> Vec<String>`
              found opaque type `impl Fn(Vec<String>) -> Vec<String>`
   = note: distinct uses of `impl Trait` result in different opaque types
```

**`expected` と `found` が同じ文字列です。** それでも違う型です。`impl Trait` は「この形の何か」という書き方で、**書いた場所ごとに別の型**になります。

案 C も同じところで落ちます。合成を型で表すと（`Composed<F, G>`）、`fold` の 1 周目で `Identity` が `Composed<Identity, Add>` になり、2 周目で型が合いません。

**案 B だけが、合成しても型が変わりません。**

### 決めたこと: 使い分ける

統一しませんでした（[ADR-030](../../../adr/ADR-030-functional-di-shape.md)）。

| その関数値は、他の関数値と同じ入れ物に入るか | 形 |
| :--- | :--- |
| 入らない（配線のときに 1 つ決まるだけ） | **`impl Fn`** |
| 入る（`Vec` に並べる、`fold` で畳む） | **`Box<dyn Fn>`** |

**形が 2 つになる代償は引き受けました。** 差し替えるだけの依存に動的ディスパッチを払う理由が無いからです。

なでしこ3 版には、この判断がありません。型が無いので、関数値は辞書に詰めるだけです。Kotlin 版にもありません。関数型は 1 種類です。**選択肢が多いことが、判断を要求します。**

### ハブ

```rust
pub struct ToDoListHub<F, S> {
    fetch: F,
    save: S,
}
```

```rust
    /// リストに項目を足す。**足した後のリストを返す。**
    ///
    /// 受け取ったリストを書き換えるのではなく、**足した新しいリストを作る**。
    /// もとのリストは変わらないので、第 5 章で「イベントを適用する関数」に
    /// そのまま化ける。
    pub fn add_item(&self, user: &User, list_name: &ListName, item: ToDoItem) -> Option<ToDoList> {
        let list = (self.fetch)(user, list_name)?;
        let updated = ToDoList {
            list_name: list.list_name,
            items: [list.items, vec![item]].concat(),
        };
        (self.save)(user, &updated);
        Some(updated)
    }
```

**ドメインのクレートは、依存を 1 つも持たないままです。** 保存先は `zettai-step2-http` にありますが、ハブはそれを知りません。

テストは、渡すものを変えるだけで書けます。

```rust
    #[test]
    fn the_hub_uses_the_function_it_was_given() {
        // 埋め込みのデータではなく、**ここで渡したものが返る**。
        let hub = ToDoListHub::new(
            |_u, name| {
                Some(ToDoList {
                    list_name: name.clone(),
                    items: vec![ToDoItem::new("渡したほう")],
                })
            },
            |_u, _l| {},
        );
```

### つまずき 2: `Fn` は中身を書き換えられない

保存先は `&self` のまま中身を書き換えます。`FnMut` にすれば素直ですが、**`FnMut` は並びに入れられません。** 第 5 章で揃える形を崩したくないので、`Mutex` で内側を可変にしました。

```rust
/// 利用者ごとのリストを持つ。
///
/// `Mutex` を使うのは、テストが並列に走るためではなく、
/// **`&self` のまま中身を書き換えるため**。ハブに渡す `save` は `Fn` で、
/// `FnMut` ではない（並びに入れられる形に揃えてある）。
#[derive(Default)]
pub struct InMemoryLists {
    lists: Mutex<HashMap<(String, String), ToDoList>>,
}
```

**所有権が「どちらを崩すか」を選ばせています。** 押された先は、第 5 章と形が揃うほうでした。

## 関数型コードをデバックする

関数値の中で落ちたとき、どこで落ちたと表示されるか。**確かめました。**

案 A（静的）で落とすと、こう出ます。

```text
   2: e_closure_backtrace::main::{closure#1}
             at ./failures/e_closure_backtrace.rs:5:34
   3: e_closure_backtrace::run::<e_closure_backtrace::main::{closure#1}>
             at ./failures/e_closure_backtrace.rs:2:41
```

案 B（`Box<dyn Fn>`）だとこうです。

```text
   2: f_boxed_backtrace::main::{closure#0}
             at ./failures/f_boxed_backtrace.rs:4:68
   3: f_boxed_backtrace::run
             at ./failures/f_boxed_backtrace.rs:2:41
```

**クロージャには名前がありません。** どちらも `{closure#0}` `{closure#1}` と番号で出ます。

違いは 3 行目です。案 A は `run::<...{closure#1}>` と、**呼ばれた側にどのクロージャが渡されたかが出ます**。案 B は `run` だけです。

ただし、**実用上の差は小さい**とも言えます。落ちた行（2 行目）はどちらもファイルと行で出ていて、そこを見ればどのクロージャかは分かります。「静的ディスパッチはデバッグしやすい」と書こうとしましたが、**測ってみると言い過ぎでした。**

## 関数型ドメインモデリング

配線は 1 か所です。

```rust
    // **配線はここだけ。** 保存先を作り、ハブに関数値として渡す。
    let store = InMemoryLists::seeded();
    let hub = ToDoListHub::new(
        |user, name| store.fetch(user, name),
        |user, list| store.save(user, list),
    );
```

矢印が 1 本増えました。

```text
リクエスト → ハブ → （渡された関数値）→ 保存先
                ↓
              描く → 応答
```

**ハブは「渡された関数値」しか呼びません。** 矢印の先が何であるかを知らないので、テストでは別の関数値に差し替わります。

### 2 つめの段階を切った

この章でコードの形が変わったので、段階を切りました（[ADR-029](../../../adr/ADR-029-cutting-a-step.md)）。`zettai-step1-*` は第 3 章の姿のまま残り、この章からは `zettai-step2-*` を読みます。

切るときに 1 つ踏みました。**実行ファイルの名前が衝突します。**

```text
The bin target `zettai` in package `zettai-step2-http` has the same output filename
as the bin target `zettai` in package `zettai-step1-http`.
This may become a hard error in the future
```

`[[bin]] name` を段階ごとに変えて避けました。**ファイル名は変えていません。** 第 2 章が `src/bin/zettai.rs` を指しているためです。

```toml
[[bin]]
name = "zettai-step2"
path = "src/bin/zettai.rs"
```

### 測ってから決めた（そして測り直した）

段階を増やすと検査が遅くなります。**最初に出した数字は間違っていました。**

「初回 24.77 秒で上限 20 秒を超える」と見ていたのですが、それは**依存クレートを初めてビルドした値**でした。キャッシュを揃えて測り直すと、こうです。

| 構成 | 初回（新しい段階だけ clean） | 定常 |
| :--- | :--- | :--- |
| 両段階が `bin` を持つ（同名） | 18.21 / 12.28 秒 | 5.32 / 5.11 秒 |
| `bin` は最新段階だけ | 14.90 / 12.33 秒 | 4.97 / 5.25 秒 |
| **名前を分ける（採用）** | 14.03 / 13.57 秒 | 6.38 / 5.36 秒 |

**差は誤差でした。** 測り直したことで結論が変わっています。最初の数字のままなら「`bin` を減らす」を選び、第 2 章の記述を 2 箇所直すことになっていました。

## まとめ

- **依存を関数値で渡しました。** ドメインは出どころを知りません
- **形を 3 つ書いて、第 5 章まで試してから決めました。** 第 4 章だけを見ると 3 案とも通ります
- **`impl Fn` は書くたびに別の型です。** エラーは `expected` と `found` に同じ文字列を並べます。並びに入れるものは `Box<dyn Fn>` に揃えます
- **統一せず使い分けました。** 「他の関数値と同じ入れ物に入るか」で決めます。選択肢が多いことが、判断を要求します
- **`FnMut` を避けて `Mutex` にしました。** 所有権が「どちらを崩すか」を選ばせ、押された先は第 5 章と揃うほうでした
- **クロージャには名前がありません。** 静的ディスパッチだと呼び出し側に型が出ますが、**「デバッグしやすい」は言い過ぎでした**
- **2 つめの段階を切り、手順を決めました。** 実行ファイルの名前が衝突します
- **測り直したら結論が変わりました。** 最初の数字は段階の代償ではなく、依存の初回ビルドでした

次の章で、この「足す」をイベントにします。**そこで `Box<dyn Fn>` が要る理由が出ます。**

---

## この章で書いたコード

- 実装: `apps/rust/zettai/zettai-step2-domain/src/lib.rs`、`apps/rust/zettai/zettai-step2-http/src/store.rs`、`apps/rust/zettai/zettai-step2-http/src/http.rs`、`apps/rust/zettai/zettai-step2-http/src/acceptance.rs`、`apps/rust/zettai/zettai-step2-http/src/bin/zettai.rs`
- 構成: `apps/rust/zettai/Cargo.toml`、`apps/rust/zettai/zettai-step2-http/Cargo.toml`
- 受け入れ: `apps/rust/zettai/zettai-step2-http/tests/see_a_todo_list.rs`
- スパイク: `apps/rust/zettai/spikes/di-shapes/`
- 制約の一覧: `apps/rust/zettai/README.md`

本文のコードは、上のファイルからの転記です。次の 3 種類だけは逐語の転記ではありません。

- コマンドの実行例と、その出力
- つまずきの説明のために、わざと壊したコード
- 比較のために引いた他言語のコード

## 参照

Uberto Barbini『From Objects to Functions』第 4 章。題材と章構成をこの本に拠っています。本連載のコードはすべて書き起こした自作実装で、原著のコードを転載したものではありません。

- [Zettai — Rust 版](index.md) / [第 3 章](chapter03.md) / [Kotlin 版の第 4 章](../kotlin/chapter04.md) / [なでしこ3 版の第 4 章](../nadesiko/chapter04.md)
- [ADR-029 段階を切る手順を決める](../../../adr/ADR-029-cutting-a-step.md) / [ADR-030 関数型 DI は impl Fn、並びに入れる変換は Box<dyn Fn> にする](../../../adr/ADR-030-functional-di-shape.md)
