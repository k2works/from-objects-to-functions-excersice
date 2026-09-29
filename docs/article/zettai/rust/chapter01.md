---
type: Article
title: "第 1 章 新しいアプリケーションを準備する"
description: "Zettai 連載 Rust 版の第 1 章。テストの土台が言語にある場合に第 1 章で何をするのかを書く。なでしこ3 版が自作した検証関数とランナーは cargo が最初から与えるので、代わりに後戻りの効かない判断（async の採否と HTTP クレートの選定）を実測してから決める。"
tags: [article, zettai, rust, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-29T02:08:46Z }
---

# 第 1 章 新しいアプリケーションを準備する

この連載は同じ 13 章を複数の言語で書き起こしています。Rust は **3 言語目**です。

[Kotlin 版](../kotlin/chapter01.md)は型があり関数型の道具もある言語、[なでしこ3 版](../nadesiko/chapter01.md)はどちらも無い言語でした。Rust は Kotlin と同じ側に見えますが、**言語が与えるものがもっと多い**言語です。

そして 2 対象に無かった制約が入ります。**所有権**です。

## サンプルアプリケーションを定義する

題材は 2 対象と同じ **Zettai** という ToDo リストアプリケーションです。Uberto Barbini の『From Objects to Functions』から借りています。

13 章をかけて、HTTP でリストを見せるところから、イベントソーシング・CQRS・永続化・バリデーション・監視までを作ります。**設計は 3 対象で同じです。変わるのは実現手段だけです。**

## Zettai: イノベーティブな ToDo リストアプリケーション

Zettai でできることは少しです。

- 利用者が自分の ToDo リストを見る
- リストに項目を足す
- 項目の状態を変える
- リストの名前を変える

**これだけを、13 章かけて作り直し続けます。** 機能を増やすのではなく、同じ機能の設計を変えていくのがこの連載です。

## テストに開発をガイドさせる

ここで 2 対象と大きく違います。

なでしこ3 版の第 1 章は、**テストを書く道具を作る**ところから始まりました。検証関数、型の比較、結果の集計、テストランナー。言語にテストフレームワークが無かったからです。

**Rust には最初からあります。**

```rust
    #[test]
    fn all_gutters_score_zero() {
        assert_eq!(score(&repeat_rolls(&[], 0, 20)), 0);
    }
```

`#[test]` を付ければ `cargo test` が拾います。`assert_eq!` は失敗すると左右の値を出します。並列に走ります。**何も作っていません。**

なでしこ3 版が第 1 章の大半を費やした作業が、この版では 0 行です。

### では第 1 章で何をするのか

**後戻りの効かない判断をします。**

Rust で HTTP と DB を扱うとき、最初に決めなければならないことがあります。**async を採るかどうか**です。

`async fn` を呼べるのは `async fn` の中だけです。第 9 章で async な DB クレートを選ぶと、そこから上のすべてが `async fn` になります。**第 1〜8 章のコードが書き換わります。**

これは章の依存関係には現れません。第 9 章の判断が第 2 章のコードの形を決めます。**決めるなら今です。**

## プロジェクトをセットアップする

### 実行環境

```text
nix develop .#rust
cd apps/rust/zettai
just check
```

| 項目 | バージョン |
| :--- | :--- |
| rustc | 1.91.1（LLVM 21.1.7） |
| cargo | 1.91.0 |
| just | 1.45.0 |
| cargo-llvm-cov | 0.6.20 |

段階ごとに cargo workspace のメンバーを増やします。Kotlin 版の Gradle モジュール、なでしこ3 版の `src/stepN/` に対応します。

```toml
[workspace]
resolver = "2"
members = ["zettai-step1-domain", "zettai-step1-http", "zettai-step2-domain", "zettai-step2-http"]
```

> 第 1 章の時点ではメンバーは 1 つでした。第 3 章でドメインを切り出して 2 つになり、第 4 章で 2 つめの段階が加わって 4 つになっています（[ADR-027](../../../adr/ADR-027-crate-boundary.md)・[ADR-029](../../../adr/ADR-029-cutting-a-step.md)）。**同じ段階の中でコードが育つ**ので、記事は最終形を載せています（[ADR-015](../../../adr/ADR-015-step-directories.md)）。

### 判断 1: async を採るか

**実際に書いて確かめました。**

同期の `postgres` クレートで、第 9 章（永続化）と第 10 章（トランザクションの境界）の題材が書けるかどうかです。

```rust
    let mut tx = client.transaction()?;
    tx.execute(
        "INSERT INTO spike_events (entity_id, payload) VALUES ($1, ($2::text)::jsonb)",
        &[&"uberto/shopping", &r#"{"type":"ListCreated"}"#],
    )?;
    tx.rollback()?;
```

実際の PostgreSQL に対して走らせました。

