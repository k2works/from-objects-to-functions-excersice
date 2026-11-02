# Zettai Scala 版

既存の Kotlin 版、Kotlin FP 版、なでしこ3 版、Rust 版、Flix 版と同じ ToDo リストアプリケーションを、**Scala 3 と Cats Effect** で作ります。副作用を `IO` の値として扱い、型クラスを標準の語彙として使うと、設計が何を得て何を手放すかを比較します。

Flix のように言語が効果を追跡するのではなく、ライブラリが与える形です。連載で自作してきたモノイド・ファンクタ・バリデーションを、Cats のものと突き合わせます。

## 公開済み

1. [第 1 章 新しいアプリケーションを準備する](chapter01.md)
2. [第 2 章 関数を使って HTTP を扱う](chapter02.md)

第 3 章以降は執筆中です。計画は [執筆計画](../../outline.md) の「Scala 追加執筆計画」にあります。

## 実行方法

JDK 21 以降が必要です。Scala の版は `apps/scala/zettai/build.sbt` で 3.3.9 に固定しています。

```bash
nix develop .#scala
cd apps/scala/zettai
sbt test
```

Nix を使わない場合は、JDK 21 以降と sbt を用意して `sbt test` を実行します。手順は [第 1 章](chapter01.md) にあります。
