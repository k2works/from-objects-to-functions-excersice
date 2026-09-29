---
type: Article
title: "第 7 章 関数型手法によるエラーハンドリング"
description: "Zettai 連載 Rust 版の第 7 章。Result が言語にある場合に、この章の主題が何になるか。既製品を測って自前の enum を選んだ理由、Option から Result への波及を数えた結果、Result が must_use であることが握りつぶしを見つけた話。"
tags: [article, zettai, rust, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-29T04:07:53Z }
---

# 第 7 章 関数型手法によるエラーハンドリング

**Kotlin 版はこの章で `Outcome` を作りました。** なでしこ3 版は結果辞書を作りました。

Rust には `Result` があります。**作るものがありません。**

では、この章に何が残るのでしょうか。

## より適切なエラー処理

いま失敗は 2 通りの表し方で散らばっています。

| どこ | 表し方 | 分かること |
| :--- | :--- | :--- |
| リストを取り出す | `Option<ToDoList>` | 「無い」だけ |
| コマンドを断る | `Rejected`（第 6 章） | 理由 2 つ |

`Option` の問題は、**「無い」としか言わないこと**です。誰のどのリストが無いのか、そもそも利用者が違うのか、区別がつきません。

なでしこ3 版が第 7 章で書いたのと同じ問題です。あちらは `空` が「値が無い」「空文字列」「見つからない」を区別しませんでした。**型があっても、`Option` を選んだ時点で同じところに立っています。**

### 既製品を先に調べる

Rust にはエラーのためのクレートがあります。**測る条件を先に決めてから測りました。**

1. 空のクレートから始める
2. `cargo add` したあと `cargo clean` してからビルドする
3. 各案とも同じ手順

| | 自前の `enum` | `thiserror` | `anyhow` |
| :--- | :--- | :--- | :--- |
| 依存クレート | **+0** | **+10** | **+1** |
| 依存のビルド | **0 秒** | **12.29 秒** | **2.15 秒** |
| 失敗を `match` で分けられるか | **できる** | できる | **できない** |

`anyhow` は軽いのですが、**型を 1 つに潰します**。第 6 章で理由を型にしたから HTTP 側の `match` が漏れを止めているので、それを捨てることになります。

`thiserror` が与えるのは `Display` の導出と `#[from]` です。**`#[from]` の中身も確かめました。**

```rust
impl From<DomainError> for HttpError {
    fn from(e: DomainError) -> Self {
        HttpError::Domain(e)
    }
}
```

**これを 1 つ書けば `?` が経路をまたいで変換します。** 導出のために依存を 10 足す理由が見つかりませんでした。

**自前の `enum` にしました**（[ADR-031](../../../adr/ADR-031-own-error-enum.md)）。第 3 章の `cucumber`、第 5 章の `proptest` と同じ基準です。

### 第 6 章の型を育てる

新しく作るのではなく、第 6 章の `Rejected` を育てました。

```rust
pub enum ZettaiError {
    /// 同じ名前のリストがもうある。
    ListAlreadyExists { list_name: ListName },
    /// リストが見つからない。**第 6 章の `ListDoesNotExist` と、
    /// `Option` の `None` が、ここで 1 つになった。**
    ListNotFound { user: User, list_name: ListName },
    /// 説明が空。
    EmptyDescription,
}
```

**「断られた」と「見つからない」が 1 つの型になりました。** 呼ぶ側から見ればどちらも「うまくいかなかった」で、区別する理由がありません。

理由が**値を持つ**ようになったのが効いています。`ListNotFound` は誰のどのリストかを持ちます。

```rust
    pub fn fetch(&self, user: &User, list_name: &ListName) -> Result<ToDoList, ZettaiError> {
        self.lists
            .lock()
            .expect("毒されていない")
            .get(&(user.0.clone(), list_name.0.clone()))
            .cloned()
            .ok_or_else(|| ZettaiError::ListNotFound {
                user: user.clone(),
                list_name: list_name.clone(),
            })
    }
```

