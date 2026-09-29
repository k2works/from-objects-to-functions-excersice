---
type: Article
title: "第 10 章 コンテキストを読み込み、コマンドを処理する"
description: "Zettai 連載 Rust 版の第 10 章。トランザクションの境界を所有権のもとで表す。文脈を値として持てないので区間として貸す。Kotlin 版の設計が型システムに拒まれ、代わりに実行時検査が要らなくなった話。"
tags: [article, zettai, rust, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-29T06:50:32Z }
---

# 第 10 章 コンテキストを読み込み、コマンドを処理する

第 9 章でイベントを保存しました。**保存は「読む → 判断する → 書く」の 3 つに分かれています。**

```rust
    let before = store.events_of(user, list_name)?;
    let new_events = crate::execute(user, list_name, &before, command)?;
    store.append(user, list_name, &new_events)?;
```

`?` が 3 回並んでいます。**どれか 1 つが失敗すると、半分だけ残ります。**

## モナドでデータベースにアクセスする

まず、本当に半分残るのかを確かめました。

```rust
    assert_eq!(
        left.len(),
        1,
        "**半分だけ残っている。** これが第 10 章の出発点"
```

残ります。`append` は 1 回ごとに別のトランザクションなので、2 回目の前で落ちると 1 回目が確定しています。

やりたいことは 1 つです。**3 つを 1 つの単位にする。**

## ContextReader を使用したコマンドの処理

Kotlin 版はここで `ContextReader<TxContext, T>` を作りました。**文脈を受け取って結果を返す、遅延した計算**です。`TxContext` は中身が空で、使うときにダウンキャストして接続を取り出します。

**その設計を、まず素直に写しました。**

### つまずき 1: 単位を値として返せない

いちばん素直な形はこれです。

<!-- code-check: ignore つまずきの説明のために、わざと落ちるコードを書いている -->

```rust
struct Unit<'a> {
    guard: MutexGuard<'a, Client>,
    tx: Transaction<'a>,
}

impl Store {
    fn begin(&self) -> Result<Unit<'_>, postgres::Error> {
        let mut guard = self.client.lock().unwrap();
        let tx = guard.transaction()?;
        Ok(Unit { guard, tx })
    }
}
```

```text
error[E0515]: cannot return value referencing local variable `guard`
error[E0505]: cannot move out of `guard` because it is borrowed
```

**ロックと、そのロックの中身を借りたトランザクションを、同じ箱に入れています。** 自己参照です。片方が動くともう片方の参照先が消えるので、安全な Rust では書けません。

### つまずき 2: 中身が空の文脈を作れない

`ContextReader` の肝は、**文脈が何であるかをドメインが知らない**ことです。Kotlin はそれを空のインターフェースで表しました。

Rust で「中身が空 + 使うときに取り出す」は `dyn Any` になります。

<!-- code-check: ignore つまずきの説明のために、わざと落ちるコードを書いている -->

```rust
trait TxContext: Any {
    fn as_any(&self) -> &dyn Any;
}

struct PgContext<'a> {
    tx: Transaction<'a>,
}

impl<'a> TxContext for PgContext<'a> {
    fn as_any(&self) -> &dyn Any { self }
}
```

```text
error[E0478]: lifetime bound not satisfied
   = note: but lifetime parameter must outlive the static lifetime
```

**`dyn Any` は `'static` を要求します。** トランザクションは接続を借りているので `'static` ではありません。

**[ADR-011](../../../adr/ADR-011-transaction-boundary.md) の決定 2 は移植できませんでした。**

なでしこ3 版も `ContextReader` を移植できませんでした。理由は「関数値ごしの非同期が待たれない」で、**言語に何かが足りないから**でした。Rust では逆です。**言語が持っている規則に拒まれています。**

## トランザクションの境界を決める

写せないと分かったので、3 案を書きました。**ロールバックのテストが green になるところまで**書いてから比べています。第 4 章で DI の形を決めたときと同じ手です（[ADR-030](../../../adr/ADR-030-functional-di-shape.md)）。

| | **A 区間として貸す** | B 引数で回す | C 遅延させる |
| :--- | :--- | :--- | :--- |
| ロールバックのテスト | green | green | green |
| **ドメイン側のライフタイム注釈** | **0** | **0** | **8** |
| ドメイン側の行数 | 27 | 22 | 54 |
| **`commit` を呼ぶ場所** | **ドメインの中** | **呼び手** | runner |

