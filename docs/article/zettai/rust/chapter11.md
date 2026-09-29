---
type: Article
title: "第 11 章 アプリカティブによるデータバリデーション"
description: "Zettai 連載 Rust 版の第 11 章。複数の理由を集める型を作り、アプリカティブ則を性質テストで確かめる。テンプレートは 3 対象で初めて既製品に寄せた。Kotlin 版の自作理由が Rust では成り立たない話。"
tags: [article, zettai, rust, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-29T07:44:20Z }
---

# 第 11 章 アプリカティブによるデータバリデーション

第 10 章で境界を作りました。**画面はまだ第 2 章のままです。**

この章で、リストの名前を変えられるようにします。そこで 2 つ足りないものが出ます。**複数の理由を返せないこと**と、**値を無害化していないこと**です。

## ToDo リストの名前変更

フォームから新しい名前を送ります。名前には規則があります。

- 空でない
- 40 文字以内
- 記号を含まない（`<` `>` `&` `"` `'` `/`）

`Result` で書くと、こうなります。

```rust
        assert_eq!(current("", TOO_LONG), Err(ZettaiError::EmptyUser));
```

**理由が 1 つしか返りません。** `?` は最初の失敗で止まります。それがモナドの性質です。

## 2 つのパラメータを持った検証

### つまずき 1: 計画に書いた具体例が作れなかった

計画にはこう書いていました。

> 利用者名が空 かつ 新しいリスト名が 41 文字 → 理由が 2 つ返る

**HTTP 経路では作れませんでした。** 利用者名はパスから来ます。

```text
/todo//book/rename
```

空の部分は落ちるので、パスは 3 要素になり、`/rename` の経路にたどり着きません。**ドメインを直接呼ぶ経路でしか再現できない条件でした。**

なでしこ3 版も同じ形で手戻りしています。あちらは「空でない・40 文字以内」と書き、**空文字は 40 文字以内なので同時にだめにならない**ことに実装中に気づきました。

**今回は「両方だめな入力」を計画に実物で書いていました。** それでも足りませんでした。**書いた入力が、すべての経路で作れるか**まで確かめる必要がありました。

置き直したのがこれです。**1 つの値に 3 つの規則をかけます。**

```text
"<script>" を 6 回 → 48 文字 かつ 記号を含む → 理由が 2 つ
```

## バリデーションを使った検証

理由を溜める型を作ります。

```rust
    pub fn zip<U>(self, other: Validated<U>) -> Validated<(T, U)> {
        match (self, other) {
            (Validated::Valid(a), Validated::Valid(b)) => Validated::Valid((a, b)),
            // **両方だめなら両方返す。** ここがこの型の存在理由
            (Validated::Invalid(a), Validated::Invalid(b)) => Validated::Invalid([a, b].concat()),
            (Validated::Invalid(a), _) => Validated::Invalid(a),
            (_, Validated::Invalid(b)) => Validated::Invalid(b),
        }
    }
```

**2 行目と 3 行目の差がすべてです。** どちらも失敗なら連ね、片方だけなら片方を返します。

使う側はこうなります。

```rust
pub fn valid_new_name(name: &str) -> Validated<crate::ListName> {
    not_empty(name)
        .zip(within_limit(name))
        .zip(without_markup(name))
        .map(|_| crate::ListName::new(name))
}
```

### なぜ別の型にしたのか

**3 案を書きました。どれでも理由は 2 つ返ります。**

| | A `Multiple` を足す | **B 別の型（採用）** | C `Result<T, Vec<..>>` |
| :--- | :--- | :--- | :--- |
| 行数 | 28 | **68** | **18** |
| 既存の `match` が止まるか | **止まる** | 止まらない | 止まらない |
| `?` がそのまま通るか | 通る | 境界で戻す | **通らない** |
| **合わせる操作を書くか** | 不要 | **`zip`** | 不要 |

**結果が同じなのに型を分けたのは、「合わせる」操作が出てくるからです。**

案 A と案 C は `if` を並べて `Vec` に push するだけです。**`zip` を書かないと、法則を確かめる対象がありません。**

そして `Result` と役割が分かれます。

| 型 | ふるまい |
| :--- | :--- |
| `Result` | 最初の失敗で止まる（モナド） |
| `Validated` | **理由を溜める**（アプリカティブ） |

