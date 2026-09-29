# スパイク: 関数型 DI の形（Unit 3 / ゲート 1・スパイク 1・4）

**第 4 章だけを見て決めると第 5 章で書き直しになる**ので、3 案それぞれで
「状態から状態への関数を合成してモノイドにする」ところまで書いた。

```bash
cargo test                                   # 3 案とも通る（第 4 章の範囲）
rustc --edition 2021 failures/<name>.rs      # 落ちるほうを確かめる
```

## 結果

| | 案 A `impl Fn` | 案 B `Box<dyn Fn>` | 案 C ジェネリクス + トレイト |
| :--- | :--- | :--- | :--- |
| 依存を差し替える（第 4 章） | できる | できる | できる |
| 2 つの変換を合成する | できる | できる | できる |
| **長さが実行時に決まる列を畳み込む（第 5 章）** | **できない** | **できる** | **できない** |
| ディスパッチ | 静的 | 動的 | 静的 |

**案 A と案 C は第 5 章で止まる。** どちらも同じ理由で、**合成の結果が別の型になる**ため、
畳み込みの途中経過を 1 つの型に揃えられない。

- `failures/a_heterogeneous.rs` — `impl Fn` は使うたびに別の opaque type。
  エラーが `expected opaque type` と `found opaque type` を**同じ文字列で**並べる
- `failures/c_runtime_length.rs` — `fold` の途中で `Identity` が `Composed<Identity, Add>` になる

**案 B だけが `fold` の中で型が変わらない。** 単位元 `identity()` から始めて合成を繰り返せる
（`b_folds_a_sequence` / `b_identity_is_a_unit`）。

## スパイク 4: 借用のもとで合成できるか

`Box<dyn Fn>` は**既定で `+ 'static`** なので、借りた値を閉じ込められない。

- `failures/d_borrowed_capture.rs` — `returning this value requires that `'1` must outlive `'static``
- `failures/d_owned_capture.rs` — **借りずに所有する**と通る

コンパイラは「型別名に `'a` を足す」も提案するが、**イベントが自分の値を持つほうが
イベントソーシングの形に合う**（保存して後から畳み込むので、借り先が先に消える）。
