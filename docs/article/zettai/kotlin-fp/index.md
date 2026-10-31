# Zettai Kotlin FP 版

既存の Kotlin 版と同じ ToDo リストアプリケーションを、Kotlin の関数、Arrow、kotlinx.coroutines を使って作ります。自前の関数型抽象を組み立てる代わりにライブラリへ委ねると、設計判断がどう変わるかを比較します。

## 公開済み

1. [第 1 章 新しいアプリケーションを準備する](chapter01.md)
2. [第 2 章 関数を使って HTTP を扱う](chapter02.md)
3. [第 3 章 ドメインの定義とテスト](chapter03.md)
4. [第 4 章 ドメインとアダプタのモデリング](chapter04.md)
5. [第 5 章 イベントで状態を変更する](chapter05.md)
6. [第 6 章 コマンドを実行してイベントを生成する](chapter06.md)
7. [第 7 章 関数型手法によるエラーハンドリング](chapter07.md)
8. [第 8 章 ファンクタを使ってイベントを射影する](chapter08.md)

## 実行方法

JDK 25 と Docker daemon が必要です。Docker は `check` に含まれる Testcontainers PostgreSQL テストで使います。

```bash
nix develop .#kotlin-fp
cd apps/kotlin-fp/zettai
bash ./gradlew check
```

Windows PowerShell では project root から次を実行します。

```powershell
cd apps/kotlin-fp/zettai
.\gradlew.bat check
```