案 C には別の問題もありました。

```text
error[E0277]: `?` couldn't convert the error to `E`
              the trait `From<Vec<E>>` is not implemented for `E`
```

**第 10 章の境界が書き換わります。** あそこは `?` を 3 回使っています。

## アプリカティブファンクタの結合

法則を確かめます。**恒等・結合律・理由が落ちないこと**の 3 つです。

```rust
#[test]
fn zipping_is_associative() {
    let mut rng = Rng::new(31415926);
    for _ in 0..TRIALS {
        let (a, b, c) = (any(&mut rng), any(&mut rng), any(&mut rng));

        let left = a
            .clone()
            .zip(b.clone())
            .zip(c.clone())
            .map(|((x, y), z)| (x, y, z));
        let right = a.zip(b.zip(c)).map(|(x, (y, z))| (x, y, z));

        assert_eq!(left, right);
    }
}
```

### モナドとの差を数字で示す

**この型の存在理由は「両方だめな入力があること」に尽きます。** 片方だけなら、モナドでも同じ結果になります。

数えました。

```text
両方だめ 46 / 200、モナドが理由を落とした 46 / 200
```

**1 対 1 で対応しています。** モナドが理由を落とすのは、両方だめなときだけです。

計算と並べます。

```text
失敗は 1/2 で引くので、両方だめは 1/4
期待値 = 200 × 0.25 = 50、σ = √(200 × 0.25 × 0.75) = 6.12
```

実測 46 は期待値から **0.65σ 下**です。

壊したときの検出率も測りました。`zip` が片方の理由を捨てるようにします。

```text
壊した zip の検出 56 / 200
```

**+0.98σ** です。しきい値は期待値から 4σ 下（25）に置いています。

**200 回だから安定して捕まえられます。** 20 回なら期待値 5 で、**1 件以下に留まる確率が 2.43%** あります。

## ユーザインタフェースの改善

画面にエラーを並べます。ここで 2 つめの足りないものが出ます。**第 10 章まで、値を無害化していませんでした。**

<!-- code-check: ignore 第 10 章までの形。この章で置き換える -->

```rust
    format!("<li>{}</li>", item.description)
```

リスト名に `<script>` を入れられます。

### 既製品を調べたら、自作の理由が消えた

2 対象はここでテンプレート機構を**自作**しました。Kotlin 版の理由はこれです（[ADR-010](../../../adr/ADR-010-own-template.md)）。

> どれも例外を投げる形で、`Outcome` を返しません。

**Rust では成り立ちませんでした。**

| | 自前 | `askama` | **`tera`** | `minijinja` |
| :--- | :--- | :--- | :--- | :--- |
| 依存クレート | +0 | **+18** | **+3** | +4 |
| 依存のビルド | 0 秒 | 28.56 秒 | **18.42 秒** | 23.53 秒 |
| **未適用のタグ** | 自分で書く | **コンパイル時に止まる** | **既定で `Err`** | 既定は空文字 |

```text
tera: Err(Error { kind: RenderingError(... message: "Variable `forgotten` is not defined.
      Available variables: name" ...) })
```

**`tera` は既定で `Result` を返し、足りない変数の名前まで挙げます。**

`askama` はさらに強く、コンパイル時に止まります。

```text
error[E0609]: no field `forgotten` on type `&ListPage`
```

**`tera` を入れました**（[ADR-036](../../../adr/ADR-036-tera.md)）。

**3 対象で初めて、既製品に寄せる判断になりました。** 第 3 章の `cucumber`、第 5 章の `proptest`、第 7 章の `thiserror`、第 9 章の `serde_json` は、どれも**数字が合わなかったから**入れませんでした。**`tera` は合いました。**

要求を既定で満たす既製品があるのに自作すると、**理由のない自作**になります。

### つまずき 2: 既製品を入れても、既定が安全とは限らない

入れてすぐテストが落ちました。

```text
生のタグが残っている: <html><body><h1>book</h1><ul>
<li><script>alert(1)</script></li>
```

**`tera` の自動エスケープは、テンプレート名の拡張子で決まります。** `"list"` という名前で登録すると効きません。`"list.html"` にして通りました。

