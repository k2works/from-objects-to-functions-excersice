---
type: Article
title: "第 6 章 コマンドを実行してイベントを生成する"
description: "Zettai 連載 Rust 版の第 6 章。コマンドとイベントを enum で分け、遷移表を match で書く。コンパイラが見るのは枝が揃っているかだけで、行き先が正しいかは見ない。ワイルドカードを禁じる lint が遷移表の形では発火しないことも実測で示す。"
tags: [article, zettai, rust, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-29T03:51:22Z }
---

# 第 6 章 コマンドを実行してイベントを生成する

第 5 章で、起きたことを畳み込んで状態を作りました。**起きたことは、誰が決めるのでしょうか。**

いまは HTTP のハンドラが決めています。「POST が来たら項目を足す」と書いてあるだけで、**足してよいかを誰も見ていません**。

この章で、**起こしたいこと**と**起きたこと**を分けます。

## 新しい ToDo リストの作成

リストを作るところから始めます。**作ろうとして断られる**ことがあるからです。

```rust
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ToDoListCommand {
    CreateList { list_name: ListName },
    AddItem { item: ToDoItem },
}
```

イベントとの違いは時制です。**コマンドは断られることがあり、イベントは断られません**（もう起きているため）。

断る理由も型にします。

```rust
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Rejected {
    /// 同じ名前のリストがもうある。
    ListAlreadyExists,
    /// リストがまだ無い。
    ListDoesNotExist,
}
```

**3 対象でここが一番違います。**

| 対象 | コマンドの表し方 | 断る理由 |
| :--- | :--- | :--- |
| Kotlin | sealed class | sealed class |
| なでしこ3 | 辞書の `種別` キー | 文字列 |
| **Rust** | **`enum`** | **`enum`** |

**書くものが 3 対象で一番少ない章です。** `enum` が言語にあり、`match` の網羅をコンパイラが見ます。

なでしこ3 版は「種別が知らない値だったら」を自分で書きました。ここには要りません。

## コマンドを使って状態を変更する

遷移を決めるのに、`ToDoList` の全部は要りません。**要るのは「いまどの状態か」だけ**です。

```rust
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ListState {
    Missing,
    Empty,
    HasItems,
}
```

これが遷移表の**行**になります。列はコマンド 2 つです。

## 状態とイベントによるドメインのモデリング

```rust
pub fn execute_in(
    state: ListState,
    command: ToDoListCommand,
) -> Result<Vec<ToDoListEvent>, Rejected> {
    match (state, command) {
        (ListState::Missing, ToDoListCommand::CreateList { list_name }) => {
            Ok(vec![ToDoListEvent::ListCreated { list_name }])
        }
        (ListState::Missing, ToDoListCommand::AddItem { .. }) => Err(Rejected::ListDoesNotExist),
        (ListState::Empty, ToDoListCommand::CreateList { .. }) => Err(Rejected::ListAlreadyExists),
        (ListState::Empty, ToDoListCommand::AddItem { item }) => {
            Ok(vec![ToDoListEvent::ItemAdded { item }])
        }
        (ListState::HasItems, ToDoListCommand::CreateList { .. }) => {
            Err(Rejected::ListAlreadyExists)
        }
        (ListState::HasItems, ToDoListCommand::AddItem { item }) => {
            Ok(vec![ToDoListEvent::ItemAdded { item }])
        }
    }
}
```

**これが遷移表そのものです。** 6 行あり、それが 3 × 2 のマスに 1 対 1 で対応します。

コマンドが通ればイベントが返り、断られれば理由が返ります。**状態は返りません。** 状態は第 5 章の畳み込みが作ります。

## 関数型ステートマシンを記述する

ここからが、この章で一番書きたかったところです。

### コンパイラは何を見るのか

なでしこ3 版は 16 マスを手で数えて、**穴を 1 つ見つけました**。Rust は `match` の網羅をコンパイラが見ます。**手で数える必要は無いのでしょうか。**

3 種類の穴を作って確かめました。

| 穴の種類 | 例 | コンパイラ | clippy |
| :--- | :--- | :--- | :--- |
| **マスが足りない** | `(HasItems, AddItem)` を書き忘れる | **止まる** | — |
| **ワイルドカードが隠す（単体の `enum`）** | `match state { _ => .. }` | 通る | **止められる** |
| **ワイルドカードが隠す（組・参照ごし）** | `match (state, command) { _ => .. }` | 通る | **通る** |
| **行き先が間違っている** | 無いリストに項目を足せてしまう | 通る | 通る |

マスが足りないときは止まります。

```text
error[E0004]: non-exhaustive patterns: `(State::HasItems, Command::AddItem)` not covered
help: ensure that all possible cases are being handled by adding a match arm with a wildcard
      pattern or an explicit pattern as shown
```

**この help が曲者です。** 「ワイルドカードを足せば handle できる」と言っています。足すと、**網羅の検査がそこで止まります**。

### つまずき: lint が遷移表の形では発火しない

ワイルドカードを禁じる lint があります。`clippy::wildcard_enum_match_arm` です。

```text
warning: wildcard match will also match any future added variants
help: try: `State::Empty | State::HasItems`
```

**これで守れる、と書こうとしました。** 実機で確かめたら、守れませんでした。

