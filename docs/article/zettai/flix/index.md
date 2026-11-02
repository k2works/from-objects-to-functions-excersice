# Zettai Flix 版

既存の Kotlin 版、Kotlin FP 版、なでしこ3 版、Rust 版と同じ ToDo リストアプリケーションを、**効果システムを持つ Flix** で作ります。純粋な核と副作用の殻の分離を、規律ではなく型検査で守れるかを比較します。

HTTP やデータベースは Java 相互運用で JDK を呼びます。効果の保証が境界のどこで切れるかを、章ごとに記録します。

## 公開済み

1. [第 1 章 新しいアプリケーションを準備する](chapter01.md)
2. [第 2 章 関数を使って HTTP を扱う](chapter02.md)
3. [第 3 章 ドメインの定義とテスト](chapter03.md)
4. [第 4 章 ドメインとアダプタのモデリング](chapter04.md)
5. [第 5 章 イベントで状態を変更する](chapter05.md)
6. [第 6 章 コマンドを実行してイベントを生成する](chapter06.md)
7. [第 7 章 関数型手法によるエラーハンドリング](chapter07.md)
8. [第 8 章 ファンクタを使ってイベントを射影する](chapter08.md)
9. [第 9 章 モナドによる安全なデータ永続化](chapter09.md)
10. [第 10 章 コンテキストを読み込み、コマンドを処理する](chapter10.md)
11. [第 11 章 アプリカティブによるデータバリデーション](chapter11.md)
12. [第 12 章 監視と関数型 JSON](chapter12.md)
13. [第 13 章 関数型アーキテクチャの設計](chapter13.md)

全 13 章を書き終えました。計画は [執筆計画](../../outline.md) の「Flix 追加執筆計画」にあります。計画は [執筆計画](../../outline.md) の「Flix 追加執筆計画」にあります。

## 実行方法

JDK 21 以降が必要です。Flix の版は `apps/flix/zettai/flix.toml` で 0.79.0 に固定しています。

```bash
nix develop .#flix
cd apps/flix/zettai
flix test
```

Nix を使わない場合は、公式リリースから `flix.jar` を取得して `java -jar flix.jar test` を実行します。手順は [第 1 章](chapter01.md) にあります。

## 章ごとのコードを再現する

Flix 版は、1 つのプロジェクトを章ごとに育てています。**最新のコードで `flix test` を実行すると、第 9 章以降の PostgreSQL の結合テストが必要になり、PostgreSQL が無いと 13 本が落ちます**（クリーンな clone で確かめた結果は、144 本が通り、13 本が落ちました）。

各章の記事は、その章を書いた時点のコードに対応します。時点のコミットに切り替えると、記事に書いた件数どおりに通ります。

| 章 | コミット | `flix test` の件数 | PostgreSQL |
| :--- | :--- | :--- | :--- |
| 1 | `741d7d5` | 3 | 不要 |
| 2 | `8a07f68` | 14 | 不要 |
| 3 | `356b4d4` | 21 | 不要 |
| 4 | `e383173` | 28 | 不要 |
| 5 | `01e96c4` | 40 | 不要 |
| 6 | `4db46a5` | 58 | 不要 |
| 7 | `59b1950` | 66 | 不要 |
| 8 | `25cbb90` | 79 | 不要 |
| 9 | `d0567ee` | 91 | 必要 |
| 10 | `3ca542c` | 101 | 必要 |
| 11 | `ae7eef8` | 129 | 必要 |
| 12 | `55227c5` | 157 | 必要 |
| 13 | `9ec6c9c` | 157 | 必要 |
| （最新の `main`） | 公開後に `TestHttpOverPostgres` を追加 | 158 | 必要 |

2026-11-03 に、新しく clone したリポジトリで、全 13 の時点を実行して確かめました。第 9 章以降は、使い捨ての PostgreSQL を別のポートで起動し、`ZETTAI_DB_URL` で向けました。

```bash
git checkout 741d7d5
cd apps/flix/zettai
curl -sL -o flix.jar https://github.com/flix/flix/releases/download/v0.79.0/flix.jar
java -jar flix.jar test
```

試したあとは、`git checkout main` で戻します。PostgreSQL の起動方法は、実装の `apps/flix/zettai/README.md` にあります。
