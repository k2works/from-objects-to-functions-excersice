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

第 13 章は執筆中です。計画は [執筆計画](../../outline.md) の「Flix 追加執筆計画」にあります。

## 実行方法

JDK 21 以降が必要です。Flix の版は `apps/flix/zettai/flix.toml` で 0.79.0 に固定しています。

```bash
nix develop .#flix
cd apps/flix/zettai
flix test
```

Nix を使わない場合は、公式リリースから `flix.jar` を取得して `java -jar flix.jar test` を実行します。手順は [第 1 章](chapter01.md) にあります。