**「既製品を入れたから安全」ではありませんでした。** 安全になる設定を、入れてから確かめる必要があります。

### つまずき 3: フォームの値がパーセントエンコードされていた

`tiny_http` が渡すボディを**実物で**確かめました。

```text
body="user=&newname=%E8%AA%AD%E3%82%80+%E6%9C%AC%26%22%3Cx%3E%22"
```

**第 4 章から使っている `description_in` は `+` しか戻していませんでした。**

```text
Some("%E8%AA%AD%E3%82%80 %E6%9C%AC%26%22%3Cx%3E%22")
```

ASCII だけの説明（`review chapter`）を使っていたので、**受け入れテストは通り続けていました**。

第 9 章の `jsonb` と同じ形です。**自分が書いた文字列だけで往復させると、相手が返す形を試していないことになります。** 2 件目です。

値の中の `&` は `%26` で届くので、**分割してから戻す**順序で正しいことも確かめました。

### `/rename` 以外への POST

なでしこ3 版が踏んだ欠陥を、先に潰しました。

```rust
    // **`/rename` で終わるパスかどうかを先に見る。**
    // なでしこ3 版は前方一致だけで振り分け、`/rename` 以外への POST まで
    // 名前変更として処理していた（Unit 6 で実際に踏んだ）。
    if let ["todo", user, list_name, "rename"] = parts.as_slice() {
        return handle_rename(method, user, list_name, body);
    }
```

**受け入れシナリオにも入れています。** ドメインを直接呼ぶ経路では検出できない欠陥です。

## まとめ

- **`Result` は最初の失敗で止まります。** 理由が 1 つしか返りません
- **理由を溜める型を別に作りました。** 3 案とも理由は 2 つ返りますが、**「合わせる」操作が出てくるのは 1 案だけ**でした
- **`Result` と `Validated` で役割が分かれます。** 使い分けが型に出ます
- **モナドが理由を落とすのは「両方だめ」のときだけ**でした。46 / 200 で 1 対 1 に対応します
- **計画に書いた具体例が、HTTP 経路では作れませんでした。** 実物を書いていても、**すべての経路で作れるか**までは確かめていませんでした
- **3 対象で初めて既製品に寄せました。** Kotlin 版の自作理由（既製品は失敗を返さない）が Rust では成り立ちません
- **既製品を入れても、既定が安全とは限りませんでした。** `tera` の自動エスケープは拡張子で決まります
- **フォームの値がパーセントエンコードされていました。** 第 4 章の実装は戻していません。**自分の出力だけで往復させると見つからない**欠陥の 2 件目です

次の章から、監視と 3 言語の総括に入ります。

---

## この章で書いたコード

- 検証: `apps/rust/zettai/zettai-step5-domain/src/validation.rs`
- 性質テスト: `apps/rust/zettai/zettai-step5-domain/tests/applicative_laws.rs`
- 画面: `apps/rust/zettai/zettai-step5-http/src/template.rs`
- 経路: `apps/rust/zettai/zettai-step5-http/src/http.rs`、`apps/rust/zettai/zettai-step5-http/src/acceptance.rs`
- スパイク: `apps/rust/zettai/spikes/error-shapes/`、`apps/rust/zettai/spikes/template-crates/`、`apps/rust/zettai/spikes/form-post/`
- 制約の一覧: `apps/rust/zettai/README.md`

本文のコードは、上のファイルからの転記です。次の 3 種類だけは逐語の転記ではありません。

- コマンドの実行例と、その出力
- つまずきの説明のために、わざと壊したコード
- 比較のために引いた他言語のコード

## 参照

Uberto Barbini『From Objects to Functions』第 11 章。題材と章構成をこの本に拠っています。本連載のコードはすべて書き起こした自作実装で、原著のコードを転載したものではありません。

- [Zettai — Rust 版](index.md) / [第 10 章](chapter10.md) / [Kotlin 版の第 11 章](../kotlin/chapter11.md) / [なでしこ3 版の第 11 章](../nadesiko/chapter11.md)
- [ADR-010 テンプレート機構](../../../adr/ADR-010-own-template.md) / [ADR-035 複数の理由を集める型](../../../adr/ADR-035-validated-applicative.md) / [ADR-036 tera を入れる](../../../adr/ADR-036-tera.md)
