---
type: Article
title: "第 5 章 イベントで状態を変更する"
description: "Zettai 連載 Rust 版の第 5 章。状態の変更をイベントで表し、状態から状態への関数を合成してモノイドにする。impl Fn では畳み込めない理由と、性質テストの検出率を分布の計算とともに測る話。既製品を測って入れなかった判断も記録する。"
tags: [article, zettai, rust, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-29T03:25:36Z }
---

# 第 5 章 イベントで状態を変更する

第 4 章で項目を足せるようになりました。**足した結果だけが残っています。**

何が起きたかは残っていません。この章で、**起きたこと**を残します。

## ToDo リストの作成の表示

ここまで、リストは最初からありました。`fetch_list` に `"book"` と `"shopping"` が埋め込まれていて、**リストが生まれる瞬間がありません。**

生まれる瞬間を、起きたこととして書きます。

```rust
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ToDoListEvent {
    ListCreated { list_name: ListName },
    ItemAdded { item: ToDoItem },
}
```

**`enum` が言語にあります。** Kotlin 版は sealed class を、なでしこ3 版は辞書の `種別` キーを使いました。3 対象で初めて、**書くものが一番少ない章**です。

`match` の網羅をコンパイラが見るので、種類を足したときに**適用を書き忘れると止まります**。なでしこ3 版は「種別が知らない値だったら」を自分で書きました。

## 状態変更の保存

状態を保存するのをやめます。**起きたことだけを持ち、状態は毎回作ります。**

イベント 1 つは、「状態をこう変える」という指示です。**それを関数にします。**

```rust
pub fn transform_for(event: ToDoListEvent) -> Transform {
    match event {
        ToDoListEvent::ListCreated { list_name } => Box::new(move |_previous| ToDoList {
            list_name: list_name.clone(),
            items: Vec::new(),
        }),
        ToDoListEvent::ItemAdded { item } => Box::new(move |list: ToDoList| ToDoList {
            list_name: list.list_name,
            items: [list.items, vec![item.clone()]].concat(),
        }),
    }
}
```

`ListCreated` は前の状態を見ません（`_previous`）。**イベントの意味がそのまま関数の形に出ています。**

### つまずき 1: 借りた値は閉じ込められない

イベントの中身をクロージャに渡すとき、参照で済ませようとしました。

<!-- code-check: ignore つまずきの説明のために、わざと落ちるコードを書いている -->

```rust
type Transform = Box<dyn Fn(Vec<String>) -> Vec<String>>;

fn make(prefix: &str) -> Transform {
    Box::new(move |mut s| { s.push(prefix.to_string()); s })
}
```

```text
error: lifetime may not live long enough
  |     Box::new(move |mut s| { s.push(prefix.to_string()); s })
  |     ^^^^^^^^ returning this value requires that `'1` must outlive `'static`
```

**`Box<dyn Fn>` には既定で `+ 'static` が付きます。** 借りた値を閉じ込めると、借り先より長生きできません。

コンパイラは 2 つ提案します。「型別名に `'a` を足す」か、「借りずに所有する」かです。**所有するほうを選びました。**

イベントは保存して後から畳み込みます。**借り先は先に消えます。** 押された先が、この設計では正しい形でした。

なでしこ3 版で 5 回起きた「制約に押されて、押された先が正しかった」と同じです。**型の側の制約でも同じことが起きる**という仮説の、これで 3 件目です。

## 再帰の力を解き放つ

イベントの並びから状態を作ります。再帰でも書けますが、**合成で書きます。**

```rust
/// 何もしない変換。**合成の単位元。**
pub fn identity() -> Transform {
    Box::new(|list| list)
}

/// 2 つの変換を繋ぐ。**結果も変換なので、繰り返せる。**
pub fn compose(f: Transform, g: Transform) -> Transform {
    Box::new(move |list| g(f(list)))
}
```

**「結果も変換」がすべてです。** これがあるから、並びをそのまま畳めます。

```rust
pub fn fold_events(events: Vec<ToDoListEvent>) -> Transform {
    events
        .into_iter()
        .map(transform_for)
        .fold(identity(), compose)
}
```

### なぜ `Box<dyn Fn>` なのか