**3 案とも書けました。** 「書けないなら書けないことを主題にする」つもりでしたが、その必要はありませんでした。

決め手は 2 つです。

**1 つめは、ドメインにライフタイムが出るかどうか。** 案 C は `'a` が 8 箇所出ます。Kotlin 版との対比は鮮明になりますが、**ドメインの署名が借用の話で埋まります**。

**2 つめは、`commit` を誰が呼ぶか。** 案 B は呼び手が書きます。

```rust
    b_argument::handle(&store, &mut tx, "k", "x").expect("通る");
    tx.commit().expect("確定できる");
```

**呼び忘れると、静かにロールバックします。** テストではそれを利用して green にできました。動くコードとしては正しいのですが、**この章の主題は「途中で失敗したら残らない」を示すこと**なので、呼び手に委ねると主題が薄まります。

**案 A を選びました。**

```rust
pub trait TransactionalStore {
    fn in_transaction(&self, work: UnitOfWork<'_>) -> Result<ToDoList, ZettaiError>;
}
```

```rust
pub fn handle_in_transaction(
    store: &dyn TransactionalStore,
    user: &User,
    list_name: &ListName,
    command: crate::ToDoListCommand,
) -> Result<ToDoList, ZettaiError> {
    store.in_transaction(&|tx| {
        let before = tx.events_of(user, list_name)?;
        let new_events = crate::execute(user, list_name, &before, command.clone())?;
        tx.append(user, list_name, &new_events)?;
        Ok(crate::replay([before, new_events].concat()))
    })
}
```

**境界がこの関数に見えます。** 読む・判断する・書くが同じ区間にあり、`commit` は出てきません。

## 設計を 2 回間違えた

Kotlin 版のこの節は、著者が設計を 2 回やり直した記録です。**この版では、設計の間違いは 0 回でした。**

理由は 2 つあります。

**1 つめは、既知の制約を設計の前に読んだことです。** なでしこ3 版が第 10 章で言っていたのと同じです。ただし効いた中身が違います。読んだのは「`Box<dyn Fn>` は既定で `'static`」（第 5 章）と「`dyn Trait` の主トレイトのメソッドは import 不要」（第 7 章）で、**どちらもライフタイムと `dyn` の話**でした。

**2 つめは、コンパイラが間違いを設計する前に止めたことです。** Kotlin 版の 1 回目の誤りは `null` を `Connection` にキャストして NPE を出したものでした。**Rust では書けません。**

### 代わりに得たもの

Kotlin 版は、文脈を空にした代償として**実行時の検査**を書いています。

<!-- code-check: ignore 比較のために引いた Kotlin 版のコード -->

```kotlin
require(context is JdbcContext)
```

**Rust では文脈を空にできませんでした。** だから文脈にドメインの動詞を持たせました。

```rust
pub trait TxEventStore {
    fn events_of(...) -> Result<Vec<ToDoListEvent>, ZettaiError>;
    fn append(...) -> Result<(), ZettaiError>;
}
```

**この形なら、間違った文脈を渡せません。** `require` に相当するものが要らなくなりました。

**「移植できなかった」で終わりませんでした。** 移植できなかった結果、Kotlin 版が実行時に確かめていたことが、型で確かめられるようになっています。

これが 2 対象に無い内容です。なでしこ3 版は「文脈が無いから自分で決めた」、Kotlin 版は「文脈を空にして実行時に確かめた」、Rust は「**空にできなかったので、埋めたら検査が要らなくなった**」です。

### ライフタイムはどこへ行ったか

消えたわけではありません。**アダプタに閉じ込めました。**

```rust
    fn in_transaction(&self, work: UnitOfWork<'_>) -> Result<ToDoList, ZettaiError> {
        let mut client = self.client.lock().expect("毒されていない");
        let tx = client.transaction().map_err(|e| unavailable(&e))?;
        let pg = PgTx {
            tx: RefCell::new(tx),
            table: self.table.clone(),
        };
        let result = work(&pg)?;
        pg.tx.into_inner().commit().map_err(|e| unavailable(&e))?;
        Ok(result)
    }
```

`PgTx<'a>` の `'a` はこのブロックの中だけです。`&dyn TxEventStore` として渡すので、**ドメイン側では借用が型から消えます**。

