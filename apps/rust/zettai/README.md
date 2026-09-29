# Zettai（Rust 版）

Zettai 連載 3 言語目のサンプル実装です。記事は [`docs/article/zettai/rust/`](../../../docs/article/zettai/rust/) にあります。

## 実行環境

```bash
nix develop .#rust
```

| 項目 | バージョン |
| :--- | :--- |
| rustc | 1.91.1（LLVM 21.1.7） |
| cargo | 1.91.0 |
| clippy | 0.1.91 |
| rustfmt | 1.8.0 |
| just | 1.45.0 |
| cargo-llvm-cov | 0.6.20 |

## コマンド

```bash
just            # タスク一覧
just check      # fmt + clippy + test（書いている間はこれ）
just check-all  # 上 + カバレッジ（リリース前と CI）
```

**各段が何を守るかは `justfile` に書いてあります。** 守っているものが無い段は入れません（[ADR-026](../../../docs/adr/ADR-026-just-and-coverage.md)）。

### devShell の外から叩いてもよい

**`justfile` が自分で `nix develop` に入り直します。** IDE のターミナルからでも、素のシェルからでも、CI と同じ道具（rustc 1.91.1・`cargo-llvm-cov` 0.6.20・`LLVM_COV`）で走ります。

そうしないと、ホストの rustc（別の版）で通って CI で落ちます。**2 対象は `./gradlew` と `./node_modules/.bin/cnako3` で道具をリポジトリに取り込んでいますが、rustc は取り込めません。**

再入は 1 回だけです（`check` が依存指定ではなく 1 行で子レシピを呼ぶため）。**続けて作業するなら、先に devShell に入るほうが速くなります。**

```bash
nix develop .#rust      # 入っておけば再入しない
cd apps/rust/zettai
just check
```

## 既知の制約

**この一覧は Unit 1 から置いています。** なでしこ3 版では Unit 6 になってから作り、それまでに同じ制約を 3 回踏みました。一覧を作って設計の前に読んだ章では、設計の間違いが 0 回になりました。**作るのに 30 分もかかりません。**

踏んだものはその場でここに足します。**空のまま始めます。**

### 言語・処理系

| # | 制約 | 対処 |
| :--- | :--- | :--- |
| 1 | `postgres` クレートで `$2::jsonb` と書くと**パラメータの型が `jsonb` と推論**され、`&str` を渡せない（`WrongType { postgres: Jsonb, rust: "&str" }`） | `($2::text)::jsonb` と書き、文字列として受けてからキャストする |
| 2 | **devShell の外では道具が別物になる。** ホストの rustc は 1.97.1、devShell は 1.91.1。`LLVM_COV` も未設定で `cov` が落ちる | `justfile` が `IN_NIX_SHELL` を見て `nix develop` に入り直す |
| 3 | **IntelliJ が `PATH` を引用せずに渡すことがある。** `IntelliJ IDEA.app` のスペースで `export PATH=...` が壊れ、`zsh:export:1: not valid in this context` になる | 実行構成の環境変数から `PATH` の上書きを外す。**この `justfile` は PATH に依存しないので、上書きする理由が無い** |
| 4 | **`Vec<String>` と `&[&str]` はそのまま比べられない**（E0308）。テストの期待値を `&str` で並べると落ちる | 比べる直前に `["a","b"].map(String::from)` で揃える。**期待値は `&str` のまま書けるので、シナリオは読みやすく保てる** |
| 5 | **`impl Fn` は書くたびに別の型になる。** 同じ `Vec` に入れられない。エラーは `expected opaque type` と `found opaque type` を**同じ文字列で**並べる（E0308） | 並びに入れるものは `Box<dyn Fn>` に揃える。差し替えるだけの依存は `impl Fn` のままでよい |
| 6 | **`Box<dyn Fn>` は既定で `+ 'static` が付く。** 借りた値を閉じ込めると `must outlive 'static` で落ちる | 閉じ込める前に所有する（`to_string()` など）。型別名に `'a` を足す道もあるが、イベントは自分の値を持つほうが形に合う |
| 7 | **段階を増やすと `bin` の名前が衝突する。** `zettai-step2-http` の `zettai` が `zettai-step1-http` の `zettai` と同じ出力名になる（cargo #6313。いまは警告、将来エラー） | 段階ごとに `[[bin]] name` を変える。**段階を切る手順の ADR に入れる** |
| 8 | **`dyn Trait` の主トレイトのメソッドは `use` なしで呼べる。** `as_reader().read_to_string(..)` は `std::io::Read` を import しなくても通り、import すると `unused_imports` で clippy が落ちる | 主トレイトのメソッドだけを使うなら import しない |
| 9 | **`match` の網羅はワイルドカードで無効になる。** `_ =>` を 1 つ置くと、枝が足りなくてもコンパイラは止まらない。`clippy::wildcard_enum_match_arm` は**単体の enum を直接 match したときだけ**発火し、**組（`(State, Command)`）や `Option<&T>` ごしでは発火しない** | lint は付けるが、**遷移表の形では効かない**。マスを数えてテストする |
| 10 | **`Result` は `#[must_use]` だが `Option` は違う。** 戻り値を捨てると `Result` だけコンパイラが警告する | **失敗を握りつぶしていた箇所が見つかった。** `Option` のままでは見つからなかった |
| 11 | **`postgres::Error` の `Display` は `"db error"` としか出さない。** 本当の理由は `source()` の先にある | 包むときに `source()` を辿って文字列にする（`spikes/sync-postgres/` の `detail_of`） |

