# スパイク: 複数のエラーをどの型で集めるか（Unit 6 / ゲート 2）

```bash
cargo test
```

いまの形は**最初の失敗で止まる**。

```text
assert_eq!(current("", TOO_LONG), Err(ZettaiError::EmptyUser));   // green
```

「利用者名が空 かつ 新しい名前が 41 文字」でも理由が 1 つしか返らない。

## 3 案

| | A `Multiple` を足す | **B 別の型 `Validated<T>`（採用）** | C `Result<T, Vec<..>>` |
| :--- | :--- | :--- | :--- |
| 両方の理由が返るか | 返る | 返る | 返る |
| 行数 | 28 | **68** | **18** |
| 新しい型 | 0 | **1** | 0 |
| **既存の `match` が止まるか** | **止まる**（E0004） | 止まらない | 止まらない |
| `?` がそのまま通るか | 通る | 境界で `into_result()` | **通らない**（E0277） |
| **合わせる操作を書くか** | 不要 | **`zip`（アプリカティブ）** | 不要 |

```text
error[E0277]: `?` couldn't convert the error to `E`
              the trait `From<Vec<E>>` is not implemented for `E`
```

**案 B を採った。** 案 A と C でも理由は 2 つ返るが、**「合わせる」操作が出てこない**。
`zip` を書かないと、アプリカティブの法則を確かめる対象が無い。

`Result` は「最初で止まる」、`Validated` は「溜める」。**使い分けが型に出る。**

## 計画に書いた具体例が実物と合っているか

```rust
        assert_eq!(TOO_LONG.chars().count(), 41);
        assert!(check_user("").is_err());
        assert!(check_name(TOO_LONG).is_err());
```

なでしこ3 版はここで手戻りした（「空でない・40 文字以内」は、**空文字が 40 文字以内なので同時にだめにならない**）。
**計画の時点で実物を書き、スパイクで確かめた。**
