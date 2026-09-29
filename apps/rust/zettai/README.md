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
| 4 | **`Vec<String>` と `&[&str]` はそのまま比べられない**（E0308）。テストの期待値を `&str` で並べると落ちる | 比べる直前に `["a","b"].map(String::from)` で揃える。**期待値は `&str` のまま書けるので、シナリオは読みやすく保てる** |
| 3 | **IntelliJ が `PATH` を引用せずに渡すことがある。** `IntelliJ IDEA.app` のスペースで `export PATH=...` が壊れ、`zsh:export:1: not valid in this context` になる | 実行構成の環境変数から `PATH` の上書きを外す。**この `justfile` は PATH に依存しないので、上書きする理由が無い** |

### 設計に効くもの

所有権・借用・ライフタイムが設計を押した箇所を記録します。第 13 章で「設計の改善だったもの」と「回避しただけのもの」に仕分けます。

| # | 押された箇所 | 押された先 | 章 |
| :--- | :--- | :--- | :--- |
| 1 | 受け入れテストで `fetch_list(..).expect(..).items.iter()` と繋いだら、一時値が借用中に破棄された（E0716） | **一度変数に受ける。** 結果として「取り出す」と「見る」が 2 行に分かれ、読みやすくなった | 2 |
| 2 | ドメインとインフラを同じクレートに置くと、**相互参照できてしまう**（2 対象と同じく検査が要る） | **クレートを分けた。** `Cargo.toml` に書いていない相手はコンパイルが止める。境界検査を書かなくてよくなった | 3 |

### clippy に押されたもの

`clippy -- -D warnings` で止まった箇所を記録します。第 13 章で「設計の改善」と「clippy の好み」に仕分けます。

| # | 指摘 | どう直したか | 設計の改善か |
| :--- | :--- | :--- | :--- |
| | | | |