### 設計に効くもの

所有権・借用・ライフタイムが設計を押した箇所を記録します。第 13 章で「設計の改善だったもの」と「回避しただけのもの」に仕分けます。

| # | 押された箇所 | 押された先 | 章 |
| :--- | :--- | :--- | :--- |
| 1 | 受け入れテストで `fetch_list(..).expect(..).items.iter()` と繋いだら、一時値が借用中に破棄された（E0716） | **一度変数に受ける。** 結果として「取り出す」と「見る」が 2 行に分かれ、読みやすくなった | 2 |
| 2 | ドメインとインフラを同じクレートに置くと、**相互参照できてしまう**（2 対象と同じく検査が要る） | **クレートを分けた。** `Cargo.toml` に書いていない相手はコンパイルが止める。境界検査を書かなくてよくなった | 3 |
| 3 | 変換の合成を**型で**表すと（`Composed<F, G>`）、実行時に長さが決まるイベント列を畳み込めない。`fold` の途中で `Identity` が `Composed<Identity, Add>` になる（E0308） | **合成を型ではなく値で持つ**（`Box<dyn Fn>`）。単位元から始めて繰り返せるようになり、モノイドの形がそのまま出る | 4・5 |
| 4 | クロージャに借りた値を閉じ込めると `'static` に足りない | **借りずに所有する。** イベントは保存して後から畳み込むので、借り先が先に消える。**押された先が正しい** | 5 |
| 5 | ハブの戻り値の型に `impl Fn` を 2 つ書くと、名前を付けられず持ち回れない | **`Box<dyn Fn>` にして `type StoreHub` と名付けた。** 配線を組み立てる場所が 1 か所に寄った | 7 |

### clippy に押されたもの

`clippy -- -D warnings` で止まった箇所を記録します。第 13 章で「設計の改善」と「clippy の好み」に仕分けます。

| # | 指摘 | どう直したか | 設計の改善か |
| :--- | :--- | :--- | :--- |
| 1 | `type_complexity`: `Box<dyn Fn(&str, &str) -> Fetched>` を構造体のフィールドに直接書くな | `type Fetcher = ...` と名前を付けた | **改善。** 依存 1 つ分に名前が付き、記事でも指せるようになった |
| 2 | `type_complexity`: ハブの戻り値（`ToDoListHub<impl Fn.., impl Fn..>`）が複雑すぎる | `type StoreHub<'a>` と名前を付けた。`impl Fn` から `Box<dyn Fn>` に変わる | **改善。** 記事でも指せるようになった |
| 3 | `map_identity`: 恒等関数を `map` するな | **`#[allow]` で許可した。** ここではそれが確かめたいファンクタ則そのもので、消すとテストが消える | **clippy の好み。** ふだんは正しいが、法則のテストには当たらない |