### 契約を先に変えて、波及を数えた

なでしこ3 版 Unit 4 と同じ手です。**実装より先にシグネチャを変え、落ちた箇所を数えます。**

| 段 | 落ちた件数 |
| :--- | :--- |
| ドメインのシグネチャを 3 つ変える | **4 件**（同じクレート内） |
| ドメインのテスト | **7 件** |
| HTTP のクレート | **10 件** |

**全体の件数は先に見えません。** cargo はクレート単位で止まるので、ドメインが通るまで HTTP 側は出てきません。

これはなでしこ3 版と対照的です。あちらは**契約テストを走らせて初めて**落ちる箇所が分かり、テストが無い箇所は分かりませんでした。こちらは**テストが無くても全部出ます**。代わりに、一度に全部は見えません。

### つまずき: `Result` は捨てると止まるが、`Option` は止まらない

`just check` が落ちました。

```text
error: unused `std::result::Result` that must be used
  --> zettai-step3-http/src/acceptance.rs:78:9
```

受け入れテストの経路で、**項目を足した結果を捨てていました**。足せなかったときに黙って通っていたということです。

`Option` のときは同じコードが通っていました。確かめました。

<!-- code-check: ignore 違いを見るために書いた使い捨てのコード -->

```rust
fn o() -> Option<i32> { Some(1) }
fn r() -> Result<i32, ()> { Ok(1) }
fn main() { o(); r(); }
```

```text
warning: unused `Result` that must be used
  = note: `#[warn(unused_must_use)]` (part of `#[warn(unused)]`) on by default
```

**`Option` には警告が出ません。** `Result` だけが `#[must_use]` です。

**型を変えたことで、既にあった欠陥が 1 つ見つかりました。** これは 2 対象に無い出来事です。なでしこ3 版は「戻り値を捨てた」ことを誰も見ていません。Kotlin 版の `Outcome` も自作なので、`@CheckReturnValue` を自分で付けない限り止まりません。

## ファンクタと圏を学ぶ

`Result` には `map` があります。**書くものがありません。**

Kotlin 版はここで `Outcome.map` を書き、なでしこ3 版は `結果写像` を書きました。**Rust ではその節が「使う」節になります。**

それでも法則は確かめます。**言語が与えたものが法則を満たしているかは、使う側が確かめることです。**

## ファンクタを使ったエラーハンドリング

```rust
#[test]
fn mapping_twice_equals_mapping_the_composition() {
    let mut rng = Rng::new(31415926);
    for _ in 0..TRIALS {
        let outcome = any_outcome(&mut rng);

        let twice = outcome.clone().map(add_marker).map(rename);
        let once = outcome.map(|list| rename(add_marker(list)));

        assert_eq!(twice, once);
    }
}
```

### つまずき: clippy が恒等則のテストを書かせてくれない

恒等の法則（`map(id) == id`）を書いたら、`just check` が落ちました。

```text
error: unnecessary map of the identity function
```

**「恒等関数を `map` するな」はふだんは正しい**指摘です。けれどここでは、**それが確かめたい法則そのもの**です。消すとテストが消えます。

理由を書いて許可しました。

```rust
/// **clippy が `map_identity` で止める。** 「恒等関数を `map` するな」は
/// ふだんは正しいが、**ここではそれが確かめたい法則そのもの**。
/// 消すとテストが消える。
#[allow(clippy::map_identity)]
```

第 13 章で「設計の改善」と「clippy の好み」に仕分ける表を作っています。**これは好みの側です。** ここまでで、改善が 2 件・好みが 1 件になりました。

### 検出率を測る

Unit 3 の学びを適用しました。**見逃しのある壊し方を先に設計します。**

`map` を壊します。**項目が 2 件のときだけ、変換を飛ばす**ようにしました。

```text
隠れた欠陥の検出 50 / 200
```

計算と並べます。

