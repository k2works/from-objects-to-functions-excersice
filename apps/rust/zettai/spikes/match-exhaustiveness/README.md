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
| **ワイルドカードが隠す（単体の enum）** | `match state { _ => .. }` | **通る** | **止められる**（`wildcard_enum_match_arm`） |
| **ワイルドカードが隠す（組・参照ごし）** | `match (state, command) { _ => .. }` | **通る** | **通る**（lint が発火しない） |
| **行き先が間違っている** | 無いリストに項目を足せてしまう | **通る** | **通る** |

```text
error[E0004]: non-exhaustive patterns: `(State::HasItems, Command::AddItem)` not covered
```

**コンパイラが見るのは「枝が揃っているか」だけ。** 「その枝が正しいか」は見ない。

`wildcard_enum_match_arm` は**単体の enum を直接 match したときだけ**発火する。
**遷移表は組（`(State, Command)`）で受けるので発火しない。**
`Option<&T>` ごしでも発火しない（`src/wildcard_check.rs` で確認）。

つまり**守ってくれるのは「枝が揃っているか」だけ**で、
表の形にした瞬間、ワイルドカードを置かれても誰も止めない。

2 対象と同じく、**マスを数えてテストする**しかない。
