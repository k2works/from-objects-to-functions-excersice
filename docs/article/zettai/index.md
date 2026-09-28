# Zettai — 関数型プログラミングで作る変更を楽に安全にできるソフトウェア

ToDo リストアプリケーション **Zettai** を TDD で一から作りながら、オブジェクト指向から関数型へ設計を移していく連載シリーズです。題材と章構成は Uberto Barbini 著『From Objects to Functions』に拠っています。

- 執筆計画：[outline.md](../outline.md)
- リリース計画（イテレーション・ストーリー・進捗）：[Kotlin 版](../../development/release_plan.md) / [なでしこ3 版](../../development/release_plan-nadesiko.md) / [Rust 版](../../development/release_plan-rust.md)
- 章構成マインドマップ：[draft.md](../draft.md)
- 参照元の原著コンパニオンコード：`references/fotf/`（読み取り専用）

## 対象別一覧

| 対象 | 記事 | サンプル実装 | 実行環境 | 状態 |
| :--- | :--- | :--- | :--- | :--- |
| Kotlin | [Kotlin 版](kotlin/index.md) | `apps/kotlin/zettai/` | Nix devShell `kotlin`（JDK 21 / Gradle） | **完結（13 / 13 章）** |
| なでしこ3 | [なでしこ3 版](nadesiko/index.md) | `apps/nadesiko/zettai/` | Nix devShell `nadesiko`（Node 22 / cnako3 3.8.7） | **完結（13 / 13 章）** |
| Rust | [Rust 版](rust/index.md) | `apps/rust/zettai/` | Nix devShell `rust`（rustc 1.91.1 / cargo 1.91.0） | 進行中（1 / 13 章） |

現在の対象は **3 言語**です。横断比較は [対象言語の横断比較](comparison/index.md) にまとめています（なでしこ3 版の Phase 1 完了時に新設）。

当初の比較軸は「型で保証する（Kotlin）」と「約束とテストで保証する（なでしこ3）」の 2 点でした。**Rust 版で軸が「言語がどこまで与えるか」に広がります。**

| 対象 | 型 | 関数型の道具 | `Outcome` 相当 | 代数的データ型 |
| :--- | :--- | :--- | :--- | :--- |
| Kotlin | あり | あり | **自前で作る** | `sealed interface` |
| なでしこ3 | なし | なし | **辞書で作る** | 無い |
| Rust | あり | あり | **`Result` が言語にある** | `enum` |

Rust 版の列は、Phase 1（第 3 章）を終えた時点で比較ページに足します（新設ではありません）。

## 全章構成

| 章 | タイトル | Kotlin | なでしこ3 | Rust |
| :--- | :--- | :--- | :--- | :--- |
| 1 | 新しいアプリケーションを準備する | [公開済み](kotlin/chapter01.md) | [公開済み](nadesiko/chapter01.md) | [公開済み](rust/chapter01.md) |
| 2 | 関数を使って HTTP を扱う | [公開済み](kotlin/chapter02.md) | [公開済み](nadesiko/chapter02.md) | 計画中 |
| 3 | ドメインの定義とテスト | [公開済み](kotlin/chapter03.md) | [公開済み](nadesiko/chapter03.md) | 計画中 |
| 4 | ドメインとアダプタのモデリング | [公開済み](kotlin/chapter04.md) | [公開済み](nadesiko/chapter04.md) | 計画中 |
| 5 | イベントで状態を変更する | [公開済み](kotlin/chapter05.md) | [公開済み](nadesiko/chapter05.md) | 計画中 |
| 6 | コマンドを実行してイベントを生成する | [公開済み](kotlin/chapter06.md) | [公開済み](nadesiko/chapter06.md) | 計画中 |
| 7 | 関数型手法によるエラーハンドリング | [公開済み](kotlin/chapter07.md) | [公開済み](nadesiko/chapter07.md) | 計画中 |
| 8 | ファンクタを使ってイベントを射影する | [公開済み](kotlin/chapter08.md) | [公開済み](nadesiko/chapter08.md) | 計画中 |
| 9 | モナドによる安全なデータ永続化 | [公開済み](kotlin/chapter09.md) | [公開済み](nadesiko/chapter09.md) | 計画中 |
| 10 | コンテキストを読み込み、コマンドを処理する | [公開済み](kotlin/chapter10.md) | [公開済み](nadesiko/chapter10.md) | 計画中 |
| 11 | アプリカティブによるデータバリデーション | [公開済み](kotlin/chapter11.md) | [公開済み](nadesiko/chapter11.md) | 計画中 |
| 12 | 監視と関数型 JSON | [公開済み](kotlin/chapter12.md) | [公開済み](nadesiko/chapter12.md) | 計画中 |
| 13 | 関数型アーキテクチャの設計 | [公開済み](kotlin/chapter13.md) | [公開済み](nadesiko/chapter13.md) | 計画中 |

章の番号とタイトルは対象言語をまたいで共通です。章を公開したら、この表の状態を記事へのリンクに置き換え、`mkdocs.yml` の nav にも追加します。

## 進捗管理

| 項目 | Kotlin | なでしこ3 | Rust |
| :--- | :--- | :--- | :--- |
| 実行環境（Nix devShell） | **完了** | **完了** | **完了** |
| サンプル実装の雛形 | **完了** | **完了** | **完了** |
| CI | **完了** | **完了** | Unit 2 で作る |
| 公開済みの章 | **13 / 13（完結）** | **13 / 13（完結）** | 1 / 13 |

## 実行環境一覧

| 対象 | devShell | 主な構成 |
| :--- | :--- | :--- |
| Kotlin | `nix develop .#kotlin` | Kotlin 2.x、JDK 21、Gradle。第 9 章以降は `docker-compose.yml` の PostgreSQL を併用 |
| なでしこ3 | `nix develop .#nadesiko` | Node 22、cnako3 3.8.7（npm の `nadesiko3`）、自作のテストヘルパとランナー（`make check`）。第 9 章の永続化は追記型イベントログのファイル保存 |
| Rust | `nix develop .#rust` | rustc 1.91.1、cargo 1.91.0、clippy 0.1.91、rustfmt 1.8.0、just 1.45.0、cargo-llvm-cov 0.6.20。タスクは `just check`。テストは組み込みの `#[test]`。**async は採らず同期で通す**（[ADR-024](../../adr/ADR-024-no-async.md)）。第 9 章の永続化は PostgreSQL |
