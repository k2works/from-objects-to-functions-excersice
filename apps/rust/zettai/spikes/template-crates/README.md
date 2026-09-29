# スパイク: テンプレート機構に既製品を入れるか（Unit 6 / ゲート 3）

## 測った条件（先に決めた）

1. 空のクレートから始める
2. `cargo add` したあと `cargo clean` してからビルドする
3. 各案とも同じ手順

## 結果

| | 自前 | `askama` | **`tera`（採用）** | `minijinja` |
| :--- | :--- | :--- | :--- | :--- |
| 依存クレート | +0 | **+18** | **+3** | +4 |
| 依存のビルド | 0 秒 | **28.56 秒** | **18.42 秒** | 23.53 秒 |
| **未適用のタグ** | 自分で書く | **コンパイル時に止まる** | **既定で `Err`** | 既定は空文字。`Strict` で `Err` |

## Kotlin 版の理由は成り立たなかった

[ADR-010](../../../../docs/adr/ADR-010-own-template.md) が自前を選んだ理由は
「どれも例外を投げる形で `Outcome` を返さない」だった。**Rust では違う。**

```text
tera: Err(Error { kind: RenderingError(... message: "Variable `forgotten` is not defined.
      Available variables: name" ...) })
minijinja（Strict）: Err("undefined value (in t:1)")
```

`tera` は**既定で `Result` を返し、変数名を挙げる**。

`askama` はさらに強く、**コンパイル時に止まる**。

```text
error[E0609]: no field `forgotten` on type `&ListPage`
```

## 採った判断

**`tera` を入れた。** 要求（未適用のタグを失敗にする）を既定で満たす既製品が
あるのに自作すると、**理由のない自作**になる。連載の基準（既製品を先に調べる）に
反する。

`askama` は要求より強いが、依存 +18・ビルド +28.56 秒。Unit 5 の学び
（**依存を足すと `check` も遅くなる。フィーチャで隠せるのはテストの実行だけ**）が
そのまま効く。
