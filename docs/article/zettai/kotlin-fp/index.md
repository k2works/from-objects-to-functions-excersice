# Zettai Kotlin FP 版

既存の Kotlin 版と同じ ToDo リストアプリケーションを、Kotlin の関数、Arrow、kotlinx.coroutines を使って作ります。自前の関数型抽象を組み立てる代わりにライブラリへ委ねると、設計判断がどう変わるかを比較します。

## 公開済み

1. [第 1 章 新しいアプリケーションを準備する](chapter01.md)
2. [第 2 章 関数を使って HTTP を扱う](chapter02.md)
3. [第 3 章 ドメインの定義とテスト](chapter03.md)

## 実行方法

```bash
nix develop .#kotlin-fp
cd apps/kotlin-fp/zettai
bash ./gradlew check
```