```bash
読み戻し: uberto/book / {"type": "ListCreated"}
ロールバック後の件数: 1
同期の postgres クレートで INSERT / SELECT / トランザクションが書けた
```

**書けました。** だから **async を採りません**（[ADR-024](../../../adr/ADR-024-no-async.md)）。

失うものもあります。同期のサーバは接続ごとにスレッドを使うので、同時接続数がスレッド数に縛られます。**教材なので問題にしませんが、本番では効きます。**

得るものは 2 つです。第 1〜8 章の書き換えリスクが 0 になること。そして**関数の形が 2 対象と揃う**ことです。`fn f(x: X) -> Y` のまま 3 対象を並べられます。

### つまずき 1: `$2::jsonb` はキャストにならない

上のコードで、最初はこう書きました。

<!-- code-check: ignore わざと壊したコード -->

```rust
"INSERT INTO spike_events (entity_id, payload) VALUES ($1, $2::jsonb)"
```

落ちました。

```bash
Error: Error { kind: ToSql(1), cause: Some(WrongType { postgres: Jsonb, rust: "&str" }) }
```

**`$2::jsonb` と書くと、パラメータの型そのものが `jsonb` と推論されます。** SQL のキャストのつもりが、ドライバへの型宣言になっていました。`($2::text)::jsonb` と書き、文字列として受けてからキャストします。

制約の一覧に 1 件目として記録しました。

### 判断 2: HTTP クレートをどれにするか

これは判断 1 と連動します。tokio 必須のクレートを選べば、その時点で async が確定するからです。

**3 候補で同じハンドラを書きました。** 「`/todo/{user}/{list}` で ToDo リストを 1 つ返す」だけのものです。

| 候補 | tokio 必須 | 依存クレート数 | 行数 | ビルド時間 |
| :--- | :--- | :--- | :--- | :--- |
| **`tiny_http`（採用）** | 不要 | **5** | 15 | **7 秒** |
| `rouille` | 不要 | 123 | 14 | 58 秒 |
| `axum` | 必須 | 61 | 13 | 70 秒 |

3 つとも実際に応答しました。**行数はほぼ同じです。**

差が出たのは**依存クレート数**（5 対 123 対 61）と**ビルド時間**（7 対 58 対 70 秒）でした。ビルド時間は検査の待ち時間に直接効きます。

`tiny_http` を選びました（[ADR-025](../../../adr/ADR-025-tiny-http.md)）。ルーティング機構が無いのでパスの分解は自前で書きますが、**なでしこ3 版と同じ形になります**。第 2 章でフレームワークがルーティングを隠さないほうが、設計の話がしやすくなります。

### 判断 3: 検査に何を入れるか

なでしこ3 版で失敗しています。`lint` が `test` と完全に重複していたのに Unit 7 まで気づかず、**検査時間の半分を食っていました。**

原因は「段を入れるときに、それが何を守るかを数えなかった」ことです。今回は先に書き出しました。

| 段 | 何を守るか | `test` と重複するか | 秒数 |
| :--- | :--- | :--- | :--- |
| `fmt` | 体裁 | しない（`test` は体裁を見ない） | 1 秒 |
| `clippy` | 慣用から外れた書き方 | しない（コンパイルは通るが指摘される書き方がある） | 3 秒 |
| `test` | 振る舞い。あわせて型・所有権も（コンパイルするため） | — | 3 秒 |
| `cov` | **テストが見ていないコード** | しない（`test` は「通ったか」しか見ない） | 3 秒 |

**4 段とも守るものが違うので、4 段とも入れました。** タスクランナーは Just です。

```just
# 書いている間はこれ
check:
    {{run}} just fmt lint test

# リリース前と CI はこれ（カバレッジを含む）
check-all:
    {{run}} just fmt lint test cov
```

2 段構えにしたのもなでしこ3 版の学びです。**速さが要るのは書いている間で、全部は CI の仕事**です。

```bash
just check      2〜4 秒
just check-all  8 秒
```

### つまずき 4: devShell の外から叩くと別の言語で動く

行頭の `{{run}}` は後から足したものです。**IDE から `just check` を叩いて気づきました。**

この版の道具は Nix の devShell から来ます。外から叩くと、そこにあるのはホストの道具です。

| 項目 | devShell 内 | ホスト |
| :--- | :--- | :--- |
| rustc | **1.91.1** | **1.97.1** |
| `cargo-llvm-cov` | 0.6.20 | 0.8.7 |
| `LLVM_COV` / `LLVM_PROFDATA` | 設定済み | **未設定 → `cov` が落ちる** |

**別のコンパイラで通って、CI で落ちます。** つまずき 2 と同じ形が、今度は逆向きに出ました。あちらは「ホストにしか無い」、こちらは「ホストのほうが使われる」です。

2 対象はこの問題を持ちません。**道具をプロジェクトの中に取り込んでいるから**です。