この lint が発火するのは、**単体の `enum` を直接 `match` したとき**だけです。遷移表は `match (state, command)` と**組で受ける**ので、発火しません。`Option<&T>` ごしでも発火しません。

つまり**遷移表という形にした瞬間、lint は効かなくなります。**

### 残るのは「行き先の誤り」だけではなかった

整理するとこうなります。

- **マスが足りない** → コンパイラが止める
- **ワイルドカードで隠す** → **誰も止めない**（遷移表の形では lint も効かない）
- **行き先が間違っている** → 誰も止めない

**2 種類の穴が残ります。** どちらも、2 対象と同じく**マスを数えてテストする**しかありません。

```rust
        assert_eq!(table.len(), 6, "状態 3 × コマンド 2 のマスが揃っている");
```

**数えることをやめられませんでした。** 型が守るのは「枝が揃っているか」までで、**「表として正しいか」は守りません。**

これが 2 対象に無い内容です。なでしこ3 版は「コンパイラが無いから数える」と書きました。Rust では「**コンパイラがあっても数える。守る範囲が違うから**」になります。

## ハブと接続する

ハブは判断を持ちません。**配線だけです。**

```rust
    pub fn handle(
        &self,
        user: &User,
        list_name: &ListName,
        command: ToDoListCommand,
    ) -> Result<ToDoList, Rejected> {
        let current = (self.fetch)(user, list_name);
        let events = execute_in(state_of_list(current.as_ref()), command)?;
```

`?` がここで初めて出ます。**断られたら、その理由のまま返します。**

第 9 章までリストを保管しているので、起きたことの並びが手元にありません。橋を架けます。

```rust
pub fn state_of_list(list: Option<&ToDoList>) -> ListState {
    match list {
        None => ListState::Missing,
        Some(list) if list.items.is_empty() => ListState::Empty,
        Some(_) => ListState::HasItems,
    }
}
```

HTTP 側は、理由を状態コードに写すだけです。

```rust
fn rejected(reason: Rejected) -> Reply {
    match reason {
        Rejected::ListDoesNotExist => not_found(),
        Rejected::ListAlreadyExists => Reply {
            status: 409,
            body: "<html><body><h1>409</h1></body></html>".to_string(),
        },
    }
}
```

**ここは `match` が守ります。** 理由を足すとコンパイルが止まります。単体の `enum` を直接 `match` しているので、**ワイルドカードを置いたら lint も止めます**。遷移表とは形が違います。

## コマンドとイベントを深く知る

コマンドとイベントを分けた効きめが、2 か所に出ました。

**1 つめ。断る理由がドメインの言葉になりました。** 以前は `Option` が `None` を返すだけで、「無いのか」「作れないのか」が区別できませんでした。いまは `Rejected` を `match` で分けられます。

**2 つめ。判断が 1 か所に集まりました。** 「足してよいか」は `execute_in` だけが知っています。HTTP のハンドラにも保管にもありません。

そして**判断が表の形をしているので、数えられます。** これが第 7 章に続きます。`Rejected` はまだ 2 つで、失敗の表し方としては貧しいままです。

## まとめ

- **`enum` が言語にあり、3 対象で書くものが一番少ない章でした。** なでしこ3 版が書いた「知らない種別だったら」は要りません
- **コマンドとイベントを時制で分けました。** コマンドは断られ、イベントは断られません
- **遷移表を `match` で書きました。** 6 行が 3 × 2 のマスに 1 対 1 で対応します
- **コンパイラが見るのは「枝が揃っているか」だけです。** 行き先が正しいかは見ません
- **ワイルドカードを禁じる lint は、遷移表の形では発火しませんでした。** 単体の `enum` を直接 `match` したときだけです。**「これで守れる」と書こうとして、実機で確かめて訂正しました**
- **数えることをやめられませんでした。** なでしこ3 版は「コンパイラが無いから数える」、Rust は「**コンパイラがあっても数える。守る範囲が違うから**」
- **断る理由を型にしたので、HTTP 側の `match` が漏れを止めます。** こちらは形が違うので lint も効きます

次の章で、失敗の表し方を作り直します。**`Result` は言語にあります。それでも判断は残るのでしょうか。**

---

## この章で書いたコード

- 実装: `apps/rust/zettai/zettai-step2-domain/src/lib.rs`、`apps/rust/zettai/zettai-step2-http/src/http.rs`
- スパイク: `apps/rust/zettai/spikes/match-exhaustiveness/`
- 制約の一覧: `apps/rust/zettai/README.md`

本文のコードは、上のファイルからの転記です。次の 3 種類だけは逐語の転記ではありません。

- コマンドの実行例と、その出力
- つまずきの説明のために、わざと壊したコード
- 比較のために引いた他言語のコード

## 参照

Uberto Barbini『From Objects to Functions』第 6 章。題材と章構成をこの本に拠っています。本連載のコードはすべて書き起こした自作実装で、原著のコードを転載したものではありません。

- [Zettai — Rust 版](index.md) / [第 5 章](chapter05.md) / [Kotlin 版の第 6 章](../kotlin/chapter06.md) / [なでしこ3 版の第 6 章](../nadesiko/chapter06.md)
- [ADR-015 段階ディレクトリ](../../../adr/ADR-015-step-directories.md) / [ADR-030 関数型 DI の形](../../../adr/ADR-030-functional-di-shape.md)
