---
type: Article
title: "第 2 章 関数を使って HTTP を扱う"
description: "Zettai 連載 Rust 版の第 2 章。tiny_http で縦串を通す。ルーティング機構が無いのでパスの分解を自前で書き、フレームワークに隠されない形で「矢印で設計する」を見る。所有権に最初に押された箇所と、それが設計をどこへ動かしたかを記録する。"
tags: [article, zettai, rust, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-29T02:08:46Z }
---

# 第 2 章 関数を使って HTTP を扱う

第 1 章では判断をしました。この章では**動くものを作ります。**

そして 2 対象に無かった相手に初めて当たります。**所有権**です。

## プロジェクトのキックオフ

作るのは 1 つだけです。**利用者が自分の ToDo リストを見られること。**

```text
GET /todo/uberto/book
  → uberto の book というリストの項目が HTML で見える
```

これを端から端まで通します。**ウォーキングスケルトン**です。薄くてよいので、HTTP からドメインまでが 1 本につながっていることを先に作ります。

3 対象ともこれを第 2 章に置いています。**章の位置を揃えることが、比較を成立させる前提**だからです。

## 関数型で HTML ページを提供する

第 1 章で選んだのは `tiny_http` でした（[ADR-025](../../../adr/ADR-025-tiny-http.md)）。依存 5 クレート、ビルド 7 秒。**ルーティング機構はありません。**

無いことが、この章では都合がよくなります。

`axum` や `rouille` を選んでいたら、パスの分解はマクロや型が引き受けます。便利ですが、**「矢印で設計する」という第 2 章の主題が、フレームワークの裏に隠れます。**

なでしこ3 版も同じ形でした。簡易 HTTP サーバのプラグインは前方一致しかせず、パスの分解を自前で書いています。**3 対象のうち 2 つで、手で書くことになりました。**

## Zettai の開発を始める

受け入れシナリオから書きます。業務の言葉だけで書きます。

<!-- code-check: ignore 第 3 章で経路をトレイトに差し替えるため、この形は第 2 章の時点のもの -->

```rust
#[test]
fn uberto_sees_his_book_list() {
    // 一度変数に受ける。`fetch_list(..).expect(..).items.iter()` と繋ぐと
    // 一時値が借用中に破棄される（E0716）。**所有権に押された 1 件目。**
    let list =
        fetch_list(&User::new("uberto"), &ListName::new("book")).expect("book のリストがある");
```

**コメントが付いているのは、最初は違う書き方をしたからです。** それは後述します。

走らせると赤になります。

```bash
error[E0432]: unresolved imports `zettai_step1_http::fetch_list`, `zettai_step1_http::User`
error: could not compile `zettai-step1-http` (test "see_a_todo_list") due to 1 previous error
```

**これは「テストが失敗する Red」ではなく「コンパイルが通らない Red」です。** 第 1 章で書き分けると決めた 2 種類のうちの後者です。

### つまずき 1: 一時値が借用中に消える

最初はこう書きました。

<!-- code-check: ignore わざと壊したコード -->

```rust
let descriptions: Vec<&str> = fetch_list(&User::new("uberto"), &ListName::new("book"))
    .expect("book のリストがある")
    .items
    .iter()
    .map(|item| item.description.as_str())
    .collect();
```

素直な書き方に見えます。落ちました。

```bash
error[E0716]: temporary value dropped while borrowed
   |
12 |       let descriptions: Vec<&str> = list
   |  ___________________________________^
13 | |         .expect("book のリストがある")
   | |______________________________________^ creates a temporary value which is freed while still in use
```

`fetch_list` が返した `ToDoList` は**誰のものでもない一時値**です。`.items.iter()` はそれを借りますが、文が終わると一時値は消えます。借りたままのものが消えるので、コンパイラが止めます。

直し方は 1 行増やすだけです。

<!-- code-check: ignore 第 3 章で経路をトレイトに差し替えるため、この形は第 2 章の時点のもの -->

