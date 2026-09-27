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

## 動かす

第 12 章の構造化ログは、動かすと標準出力で見られます。

```bash
./gradlew :zettai-step5-monitoring:run
```

`http://localhost:8080/todo/uberto/book` を開くと画面が表示され、標準出力にログが 1 行 1 JSON で出ます。出来事の保存先はメモリなので、止めると消えます（PostgreSQL 経由の経路は結合テストで確かめています）。

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
| `zettai-step2-domain` | 3〜4 | ドメインとインフラの分離、関数型の依存性注入 |
| `zettai-step3-persistence` | 5〜9 | イベントの畳み込み・コマンド・`Outcome`・射影・PostgreSQL 永続化 |
| `zettai-step4-context` | 10〜11 | `ContextReader` とトランザクション、アプリカティブによるバリデーション |
| `zettai-step5-monitoring` | 12〜13 | 構造化ログ、双方向変換（`Converter`）、設計の総括 |

**連載は全 13 章で完結しています（13 / 13）。**

> **これは教材の実装です。そのまま実務に出せません。** 認証・認可を実装していないため、URL を知っていれば誰でも他人のリストを操作できます。ほかに HTML エスケープ・CSRF 対策・JSON エスケープ・スナップショットを扱っていません。詳細は [第 13 章の「扱わなかったこと」](../../../docs/article/zettai/kotlin/chapter13.md) を参照してください。

## テストの書き方

- テストフレームワークは JUnit 5、アサーションは [Strikt](https://strikt.io/) を使います
- テスト名は日本語のバッククォート記法（`` fun `アプリケーション名を返す`() ``）で書きます。何を確かめているかが一文で読めるようにするためです
- テストは純粋関数として書きます。共有された可変状態を持ち込まず、入力から出力を確かめる形にします（第 1 章の主題）
