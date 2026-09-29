---
type: Article
title: "第 3 章 ドメインの定義とテスト"
description: "Zettai 連載 Rust 版の第 3 章。受け入れテストの経路をトレイトで差し替え、ドメインとインフラをクレートで分ける。2 対象が境界検査を書いて守ったものを、コンパイラが肩代わりする。既製品の cucumber を調べて入れなかった理由も数字で示す。"
tags: [article, zettai, rust, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-29T02:08:46Z }
---

# 第 3 章 ドメインの定義とテスト

第 2 章で縦串が通りました。**ドメインと HTTP は同じクレートの中にいます。**

この章で分けます。2 対象はここで検査を書きました。型のある言語では、**コンパイラに守らせられるかもしれません。**

## 受け入れテストを改善する

第 2 章のシナリオは、ドメインを直接呼んでいました。

<!-- code-check: ignore 第 2 章の時点の形。この章で置き換える -->

```rust
    let list =
        fetch_list(&User::new("uberto"), &ListName::new("book")).expect("book のリストがある");
```

**これだと HTTP を通ったときに同じことが起きるかを確かめていません。** HTTP のハンドラには別のテストがありますが、それは「ハンドラが 200 を返すか」を見ているだけです。

やりたいのは、**同じシナリオを複数の経路で走らせること**です。片方だけ落ちたら、そこに業務のロジックが漏れています。

3 対象で共通の考え方です（[ADR-016](../../../adr/ADR-016-own-acceptance-entry.md)）。手段が違います。

| 対象 | 手段 |
| :--- | :--- |
| Kotlin | Pesticide の `DdtActions`（ライブラリ） |
| なでしこ3 | 関数値を詰めた辞書（自作） |
| Rust | **これから決める** |

### まず既製品を調べる

**「自前で書く」を惰性で続けない**と決めています。Rust には `cucumber` があります。

入れて測りました。

| 項目 | 現状 | `cucumber` を入れると |
| :--- | :--- | :--- |
| 依存クレート数 | **6** | **127** |
| ビルド時間 | — | **+92 秒** |

`just check` の上限は 20 秒です。**単独で超えます。**

与えてくれるのは Gherkin の `.feature` ファイル、つまり自然言語でシナリオを書く仕組みです。**それは欲しいものではありませんでした。**

シナリオは第 2 章の時点で既に業務の言葉だけで書けています。足りないのは**経路の差し替え**だけで、それは Rust に最初からあるもので足ります。

## 高階関数を使う

Kotlin 版となでしこ3 版は、関数値を渡して経路を差し替えました。**Rust ではトレイトのほうが素直です。**

```rust
/// シナリオが使える操作。**経路ごとに実装する。**
///
/// Kotlin 版は Pesticide の `DdtActions`、なでしこ3 版は関数値を詰めた辞書。
/// Rust はトレイトがそのまま当たる。
pub trait ZettaiActions {
    /// 記録に残す経路の名前。どの経路で落ちたかを知るために要る。
    fn route(&self) -> &'static str;

    /// 利用者のリストの項目を、説明の並びで返す。無ければ `None`。
    fn items_of(&self, user: &str, list_name: &str) -> Option<Vec<String>>;
}
```

経路 1 はドメインを直接呼びます。

```rust
impl ZettaiActions for DomainOnly {
    fn route(&self) -> &'static str {
        "ドメイン直接"
    }
```

経路 2 は HTTP のハンドラを通します。

```rust
/// 経路 2: HTTP のハンドラを通す。
///
/// **サーバを起こさない。** `handle` が `tiny_http` の型を返さないので、
/// 関数として呼べる（第 2 章）。なでしこ3 版はサーバの起動と停止を
/// `Makefile` が持つ必要があった。
pub struct ThroughHttp;
```

**第 2 章の判断がここで効きました。** `handle` が `Reply` を返すようにしておいたので、HTTP 経由の経路がサーバを起こさずに書けます。

なでしこ3 版は「関数値ごしに非同期な命令を呼べない」という制約があり、応答を先に記録してからシナリオに渡していました。**言語の制約が受け入れテストの形まで決めていた**わけです。

### シナリオは経路を知らない

```rust
#[test]
fn every_route_tells_the_same_story() {
    for actions in all_routes() {
        uberto_sees_his_book_list(actions.as_ref());
        an_empty_list_is_still_a_list(actions.as_ref());
        an_unknown_list_is_not_found(actions.as_ref());
    }
}
```

シナリオ側は `&dyn ZettaiActions` しか見ません。

