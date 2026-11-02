# Zettai（Scala 版）

Zettai 連載 5 言語目（6 実装方針目）のサンプル実装です。記事は [`docs/article/zettai/scala/`](../../../docs/article/zettai/scala/) にあります。Scala 3 と Cats Effect で書きます。

## 実行環境

```bash
nix develop .#scala
cd apps/scala/zettai
sbt test
```

| 項目 | バージョン |
| :--- | :--- |
| Scala | 3.3.9（`build.sbt` で固定。3.3 系の LTS） |
| sbt | 1.10.11（`project/build.properties` で固定） |
| JDK | 21（devShell が提供） |
| Cats | 2.13.0 |
| Cats Effect | 3.7.1 |
| MUnit / munit-cats-effect | 1.3.6 / 2.2.1（テスト） |

Nix を使わない場合は、JDK 21 以降と sbt を自分で用意して同じコマンドを実行します。初回は依存ライブラリの取得で時間がかかります。

## コマンド

```bash
sbt test      # テストの実行（コンパイルを含む）
sbt compile   # 本体のコンパイル
```

## 既知の制約

- Windows の日本語環境では、sbt の出力に含まれる日本語が文字化けする。ソースは UTF-8 で、テストの判定には影響しない
- ライブラリの版は組み合わせを確かめて固定した。上げるときは全テストを通し直す