第 4 章で決めた使い分けが、ここで効きます（[ADR-030](../../../adr/ADR-030-functional-di-shape.md)）。

`impl Fn` で書くと、この `fold` は通りません。

```text
= note: expected opaque type `impl Fn(Vec<String>) -> Vec<String>`
           found opaque type `impl Fn(Vec<String>) -> Vec<String>`
= note: distinct uses of `impl Trait` result in different opaque types
```

自前のトレイトで合成を型として表しても同じです。1 周目で型が変わります。

```text
expected `Identity`, found `Composed<Identity, Add>`
```

**「同じ入れ物に入るか」で形を選ぶ**という第 4 章の基準は、この 2 つのエラーから来ています。

Kotlin 版にこの判断はありません。関数型は 1 種類で、`fold` がそのまま通ります。なでしこ3 版にもありません。関数値に型がありません。**選択肢があることの代償を、Rust だけが払っています。**

## イベントを畳み込む

```rust
pub fn replay(events: Vec<ToDoListEvent>) -> ToDoList {
    fold_events(events)(empty_list())
}
```

テストは、起きたことを並べて書くだけです。

```rust
    #[test]
    fn creating_again_starts_over() {
        // ListCreated は前の状態を見ない。**イベントの意味がそのまま出る。**
        let list = replay(vec![created("book"), added("write"), created("shopping")]);
        assert_eq!(list.list_name, ListName::new("shopping"));
        assert!(list.items.is_empty());
    }
```

## モノイドの発見

`identity` と `compose` があって、次の 2 つが成り立ちます。

- **単位元**: `compose(identity(), f)` も `compose(f, identity())` も `f` と同じ
- **結合律**: 繋ぐ順序を変えても結果が同じ

これがモノイドです。**発見であって、作ったものではありません。** 畳み込みが書けた時点で、すでにそうなっていました。

性質テストで確かめます。

### まず既製品を調べた

3 対象共通の手順です。今回も先に測りました。

| | 自作 | `proptest` | `quickcheck` |
| :--- | :--- | :--- | :--- |
| 依存クレート | **0** | 38 | 21 |
| 依存のビルド（clean から） | **0 秒** | **26.77 秒** | 12.82 秒 |
| 与えるもの | 何も | 生成器・縮小・種の再現 | 生成器・縮小 |

`proptest` は `just check` の上限 20 秒を**単独で超えます**。第 3 章で `cucumber` を入れなかったのと同じ基準です。

`quickcheck` は収まります。それでも入れませんでした。理由は次の節です。

### 検出率を測る側から見ると、既製品は向きが逆

[ADR-017](../../../adr/ADR-017-own-property-testing.md) が「法則を壊して、200 回中何回検出したかを数える」と決めています。**既製品は最初の反例で止まる設計**です。

数えられるかは確かめました。`TestRunner` を `cases: 1` で 200 回まわせば数えられます。

```text
検出 19 / 200
```

法則を `a == 7` のときだけ壊し、`a` を `0..10` から引いたので、**期待値 20・標準偏差 4.24**。19 は期待どおりです。

**測れます。** ただしループは自分で書くことになり、そのとき既製品が担っているのは生成器だけです。縮小も種の再現も使いません。**使わない機能のために依存を 21 足すことになるので、入れませんでした。**

「測れないから自作」ではありません。**測れることを確かめたうえで、入れる理由が無いと判断しました。**

### つまずき 2: 検出率を測ったのに、何も分からなかった

最初に壊したのは合成そのものです。`compose(f, g)` が `f` を返すようにしました。

```text
検出 200 / 200
```

**全部検出します。** 壊れた合成は `identity()` を返すので、結果はもとの状態のままです。イベントは必ず状態を変えるので、必ず違いが出ます。

数字としては良いのですが、**数えた意味がありません。** 検出率が 1 に張り付く壊し方では、しきい値に分布の計算を添える意味もありません。

そこで、見逃しのある壊し方に変えました。**`item7` を足すときだけ、足さない。**

```rust
fn transform_with_a_hidden_bug(event: ToDoListEvent) -> Transform {
    match &event {
        ToDoListEvent::ItemAdded { item } if item.description == "item7" => Box::new(|list| list),
        _ => transform_for(event),
    }
}
```