```rust
    let list =
        fetch_list(&User::new("uberto"), &ListName::new("book")).expect("book のリストがある");

    let descriptions: Vec<&str> = list
        .items
        .iter()
        .map(|item| item.description.as_str())
        .collect();
```

**押された先が、読みやすい形でした。** 「取り出す」と「見る」が 2 行に分かれ、何をしているかが 1 行ずつになりました。

なでしこ3 版では 5 回「制約に押されて、押された先が正しかった」ことが起きました。**型の側の制約でも同じことが起きるか**がこの版の問いの 1 つで、1 件目はそうなりました。

制約の一覧の「設計に効くもの」の表に記録しました。

## 矢印で設計する

この章の主題です。**リクエストから応答までを、関数の並びとして見ます。**

```text
リクエストのパス → 利用者とリスト名 → ToDo リスト → HTML → 応答
```

矢印が 4 本あります。1 本ずつ関数にします。

```rust
/// パスを `/` で割り、空を落とす。
///
/// `tiny_http` は前方一致もしないので、ここで全部を決める。
pub fn split_path(path: &str) -> Vec<&str> {
    path.split('/').filter(|part| !part.is_empty()).collect()
}
```

```rust
/// ToDo リストを HTML にする。
pub fn render(list: &ToDoList) -> String {
    let items: String = list
        .items
        .iter()
        .map(|item| format!("<li>{}</li>", item.description))
        .collect();
```

そして繋ぎます。

```rust
/// リクエストのパスから応答を決める。**矢印はここで繋がる。**
pub fn handle(path: &str) -> Reply {
    let parts = split_path(path);
    let (user, list_name) = match parts.as_slice() {
        ["todo", user, list_name] => (User::new(user), ListName::new(list_name)),
        _ => return not_found(),
    };
```

**`match` でパスの形を書けます。** `["todo", user, list_name]` は「3 つあって、1 つめが `todo`」を 1 行で表しています。なでしこ3 版では要素数を数えてから添字で取り出しました。

### HTTP の型を返さない

`handle` が返すのは `tiny_http` の型ではありません。

```rust
/// 応答。状態コードと本文だけを持つ。
///
/// `tiny_http` の型を返さないのは、**この関数を HTTP 無しでテストできる**
/// ようにするため。サーバの組み立ては `src/bin/zettai.rs` が持つ。
#[derive(Debug, PartialEq, Eq)]
pub struct Reply {
    pub status: u16,
    pub body: String,
}
```

**この 1 つの判断で、HTTP のテストがサーバ無しで書けます。**

```rust
    #[test]
    fn a_known_list_is_rendered() {
        let reply = handle("/todo/uberto/book");
        assert_eq!(reply.status, 200);
        assert!(reply.body.contains("<li>write chapter</li>"));
        assert!(reply.body.contains("<h1>book</h1>"));
    }
```

サーバを起動しません。ポートも使いません。**並列に走ります。**

なでしこ3 版では受け入れテストの HTTP 経路でサーバを起こす必要があり、`Makefile` が起動と停止を持ちました。**そうなった原因は「関数値ごしに非同期な命令を呼べない」という言語の制約**で、こちらにはその制約がありません。

サーバ側は配線だけです。

```rust
    for request in server.incoming_requests() {
        let reply = handle(request.url());
        let response = Response::from_string(reply.body)
            .with_header(html.clone())
            .with_status_code(StatusCode(reply.status));
        let _ = request.respond(response);
    }
```

**「何を返すか」は `handle` が決め、「どう返すか」だけがここにあります。**

## ToDo リストをマップで提供する

ドメイン側はまだ薄いままにします。

```rust
pub fn fetch_list(user: &User, list_name: &ListName) -> Option<ToDoList> {
    if user != &User::new("uberto") {
        return None;
    }
    match list_name.0.as_str() {
        "book" => Some(ToDoList {
            list_name: list_name.clone(),
```

**見つからないことを `Option` で表しています。** 第 7 章で `Result` に変わり、「なぜ見つからないか」を持つようになります。