```rust
fn an_unknown_list_is_not_found(actions: &dyn ZettaiActions) {
    assert_eq!(
        actions.items_of("uberto", "nope"),
        None,
        "{}: 無いリストは見つからない",
        actions.route()
    );
}
```

**`route()` があるので、どの経路で落ちたかがメッセージに出ます。** なでしこ3 版の Unit 6 で、HTTP 経由だけが捕まえた実装の欠陥がありました。経路名が出ていたから分かったことです。

### つまずき 1: `Vec<String>` と `&[&str]` は比べられない

期待値を素直に書いたら落ちました。

<!-- code-check: ignore わざと壊したコード -->

```rust
assert_eq!(
    actions.items_of("uberto", "book"),
    Some(&["write chapter", "insert code", "publish book"][..]),
    ...
);
```

```bash
error[E0308]: mismatched types
   = note: expected enum `Option<&[String]>`
              found enum `Option<&[&str]>`
```

`items_of` が返すのは `Vec<String>` で、期待値は `&[&str]` です。**Rust はこの 2 つを同じものとして扱いません。**

比べる直前に揃えます。

```rust
    // 期待値は `&str` で並べ、比べる直前に `String` へ揃える。
    // `Vec<String>` に `&[&str]` はそのまま比べられない（E0308）。
    let expected = ["write chapter", "insert code", "publish book"].map(String::from);
```

**期待値は `&str` のまま書けるので、シナリオの読みやすさは保てました。** 1 行増えただけです。

2 対象にはこの摩擦がありません。Kotlin は `String` が 1 種類で、なでしこ3 は型を区別しません。**型が多いことの代償**が、こういう形で出ます。

## ドメインとインフラストラクチャの分離

ここが本題です。**どう分けるか**を決めます。

選択肢は 2 つありました。

- **案A モジュール分割**: 1 クレートの中で `mod domain` / `mod http`
- **案B クレート分割**: `zettai-step1-domain` / `zettai-step1-http`

両方を作り、**ドメインから HTTP を呼ぶコードをわざと書いて**確かめました。

| | 案A モジュール | 案B クレート |
| :--- | :--- | :--- |
| ドメインから HTTP を呼ぶと | **通る**（エラー 0 件） | **コンパイルが止まる** |
| `cargo test --all` | 3 秒 | **3 秒**（変わらず） |
| `Cargo.toml` | 1 個 | 2 個 |
| 境界検査 | **書く必要がある** | **書かなくてよい** |

案A で境界を破ってもエラーは 0 件でした。**同一クレートの中は相互参照できます。** 2 対象と同じで、検査を書いて守るしかありません。

案B ではこうなります。

```bash
error[E0433]: failed to resolve: use of unresolved module or unlinked crate `zettai_step1_http`
error: could not compile `zettai-step1-domain` (lib) due to 1 previous error
```

**ビルド時間は変わりませんでした。** 増えるのは `Cargo.toml` の数だけです。

クレートを分けました（[ADR-027](../../../adr/ADR-027-crate-boundary.md)）。

```toml
# **ドメインへの依存は一方向。** ドメイン側の Cargo.toml に
# zettai-step1-http は無い。だからドメインから HTTP は呼べない。
[dependencies]
zettai-step1-domain = { path = "../zettai-step1-domain" }
tiny_http = "0.12.0"
```

ドメイン側はこうです。

```toml
# **依存を持たない。** ここに何かを足すときは、それがドメインの言葉か
# どうかを疑う。HTTP も永続化もここには来ない（Unit 2 のゲート 2）。
[dependencies]
```

**空です。** 何かを足すときに目立ちます。

### 境界検査を 1 行も書いていない

これが 2 対象との最大の差です。

| 対象 | 境界を守るために書いたもの |
| :--- | :--- |
| Kotlin | `DomainBoundaryTest`（import を機械的に検査） |
| なでしこ3 | `boundary_test.nako3`（7 ファイル × 5 項目 = 35 件） |
| **Rust** | **無し** |

なでしこ3 版は、章が進むごとに検査の項目が増えました。第 2 章で 1 項目、第 10 章で 4 項目、第 12 章で 5 項目です。**ファイルが増えるたびに一覧へ足す必要があり、第 4 章では実際に漏れかけました。**

Rust では `Cargo.toml` が一覧そのものです。**足し忘れることがありません。** 書いていないものは使えないからです。

## ドメインからテストを駆動する

ドメインのクレートには、ドメインのテストだけがあります。