| 対象 | 道具の在り処 |
| :--- | :--- |
| Kotlin | `./gradlew`（リポジトリの中） |
| なでしこ3 | `./node_modules/.bin/cnako3`（リポジトリの中） |
| **Rust** | **PATH**（rustc も `cargo-llvm-cov` も取り込めない） |

rustc をリポジトリに置くわけにはいきません。そこで**環境ごと入り直します。**

```just
repo  := justfile_directory() / '../../..'
shell := 'nix develop ' + repo + '#rust --command'
run   := if env('IN_NIX_SHELL', '') == '' { shell } else { '' }
```

`IN_NIX_SHELL` は Nix が立てる変数です。**中にいれば `run` は空になり、何も挟まりません。** 外から叩いたときだけ `nix develop` が前に付きます。

`check` を依存指定（`check: fmt lint test`）から 1 行に変えたのはこのためです。依存のままだと `fmt`・`lint`・`test` がそれぞれ入り直して 3 回になります。

```just
# 依存指定ではなく 1 行で子レシピを呼ぶ。こうすると再入が 1 回で済む
```

外から叩いても 3〜4 秒のままでした。**`nix develop` の 2 回目以降は 1.5 秒で、ビルドのキャッシュが効いているぶんと相殺されます。**

### つまずき 2: `just` も `cargo-llvm-cov` も CI には来ない

どちらもローカルでは動きました。**ホストの `~/.cargo/bin` に入っていたからです。**

```bash
/Users/k2works/.cargo/bin/just
/Users/k2works/.cargo/bin/cargo-llvm-cov
```

CI は Nix の devShell しか使いません。**ローカルで動いたことは、CI で動くことを意味しません。** `shell.nix` に両方足しました。

足したあと、カバレッジがまだ落ちました。

```bash
error: failed to find llvm-tools-preview, please install llvm-tools-preview, or set LLVM_COV and LLVM_PROFDATA environment variables
```

`cargo-llvm-cov` は rustup の `llvm-tools-preview` を探しますが、Nix にはありません。調べると、**rustc の LLVM（21.1.7）と devShell の `llvmPackages.bintools` の版が一致していました。** 環境変数で指せば済みます。

```nix
    export LLVM_COV="$(command -v llvm-cov)"
    export LLVM_PROFDATA="$(command -v llvm-profdata)"
```

### つまずき 3: テストの書き込み先を分ける

なでしこ3 版の第 12 章で踏んだ問題です。記録の書き込み先を 1 つに決め打ちしていたので、**並列に走るテストが同じファイルに書いて件数が混ざりました。**

同じことを踏まないよう、第 1 章のうちに手段を用意します。

```rust
pub fn unique_path(label: &str) -> PathBuf {
    static COUNTER: AtomicUsize = AtomicUsize::new(0);
    let n = COUNTER.fetch_add(1, Ordering::SeqCst);
    let pid = std::process::id();
    std::env::temp_dir().join(format!("zettai_{label}_{pid}_{n}"))
}
```

**依存クレートは要りませんでした。** プロセス ID と連番で足ります。

## ユニットテストを関数型にする

ここからが第 1 章の本題です。題材はボウリングの得点計算にします。

### オブジェクト指向でよくある形

投球を溜め込むオブジェクトを作り、それを書き換えます。

```rust
impl Game {
    pub fn new() -> Self {
        Game::default()
    }

    /// 1 投する。**self を書き換える。**
    pub fn roll(&mut self, pins: u16) {
        self.rolls.push(pins);
    }
```

テストはこうなります。

```rust
    #[test]
    fn a_perfect_game_scores_three_hundred() {
        let mut game = Game::new();
        game.roll_many(10, 12);
        assert_eq!(game.score(), 300);
    }
```

**`mut` が付いています。** Rust は「これは書き換わる」をここで見せます。Kotlin の `var` やなでしこ3 の辞書より、目に入る位置にあります。

### 状態を持つと何が起きるか

「同じオブジェクトに聞いているのに答えが変わる」と書きたくなります。**試したら書けませんでした。**

得点は先頭 10 フレームで決まるので、12 投したあとに何を足しても答えは変わりません。**思い込みで書いたテストが落ちて気づきました。**

実際に示せたのはこちらです。

```rust
    #[test]
    fn asking_before_ten_frames_panics() {
        use std::panic::{catch_unwind, AssertUnwindSafe};
        let mut game = Game::new();
        game.roll_many(0, 4); // 2 フレーム分しか投げていない

        let asked_too_early = catch_unwind(AssertUnwindSafe(|| game.score()));

        // **いつ聞いてよいかが型に現れない。** 溜め込む側は「もう聞ける」を知らない。
        assert!(asked_too_early.is_err());
    }
```