Kotlin 版は第 7 章まで `ToDoList?` でした。なでしこ3 版は `空` でしたが、**`空` は「値が無い」「空文字列」「まだ調べていない」を区別しない**ので、第 7 章で結果辞書に変えています。

`Option` にはその曖昧さがありません。**`None` は `None` だけです。** それでも第 7 章で変えるのは、**「なぜ」が要る**からです。理由は型の有無とは別の話でした。

### 空のリストも表示できる

```rust
    #[test]
    fn an_empty_list_is_still_two_hundred() {
        let reply = handle("/todo/uberto/shopping");
        assert_eq!(reply.status, 200);
        assert!(!reply.body.contains("<li>"));
    }
```

なでしこ3 版ではここに罠がありました。**`繰り返す` が開始より終了が小さいときに逆向きに回る**ので、0 件のリストを回すコードが潜在バグになっていました。

Rust の `.iter()` は 0 件なら 0 回です。**ガードが要りません。**

### 動かす

```bash
book:     200 / 3 件
shopping: 200
nope:     404
短いパス: 404
<html><body><h1>book</h1><ul><li>write chapter</li><li>insert code</li><li>publish book</li></ul></body></html>
```

縦串が通りました。

## まとめ

- **`tiny_http` にルーティング機構が無いことが、この章では都合がよくなりました。** フレームワークが隠さないので、「矢印で設計する」がそのままコードに出ます。3 対象のうち 2 つで手で書いています
- **`handle` が `tiny_http` の型を返さない**という 1 つの判断で、HTTP のテストがサーバ無しで書けます。なでしこ3 版がサーバを起こす必要があったのは、言語の制約が原因でした
- **所有権に押されたのは 1 件だけ**で、一時値の借用でした。**押された先は読みやすい形**（「取り出す」と「見る」が 2 行に分かれる）で、なでしこ3 版で 5 回起きたことと同じでした
- **`match` でパスの形を書けます。** `["todo", user, list_name]` の 1 行が、なでしこ3 版の「要素数を数えて添字で取り出す」に相当します
- **`Option` は `空` と違って曖昧ではありません。** それでも第 7 章で `Result` に変えます。「なぜ見つからないか」が要るからで、これは型の有無とは別の話です
- 0 件のリストにガードが要りませんでした。なでしこ3 版では 5 箇所すべてが潜在バグでした

> この章のシナリオは 1 経路（ドメイン直接）だけです。**第 3 章でトレイトに差し替える**ので、上の 2 つのコードは第 2 章の時点の形です（[ADR-015](../../../adr/ADR-015-step-directories.md) の「段階の中のドリフト」）。

次の章で分けます。**いまはドメインと HTTP が同じクレートの中にいます。** 分け方をどうするか——2 対象は検査を書いて守りました。型のある言語では、**コンパイラに守らせられるかもしれません。**

---

## この章で書いたコード

- 実装: `apps/rust/zettai/zettai-step1-http/src/domain.rs`、`apps/rust/zettai/zettai-step1-http/src/http.rs`、`apps/rust/zettai/zettai-step1-http/src/bin/zettai.rs`
- 受け入れ: `apps/rust/zettai/zettai-step1-http/tests/see_a_todo_list.rs`
- 制約の一覧: `apps/rust/zettai/README.md`

本文のコードは、上のファイルからの転記です。次の 3 種類だけは逐語の転記ではありません。

- コマンドの実行例と、その出力
- つまずきの説明のために、わざと壊したコード
- 比較のために引いた他言語のコード

## 参照

Uberto Barbini『From Objects to Functions』第 2 章。題材と章構成をこの本に拠っています。本連載のコードはすべて書き起こした自作実装で、原著のコードを転載したものではありません。

- [Zettai — Rust 版](index.md) / [第 1 章](chapter01.md) / [Kotlin 版の第 2 章](../kotlin/chapter02.md) / [なでしこ3 版の第 2 章](../nadesiko/chapter02.md)
- [ADR-025 tiny_http を採用する](../../../adr/ADR-025-tiny-http.md)
