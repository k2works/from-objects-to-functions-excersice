# Zettai Scala 版

既存の Kotlin 版、Kotlin FP 版、なでしこ3 版、Rust 版、Flix 版と同じ ToDo リストアプリケーションを、**Scala 3 と Cats Effect** で作ります。副作用を `IO` の値として扱い、型クラスを標準の語彙として使うと、設計が何を得て何を手放すかを比較します。

Flix のように言語が効果を追跡するのではなく、ライブラリが与える形です。連載で自作してきたモノイド・ファンクタ・バリデーションを、Cats のものと突き合わせます。

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

第 13 章は執筆中です。計画は [執筆計画](../../outline.md) の「Scala 追加執筆計画」にあります。

## 実行方法

JDK 21 以降が必要です。第 9 章以降のテストは、PostgreSQL を Testcontainers で起動するため Docker も必要です。Scala の版は `apps/scala/zettai/build.sbt` で 3.3.9 に固定しています。

```bash
nix develop .#scala
cd apps/scala/zettai
sbt test
```

Nix を使わない場合は、JDK 21 以降と sbt を用意して `sbt test` を実行します。手順は [第 1 章](chapter01.md) にあります。