**いつ聞いてよいかが型に現れません。** `score()` はいつでも呼べます。まだ 2 フレームしか投げていなくても呼べて、落ちます。

正直に書いておくと、**関数型で書いても同じ入力なら落ちます。** 違うのは、入力が呼び出し側に全部見えていることです。

### 関数型で書く

入力から出力への関数にします。

```rust
/// 投球の並びから得点を計算する。
pub fn score(rolls: &[u16]) -> u16 {
    let mut total = 0;
    let mut i = 0;
    for _ in 0..10 {
        if rolls[i] == 10 {
            total += 10 + rolls[i + 1] + rolls[i + 2];
            i += 1;
        } else {
```

**`&[u16]` を受け取ります。** 借用なので、呼び出し側は自分の並びを持ったままです。関数は読むだけで、書き換えません。

中に `let mut total` はあります。**関数の外から見れば、同じ入力に同じ出力を返すことは変わりません。**

投球を並べる補助もこう書けます。

```rust
pub fn repeat_rolls(rolls: &[u16], pins: u16, n: usize) -> Vec<u16> {
    rolls
        .iter()
        .copied()
        .chain(std::iter::repeat_n(pins, n))
        .collect()
}
```

なでしこ3 版では、この関数に**ガードが必要でした**。`繰り返す` が開始より終了が小さいときに逆向きに回るためです。

```rust
/// なでしこ3 版では `繰り返す` が開始より終了が小さいときに逆向きに回るので、
/// 0 件を先に返すガードが要った。Rust の `0..n` は n が 0 なら空なので要らない。
```

**Rust の `0..n` は n が 0 なら空です。** ガードが 1 つ消えました。

### 走らせる

```bash
running 9 tests
test result: ok. 9 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out
```

カバレッジも測ります。

```bash
TOTAL   126 regions / 99.21%   18 functions / 100.00%   81 lines / 100.00%
```

## まとめ

- **テストの土台を作りませんでした。** `#[test]` と `assert_eq!` と `cargo test` が言語にあります。なでしこ3 版が第 1 章の大半を費やした作業が 0 行です
- **代わりに、後戻りの効かない判断を 2 つしました。** async を採るか、HTTP クレートをどれにするか。どちらも**実際に書いて数字を出してから**決めました
- **async を採りません。** 同期の `postgres` クレートで第 9〜10 章の題材が書けることを実 DB で確かめました。失うのは同時接続数、得るのは第 1〜8 章の書き換えリスク 0 と、2 対象と揃う関数の形です
- **`tiny_http` を選びました。** 3 候補で行数はほぼ同じで、差が出たのは依存クレート数（5 対 123 対 61）とビルド時間（7 対 58 対 70 秒）でした
- **検査は 4 段とも「何を守るか」を書いてから入れました。** なでしこ3 版では重複した段に Unit 7 まで気づかず、検査時間の半分を食っていました
- **ローカルで動いたことは、CI で動くことを意味しません。** `just` も `cargo-llvm-cov` もホストに入っていただけで、devShell には無く CI にも来ませんでした
- **思い込みで書いたテストが 1 本落ちました。** 「同じオブジェクトが違う答えを返す」は、この題材では示せません。示せたのは「**いつ聞いてよいかが型に現れない**」ことでした

次の章では HTTP を扱います。**所有権に最初に当たる章**になるはずです。

---

## この章で書いたコード

- 実装: `apps/rust/zettai/zettai-step1-http/src/lib.rs`、`apps/rust/zettai/zettai-step1-http/src/bowling.rs`、`apps/rust/zettai/zettai-step1-http/src/bowling_oo.rs`
- 検査: `apps/rust/zettai/justfile`
- 環境: `ops/nix/environments/rust/shell.nix`
- 制約の一覧: `apps/rust/zettai/README.md`

第 1〜2 章は `zettai-step1-http/` で育てます。段階が増えたら workspace のメンバーを足します（[ADR-015](../../../adr/ADR-015-step-directories.md) と同じ考え方）。

本文のコードは、上のファイルからの転記です。次の 3 種類だけは逐語の転記ではありません。

- コマンドの実行例と、その出力
- つまずきの説明のために、わざと壊したコード
- 比較のために引いた他言語のコード

## 参照

Uberto Barbini『From Objects to Functions』第 1 章。題材と章構成をこの本に拠っています。本連載のコードはすべて書き起こした自作実装で、原著のコードを転載したものではありません。

- [Zettai — Rust 版](index.md) / [Kotlin 版の第 1 章](../kotlin/chapter01.md) / [なでしこ3 版の第 1 章](../nadesiko/chapter01.md)
- [ADR-024 async を採らない](../../../adr/ADR-024-no-async.md) / [ADR-025 tiny_http を採用する](../../../adr/ADR-025-tiny-http.md) / [ADR-026 Just とカバレッジ](../../../adr/ADR-026-just-and-coverage.md)
