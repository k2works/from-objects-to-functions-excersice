# スパイク: トランザクションの境界（Unit 6 / ゲート 1）

```bash
docker compose up -d zettai-db
cargo test                                          # 3 案とも green
rustc --edition 2021 failures/<name>.rs             # 落ちるほうを確かめる
```

**ロールバックのテストが green になるところまで**書いてから比べた
（[ADR-030](../../../../docs/adr/ADR-030-functional-di-shape.md) のときと同じ深さ）。

## 先に 2 つ落ちた

| 落ちた形 | エラー |
| :--- | :--- |
| **単位を値として返す**（`store.begin() -> Unit<'_>`） | **E0515** + **E0505**。`MutexGuard` とそれを借りる `Transaction` を同じ箱に入れると自己参照になる |
| **[ADR-011](../../../../docs/adr/ADR-011-transaction-boundary.md) の決定 2**（空の `TxContext` + ダウンキャスト） | **E0478**「lifetime bound not satisfied … but lifetime parameter must outlive the static lifetime」 |

```text
error[E0478]: lifetime bound not satisfied
   = note: but lifetime parameter must outlive the static lifetime
```

**`dyn Any` が `'static` を要求する。`Transaction<'a>` は `'static` ではない。**
Kotlin 版の「中身が空の文脈 + 使うときにダウンキャスト」は移植できない。
**3 案とも、文脈にドメインの動詞を持たせる形になる。**

## 3 案の実測

| | **A スコープ貸し（採用）** | B 引数で回す | C 遅延 `HubAction` |
| :--- | :--- | :--- | :--- |
| ロールバックのテスト | green | green | green |
| **ドメイン側のライフタイム注釈** | **0 箇所** | **0 箇所** | **8 箇所** |
| ドメイン側の行数 | 27 | 22 | 54 |
| 新しい概念 | trait 2 | trait 1 | trait 1 + 型別名 1 + 関数 4 |
| **`commit` を呼ぶ場所** | **ドメインの中（見えない）** | **呼び手（毎回書く）** | runner の中 |
| clippy | `type_complexity` 1 件 | 無し | 無し |

**案 A を採った。** ドメインのライフタイム注釈が 0 のまま、境界が 1 関数に見える。
案 C は同じことを 8 箇所の `'a` と倍の行数で買うことになる。
案 B は `commit` の呼び忘れが静かに通る（第 10 章の主題が薄まる）。