```text
隠れた欠陥の検出 12 / 200
```

計算と並べます。

```text
P(ItemAdded) = 3/4、P(n == 7) = 1/10 なので p = 0.075
期待値 = 200 × 0.075 = 15
σ = √(200 × 0.075 × 0.925) = 3.72
```

実測 12 は期待値から 1σ 以内です。**しきい値は期待値から 3σ 下（4 件）に置きました。**

なでしこ3 版では、しきい値を期待値から **1.58σ** しか離しておらず、**CI が確率 5.7% で落ちました**。落ちるたびに再実行して通っていたので、原因が分かるまで 3 Unit かかっています。

そしてこの数字は、**試行回数が 200 であることの意味**も示します。20 回なら期待値 1.5 で、**1 度も引かない確率が約 22%** あります。**検出率を測らないと、この欠陥を見逃すテストを「通った」と呼ぶことになります。**

### 途中で間違えた

この節を書くために、最初は「合成の順序を入れ替える」壊し方で測りました。見逃しが 3/16 出るはずだと計算して、しきい値を置きました。

実測は 200 / 200 でした。**計算が間違っていました。** `compose(f, g)` は `g(f(x))` なので、入れ替えると最後に適用されるイベントが変わります。どちらの順でも結果は違うので、見逃しは起きません。

**計算を実測と突き合わせていなければ、間違ったまま「分布の計算を添えた」と書いていました。** 分布を書くことが目的ではなく、**書いた分布が当たっているかを確かめることが目的**です。

## まとめ

- **`enum` が言語にあります。** 3 対象で、この章に書くものが一番少ない版です
- **状態を保存せず、起きたことだけを持ちました。** 状態は毎回畳み込んで作ります
- **イベント 1 つを「状態から状態への関数」にしました。** `ListCreated` が前の状態を見ないことが、そのまま `_previous` に出ます
- **借りた値はクロージャに閉じ込められませんでした。** `Box<dyn Fn>` は既定で `'static` です。**押された先（所有する）が、この設計では正しい形でした**。制約が設計を押した 3 件目です
- **合成の結果が同じ型に戻ることが、畳み込みのすべてです。** `impl Fn` と自前トレイトはここで落ちます
- **既製品は測ったうえで入れませんでした。** 検出率は測れます。ただしそのとき使うのは生成器だけです
- **検出率が 200 / 200 になる壊し方では、数えても何も分かりません。** 見逃しのある壊し方に変えて 12 / 200 を得ました（期待値 15・σ 3.72）
- **分布の計算を 1 度間違えました。** 実測と突き合わせて気づきました。計算を書くことが目的ではありません

Phase 2 の前半はここまでです。次の章から、コマンドとイベントを分けます。**`enum` がもう一度効きます。**

---

## この章で書いたコード

- 実装: `apps/rust/zettai/zettai-step2-domain/src/lib.rs`
- 性質テスト: `apps/rust/zettai/zettai-step2-domain/tests/monoid_laws.rs`
- スパイク: `apps/rust/zettai/spikes/prop-frameworks/`、`apps/rust/zettai/spikes/di-shapes/`
- 検査: `apps/rust/zettai/justfile`、`ops/scripts/check_rust_ci_parity.py`
- 制約の一覧: `apps/rust/zettai/README.md`

本文のコードは、上のファイルからの転記です。次の 3 種類だけは逐語の転記ではありません。

- コマンドの実行例と、その出力
- つまずきの説明のために、わざと壊したコード
- 比較のために引いた他言語のコード

## 参照

Uberto Barbini『From Objects to Functions』第 5 章。題材と章構成をこの本に拠っています。本連載のコードはすべて書き起こした自作実装で、原著のコードを転載したものではありません。

- [Zettai — Rust 版](index.md) / [第 4 章](chapter04.md) / [Kotlin 版の第 5 章](../kotlin/chapter05.md) / [なでしこ3 版の第 5 章](../nadesiko/chapter05.md)
- [ADR-017 性質テストを自作し、検出率を測ってから信用する](../../../adr/ADR-017-own-property-testing.md) / [ADR-030 関数型 DI は impl Fn、並びに入れる変換は Box<dyn Fn> にする](../../../adr/ADR-030-functional-di-shape.md)
