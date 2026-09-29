---
type: ADR
title: "ADR-034 トランザクションの境界を区間として貸す"
description: "Zettai 連載 Rust 版でトランザクションの境界を決める決定。単位を値として返せないこと、ADR-011 の決定 2（空の文脈 + ダウンキャスト）が型システムに拒まれること、代わりに実行時検査が要らなくなったことを実測とともに記録する。"
tags: [adr, rust, zettai]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-29T06:50:32Z }
---

# ADR-034 トランザクションの境界を区間として貸す

日付: 2026-09-29

## ステータス

2026-09-29 提案されました

## コンテキスト

第 10 章で「読む → 判断する → 書く」を 1 つのトランザクションにします。**3 対象で共通の主題**です。

| 対象 | 解いた形 |
| :--- | :--- |
| Kotlin | `ContextReader<TxContext, T>`。**文脈は中身が空**で、使うときにダウンキャスト（[ADR-011](ADR-011-transaction-boundary.md)） |
| なでしこ3 | 文脈を「保存先の指し先」という値にした。`ContextReader` は移植できなかった（[ADR-021](ADR-021-context-as-path.md)） |
| **Rust** | **これから決める** |

**第 9 章までの形では半分残ります。** 実測しました。

```text
assert_eq!(left.len(), 1, "半分だけ残っている");   // green
```

### 先に 2 つ落ちた

**1. 単位を値として返す形**（Kotlin 版の `runInTransaction` を素直に写したもの）

```text
error[E0515]: cannot return value referencing local variable `guard`
error[E0505]: cannot move out of `guard` because it is borrowed
```

`MutexGuard<Client>` と、それを借りる `Transaction` を同じ箱に入れると**自己参照**になります。

**2. [ADR-011](ADR-011-transaction-boundary.md) の決定 2**（中身が空の文脈 + ダウンキャスト）

```text
error[E0478]: lifetime bound not satisfied
   = note: but lifetime parameter must outlive the static lifetime
```

`dyn Any` は `'static` を要求し、`Transaction<'a>` は `'static` ではありません。

### 3 案を測った

`spikes/tx-shapes/` に書き、**ロールバックのテストが green になるところまで**進めてから比べました（[ADR-030](ADR-030-functional-di-shape.md) と同じ深さ）。

| | **A 区間として貸す** | B 引数で回す | C 遅延させる |
| :--- | :--- | :--- | :--- |
| ロールバックのテスト | green | green | green |
| **ドメイン側のライフタイム注釈** | **0** | **0** | **8** |
| ドメイン側の行数 | 27 | 22 | 54 |
| 新しい概念 | trait 2 | trait 1 | trait 1 + 型別名 1 + 関数 4 |
| **`commit` を呼ぶ場所** | **ドメインの中** | **呼び手** | runner |
| clippy | `type_complexity` 1 件 | 無し | 無し |

**3 案とも書けました。** リスク台帳の「書けないなら書けないことを章の主題にする」は使いませんでした。

## 決定

**トランザクションを値として返さず、区間として貸します。**

1. `TransactionalStore::in_transaction(work)` が単位を貸す。**戻り値を `ToDoList` に固定**する（オブジェクト安全のため）
2. 単位の中で触れる口を `TxEventStore` とする。**`postgres` を知らない**
3. `handle_in_transaction` が境界。**読む・判断する・書くが同じ区間にある**
4. **`commit` はドメインに出さない。** 呼び忘れが起こりえない形にする
5. **文脈にドメインの動詞を持たせる。** 中身を空にはしない
6. ライフタイムはアダプタ（`PgTx<'a>`）に閉じる

### 検討した代替案

| 案 | 採らなかった理由 |
| :--- | :--- |
| B 引数で回す | いちばん小さい（trait 1・22 行）が、**`commit` が呼び手に散る**。呼び忘れが静かに通る。**この章の主題（途中で失敗したら残らないことを示す）が薄まる** |
| C 遅延させる（`HubAction`） | Kotlin 版との対比は鮮明だが、**ドメインに `'a` が 8 箇所出る**。行数が倍、概念が 4 つ増える |
| 単位を値として返す | **書けない**（E0515・E0505） |
| 空の文脈 + ダウンキャスト（[ADR-011](ADR-011-transaction-boundary.md) 決定 2） | **書けない**（E0478） |

## 影響

- **Kotlin 版の実行時検査が要らなくなりました。** あちらは文脈を空にした代償に `require(context is JdbcContext)` を書いています。**文脈に動詞を持たせたので、間違った文脈を渡せません**
- **`commit` を書かないほうが安全**です。`Transaction` は drop でロールバックします。Kotlin 版の `try`/`catch` に相当するコードがありません
- ドメインのクレートは**依存 0 のまま**です（[ADR-027](ADR-027-crate-boundary.md)）
- ドメイン側のライフタイム注釈は **3 箇所**（`UnitOfWork<'a>` の型別名と `in_transaction` の `'_`）。スパイクの 0 から増えたのは、clippy の `type_complexity` を消すために型別名を置いたためです
- [ADR-032](ADR-032-postgres-event-store.md) の「解かないこと: トランザクションの境界」がここで閉じます
- `&dyn Fn` が [ADR-030](ADR-030-functional-di-shape.md) の 2 形に加わりました（下記）

### [ADR-030](ADR-030-functional-di-shape.md) への追補

関数値の形が 3 つになりました。判断の基準は変わりません。

| その関数値は | 形 | 例 |
| :--- | :--- | :--- |
| 配線のときに 1 つ決まるだけ | `impl Fn`（型引数） | `ToDoListHub<F, S>` |
| 並びに入れて畳み込む | `Box<dyn Fn>` | `Transform`（第 5 章） |
| **その場で 1 回だけ渡す（持ち回らない）** | **`&dyn Fn`** | **`UnitOfWork`（第 10 章）** |

`&dyn Fn` は**所有しません**。呼ばれている間だけ生きていればよいので、`Box` にする理由がありません。

### この決定が解かないこと

| 解かないこと | どうするか |
| :--- | :--- |
| **単位の戻り値が `ToDoList` に固定されている** | `<T>` を入れるとオブジェクト安全でなくなり、`&dyn TransactionalStore` で受けられません。**射影は畳み込み直しています**。型が増えたら見直します |
| 単位を跨ぐ処理 | いまは 1 コマンドが 1 単位です（[ADR-011](ADR-011-transaction-boundary.md) の決定 1 を踏襲）。**複数コマンドをまとめる話は連載の範囲外**です |
| 並行して捌くとき | `Mutex<Client>` 1 本です。接続プールは扱いません |
| 読み出しの分離レベル | 既定のままです。**繰り返し読みの一貫性は確かめていません** |

## コンプライアンス

- ドメインの `Cargo.toml` に `postgres` が現れないこと
- ドメインのコードに `commit` が現れないこと
- `in_transaction` を通さずに `append` を呼ぶ経路が、ドメインに無いこと
- **「途中で失敗したら 1 件も残らない」テストが green であること**
