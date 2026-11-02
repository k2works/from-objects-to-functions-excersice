# Zettai（Flix 版）

Zettai 連載 4 言語目のサンプル実装です。記事は [`docs/article/zettai/flix/`](../../../docs/article/zettai/flix/) にあります。

## 実行環境

```bash
nix develop .#flix
cd apps/flix/zettai
flix test
```

| 項目 | バージョン |
| :--- | :--- |
| Flix | 0.79.0（`flix.toml` で固定） |
| JDK | 21（Flix の要件は 21 以降） |

`flix` は devShell が定義するシェル関数です。初回に公式リリースの `flix.jar` を `.flix/` に取得します（`.gitignore` 済み）。Nix を使わない場合は、`flix.jar` を取得して `java -jar flix.jar` で同じコマンドを実行します。

## コマンド

```bash
flix check   # 型と効果の検査
flix test    # テスト
```

## 既知の制約

Unit 1 時点で確認できたことだけを書きます。章を進めるたびに追記します。

| 制約 | 内容 |
| :--- | :--- |
| 版の固定 | Flix は変更が速いため `flix.toml` の `flix` で固定する。上げるときは記事のコードと出力も取り直す |
| Windows のコンソール | 既定のコードページ（日本語環境では Shift_JIS 系）で、エラー出力の日本語が文字化けする。ソースは UTF-8 で、テストの判定には影響しない |
| Nix | `nix` が無い環境では devShell を評価できていない（Unit 1 時点で未検証） |