```text
P(Ok) = 3/4、P(項目が 2 件) = 1/3 なので p = 0.25
期待値 = 200 × 0.25 = 50
σ = √(200 × 0.25 × 0.75) = 6.12
```

**実測 50、期待値 50。** 前の章では計算を 1 度間違えて実測に直されたので、今回は壊し方を先に設計してから計算しました。

しきい値は期待値から **4σ 下**（25）に置いています。

## `Result` を使って実装する

`?` が効きます。

```rust
        let events = execute_in(state_of_list(current.as_ref()), user, list_name, command)?;
```

HTTP 側は理由を状態コードに写すだけです。

```rust
fn failed(reason: ZettaiError) -> Reply {
    match reason {
        ZettaiError::ListNotFound { .. } => not_found(),
        ZettaiError::ListAlreadyExists { .. } => Reply {
            status: 409,
            body: "<html><body><h1>409</h1></body></html>".to_string(),
        },
        ZettaiError::EmptyDescription => bad_request(),
    }
}
```

**理由を 1 つ増やしたら、実際にここが止まりました。** 第 6 章では 2 つだった `match` が 3 つになっています。

そして**この `match` は単体の `enum` を直接受けている**ので、第 6 章の遷移表と違い、ワイルドカードを置いたら lint が止めます。**同じ `match` でも形によって守られ方が違います。**

## まとめ

- **作るものがありませんでした。** `Result` も `map` も言語にあります
- **それでも判断は残りました。** 既製品を入れるか、理由をどう表すか、どこまで型で区別するか
- **既製品を測って自前にしました。** `thiserror` は依存 +10・12.29 秒、`anyhow` は `match` で分けられません。`#[from]` の中身は 1 つ書けば済む `impl From` でした
- **第 6 章の型を育てました。** 「断られた」と「見つからない」が 1 つになり、理由が値を持つようになりました
- **波及を数えました。** ドメイン 4 件 → テスト 7 件 → HTTP 10 件。**一度に全部は見えません**
- **`Result` は `#[must_use]`、`Option` は違いました。** 型を変えたら、失敗を握りつぶしていた箇所が 1 つ見つかりました
- **clippy が恒等則のテストを止めました。** これは好みの側に仕分けます
- **ファンクタ則の検出率は 50 / 200 で、計算した期待値どおりでした**（σ 6.12。しきい値は 4σ 下）

Phase 2 の後半に入ります。次の章から射影と永続化です。**`Result` がそこで効いてきます。**

---

## この章で書いたコード

- 実装: `apps/rust/zettai/zettai-step3-domain/src/lib.rs`、`apps/rust/zettai/zettai-step3-http/src/store.rs`、`apps/rust/zettai/zettai-step3-http/src/http.rs`、`apps/rust/zettai/zettai-step3-http/src/acceptance.rs`
- 性質テスト: `apps/rust/zettai/zettai-step3-domain/tests/functor_laws.rs`
- スパイク: `apps/rust/zettai/spikes/error-crates/`
- 制約の一覧: `apps/rust/zettai/README.md`

本文のコードは、上のファイルからの転記です。次の 3 種類だけは逐語の転記ではありません。

- コマンドの実行例と、その出力
- つまずきの説明のために、わざと壊したコード
- 比較のために引いた他言語のコード

## 参照

Uberto Barbini『From Objects to Functions』第 7 章。題材と章構成をこの本に拠っています。本連載のコードはすべて書き起こした自作実装で、原著のコードを転載したものではありません。

- [Zettai — Rust 版](index.md) / [第 6 章](chapter06.md) / [Kotlin 版の第 7 章](../kotlin/chapter07.md) / [なでしこ3 版の第 7 章](../nadesiko/chapter07.md)
- [ADR-031 失敗を自前の enum で表す](../../../adr/ADR-031-own-error-enum.md) / [ADR-017 性質テストの自作](../../../adr/ADR-017-own-property-testing.md)
