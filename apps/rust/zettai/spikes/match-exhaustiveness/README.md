# スパイク: `match` の網羅は何を見るか（Unit 4 / スパイク 4）

なでしこ3 版は遷移表の 16 マスを手で数えて穴を 1 つ見つけた。
Rust はコンパイラが網羅を見る。**どこまで見るのかを確かめた。**

```bash
rustc --edition 2021 -A warnings failures/<name>.rs -o /tmp/x
cargo clippy   # wildcard_enum_match_arm を試す
```

## 結果

| 穴の種類 | 例 | コンパイラ | clippy |
| :--- | :--- | :--- | :--- |
| **マスが足りない** | `(HasItems, AddItem)` を書き忘れる | **止まる**（E0004） | — |
| **ワイルドカードが隠す** | `_ => Missing` を置く | **通る** | **止められる**（`wildcard_enum_match_arm`） |
| **行き先が間違っている** | 無いリストに項目を足せてしまう | **通る** | **通る** |

```text
error[E0004]: non-exhaustive patterns: `(State::HasItems, Command::AddItem)` not covered
```

**コンパイラが見るのは「枝が揃っているか」だけ。** 「その枝が正しいか」は見ない。

ワイルドカードは lint で禁じられるので、**残る穴は 1 種類（行き先の誤り）**になる。
そこは 2 対象と同じく、**マスを数えてテストする**しかない。