**「押された先が正しい」の 5 件目**です。所有権が「値として返す」を禁じ、押された先が「区間として貸す」でした。**そちらのほうが、`commit` の呼び忘れが起こりえない形です。**

### `commit` を書かないほうが安全

`Transaction` は **drop でロールバック**します。`commit` を呼んだときだけ確定します。

Kotlin 版は `try`/`catch` で「失敗したら戻す」を書きました。**このコードには書いてありません。** 失敗して早期に返ると `pg` が落ち、`Transaction` が落ち、ロールバックされます。

**書かなかったほうが安全**という逆転が起きています。

## データベースに対する射影のクエリ

読む側も同じ区間を通します。

```rust
pub fn summary_in_transaction(
    store: &dyn TransactionalStore,
    user: &User,
    list_name: &ListName,
) -> Result<crate::projection::ListSummary, ZettaiError> {
```

第 8 章でコマンド側とクエリ側は**型で分けました**。保管との付き合い方は同じです。

## イベントソーシングによるドメインのモデリング

ここまでで揃ったものを並べます。

| 要素 | 章 | Rust では |
| :--- | :--- | :--- |
| 起きたこと | 5 | `enum ToDoListEvent` |
| 畳み込み | 5 | `Box<dyn Fn>` の合成（モノイド） |
| 指示 | 6 | `enum ToDoListCommand` |
| 遷移表 | 6 | `match (state, command)` |
| 失敗 | 7 | `Result<T, ZettaiError>` |
| 射影 | 8 | 別の型（CQRS） |
| 保管 | 9 | イベントを 1 テーブルに追記 |
| **境界** | **10** | **区間として貸す** |

**状態はどこにも保存されていません。** 毎回イベントから作ります。

## まとめ

- **「読む → 判断する → 書く」を 1 つの単位にしました。** 途中で失敗したら 1 件も残りません
- **単位を値として返せませんでした。** ロックとそれを借りたトランザクションは自己参照になります（E0515・E0505）
- **[ADR-011](../../../adr/ADR-011-transaction-boundary.md) の決定 2 は移植できませんでした。** `dyn Any` が `'static` を要求します（E0478）
- **代わりに、Kotlin 版の実行時検査が要らなくなりました。** 文脈を空にできなかったので動詞を持たせ、結果として間違った文脈を渡せなくなっています
- **3 案とも書けました。** 「書けないことを主題にする」用意はしていましたが、使いませんでした
- **決め手はライフタイム注釈の数（0 対 8）と `commit` を誰が呼ぶか**でした
- **設計の間違いは 0 回でした。** 既知の制約を先に読んだことと、コンパイラが設計の前に止めたことの両方が効いています
- **`commit` を書かないほうが安全**という逆転が起きました。Drop が既定でロールバックします
- **制約が設計を押した 5 件目**です。押された先は、呼び忘れが起こりえない形でした

次の章で、複数のエラーをまとめて返します。

---

## この章で書いたコード

- 境界: `apps/rust/zettai/zettai-step5-domain/src/store.rs`
- アダプタ: `apps/rust/zettai/zettai-step5-http/src/event_store.rs`
- 結合テスト: `apps/rust/zettai/zettai-step5-http/tests/persistence.rs`
- スパイク: `apps/rust/zettai/spikes/tx-shapes/`
- 制約の一覧: `apps/rust/zettai/README.md`

本文のコードは、上のファイルからの転記です。次の 3 種類だけは逐語の転記ではありません。

- コマンドの実行例と、その出力
- つまずきの説明のために、わざと壊したコード
- 比較のために引いた他言語のコード

## 参照

Uberto Barbini『From Objects to Functions』第 10 章。題材と章構成をこの本に拠っています。本連載のコードはすべて書き起こした自作実装で、原著のコードを転載したものではありません。

- [Zettai — Rust 版](index.md) / [第 9 章](chapter09.md) / [Kotlin 版の第 10 章](../kotlin/chapter10.md) / [なでしこ3 版の第 10 章](../nadesiko/chapter10.md)
- [ADR-011 トランザクションの境界](../../../adr/ADR-011-transaction-boundary.md) / [ADR-027 クレート境界](../../../adr/ADR-027-crate-boundary.md) / [ADR-030 関数型 DI の形](../../../adr/ADR-030-functional-di-shape.md)