```rust
    #[test]
    fn a_known_list_has_its_items() {
        let list = fetch_list(&User::new("uberto"), &ListName::new("book")).unwrap();
        assert_eq!(list.items.len(), 3);
        assert_eq!(list.list_name, ListName::new("book"));
    }
```

**HTTP を知らないので、HTTP のことを書けません。** 書こうとしたらコンパイルが止まります。

これは「気をつける」ではなく「できない」です。**規律がコンパイラに移りました。**

## DDT をトレイトに変換する

> この節はマインドマップでは「DDTをPesticideに変換する」です。Pesticide は Kotlin のライブラリで Rust には無いので読み替えました（[執筆計画](../../outline.md) の規約）。

Kotlin 版はここで、自作の DDT を Pesticide というライブラリに載せ替えます。**枠組みを既製品に寄せる**章です。

なでしこ3 版は寄せる先が無いので、「共通の仕組み」を自作しました。

**Rust は寄せる先があります。** `cucumber` です。そして**寄せませんでした。**

理由は上に書いたとおりで、依存 127 クレート・ビルド +92 秒に対して、得られるのは Gherkin だけだからです。**必要だったのは経路の差し替えで、それは言語にありました。**

| 対象 | この節でしたこと |
| :--- | :--- |
| Kotlin | 自作 → ライブラリ（Pesticide） |
| なでしこ3 | 自作のまま（寄せる先が無い） |
| **Rust** | **調べて、寄せないと決めた** |

**3 通りに分かれました。** 同じ節で、同じ目的で、結論が 3 つとも違います。

これは「どれが正しいか」ではありません。**言語が何を与えるかで、同じ判断の答えが変わる**という例です。

## まとめ

- **既製品を先に調べました。** `cucumber` は依存 6 → 127、ビルド +92 秒で `just check` の上限を単独で超えます。与えるのは Gherkin で、必要だったのは経路の差し替えでした
- **経路の差し替えはトレイト 1 つで足りました。** 依存もビルド時間も増えません
- **第 2 章の判断がここで効きました。** `handle` が `tiny_http` の型を返さないので、HTTP 経由の経路がサーバを起こさずに書けます
- **境界をクレートで分けました。** ドメインから HTTP を呼ぶとコンパイルが止まります。ビルド時間は変わりませんでした
- **境界検査を 1 行も書いていません。** Kotlin 版は import の検査、なでしこ3 版は 7 ファイル × 5 項目の検査を書きました。`Cargo.toml` が一覧そのものなので、足し忘れが起きません
- **`Vec<String>` と `&[&str]` は比べられませんでした。** 型が多いことの代償です。1 行増やして揃えました
- **同じ節（DDT の変換）で、3 対象の結論が 3 つとも違いました。** 寄せる／寄せられない／寄せないと決めた。**言語が何を与えるかで、同じ判断の答えが変わります**

Phase 1 はここまでです。次の章から、ドメインが本格的に育ちます。**所有権が本当に効いてくるのは、そこからです。**

---

## この章で書いたコード

- 実装: `apps/rust/zettai/zettai-step1-domain/src/lib.rs`、`apps/rust/zettai/zettai-step1-http/src/acceptance.rs`
- 構成: `apps/rust/zettai/Cargo.toml`、`apps/rust/zettai/zettai-step1-http/Cargo.toml`、`apps/rust/zettai/zettai-step1-domain/Cargo.toml`
- 受け入れ: `apps/rust/zettai/zettai-step1-http/tests/see_a_todo_list.rs`
- 制約の一覧: `apps/rust/zettai/README.md`

本文のコードは、上のファイルからの転記です。次の 3 種類だけは逐語の転記ではありません。

- コマンドの実行例と、その出力
- つまずきの説明のために、わざと壊したコード
- 比較のために引いた他言語のコード

## 参照

Uberto Barbini『From Objects to Functions』第 3 章。題材と章構成をこの本に拠っています。本連載のコードはすべて書き起こした自作実装で、原著のコードを転載したものではありません。

- [Zettai — Rust 版](index.md) / [第 2 章](chapter02.md) / [Kotlin 版の第 3 章](../kotlin/chapter03.md) / [なでしこ3 版の第 3 章](../nadesiko/chapter03.md)
- [ADR-027 境界をクレートで分け、境界検査を書かない](../../../adr/ADR-027-crate-boundary.md) / [ADR-028 受け入れテストの経路をトレイトで差し替える](../../../adr/ADR-028-own-acceptance-trait.md)
