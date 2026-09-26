# Zettai（Kotlin 版サンプル実装）

連載 [Zettai — 関数型プログラミングで作る変更を楽に安全にできるソフトウェア](../../../docs/article/zettai/kotlin/index.md) のサンプル実装です。記事に載せるコードはすべてこのプロジェクトの動作確認済みの実装から転記します。

## 実行環境

リポジトリルートで devShell に入ります。

```bash
nix develop .#kotlin
```

devShell には次が入ります。

| ツール | バージョン | 用途 |
| :--- | :--- | :--- |
| JDK | 21 | 実行環境 |
| Kotlin | 2.3 | CLI（`kotlinc`）。ビルドでは使わない |
| Gradle | 8.14 | ビルド。Wrapper があるので `./gradlew` を使う |

**ビルドの正は Gradle 側です。** コンパイラのバージョンは `gradle/libs.versions.toml` の `kotlin` で決まります（現在 2.2.0）。devShell の `kotlinc` は単発のスクリプトを試すための CLI で、バージョンが一致していなくてもビルドには影響しません。

## ビルドとテスト

```bash
./gradlew check
```

`check` にコンパイルとテストが集約されています。コミット前にこれが green であることを確認します。

## ディレクトリ規約

```text
apps/kotlin/zettai/
├── settings.gradle.kts          # モジュールを include
├── build.gradle.kts             # 全モジュール共通の設定（jvmToolchain(21)・テスト依存）
├── gradle/
│   └── libs.versions.toml       # 依存バージョンの集中管理
└── zettai-stepN-<テーマ>/        # 章の進行にあわせて増える
    ├── build.gradle.kts         # そのモジュール固有の依存だけを書く
    └── src/
        ├── main/kotlin/zettai/
        └── test/kotlin/zettai/
```

- **モジュール名**は原著のコンパニオンコード（`zettai_stepN_*`）に対応させ、Gradle の慣習にあわせてハイフン区切りにします
- **バージョンは `libs.versions.toml` に集中させます。** `build.gradle.kts` にリテラルのバージョンを書きません
- **パッケージは `zettai`** です。原著のパッケージ（`com.ubertob.fotf.*`）とは分けています。自作実装であることを明確にするためです

## モジュール一覧

| モジュール | 章 | 内容 |
| :--- | :--- | :--- |
| `zettai-step1-http` | 1〜2 | プロジェクトの雛形と、第 2 章の HTTP アダプタ |

章が進むごとにこの表に行を追加します。

## テストの書き方

- テストフレームワークは JUnit 5、アサーションは [Strikt](https://strikt.io/) を使います
- テスト名は日本語のバッククォート記法（`` fun `アプリケーション名を返す`() ``）で書きます。何を確かめているかが一文で読めるようにするためです
- テストは純粋関数として書きます。共有された可変状態を持ち込まず、入力から出力を確かめる形にします（第 1 章の主題）
