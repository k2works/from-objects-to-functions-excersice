# practicing-zettai プロジェクト固有設定

`SKILL.md` は手順、このファイルは本リポジトリでの実パス・コマンドです。食い違う場合はこのファイルを正とします。

## 実習ディレクトリ（sandbox）

学習者のコードはすべて `apps/sandbox/{対象}/` に置きます。`apps/sandbox/` は Git 追跡しません（`.gitignore` に `apps/sandbox/` がある。無ければ追記を提案する）。

```text
apps/sandbox/
├── kotlin/
├── kotlin-fp/
├── nadesiko/
├── rust/
├── flix/
└── scala/
```

`apps/{対象}/zettai/` は完成実装です。**読み取り専用**とし、学習者のコードの置き場にも作業ディレクトリにもしません。

## 対象の対応表

| 対象 | 記事 | 完成実装（読み取り専用） | sandbox | devShell |
| :--- | :--- | :--- | :--- | :--- |
| Kotlin | `docs/article/zettai/kotlin/` | `apps/kotlin/zettai/` | `apps/sandbox/kotlin/` | `nix develop .#kotlin`（JDK 21 / Gradle） |
| Kotlin FP | `docs/article/zettai/kotlin-fp/` | `apps/kotlin-fp/zettai/` | `apps/sandbox/kotlin-fp/` | `nix develop .#kotlin-fp`（JDK 25 / Gradle 9.7.0 / Arrow） |
| なでしこ3 | `docs/article/zettai/nadesiko/` | `apps/nadesiko/zettai/` | `apps/sandbox/nadesiko/` | `nix develop .#nadesiko`（Node 22 / cnako3 3.8.7） |
| Rust | `docs/article/zettai/rust/` | `apps/rust/zettai/` | `apps/sandbox/rust/` | `nix develop .#rust`（rustc 1.91.1 / just） |
| Flix | `docs/article/zettai/flix/` | `apps/flix/zettai/` | `apps/sandbox/flix/` | `nix develop .#flix`（JDK 21 / Flix 0.79.0） |
| Scala | `docs/article/zettai/scala/` | `apps/scala/zettai/` | `apps/sandbox/scala/` | `nix develop .#scala`（Scala 3.3.9 / sbt / JDK 21） |

## テストコマンド（sandbox の各ディレクトリで実行）

各完成実装の README に書かれたコマンドです。

| 対象 | 品質ゲート | 備考 |
| :--- | :--- | :--- |
| Kotlin | `./gradlew check`（Windows は `gradlew.bat check`） | `check` にコンパイルとテストを集約。Gradle Wrapper を持ち込む |
| Kotlin FP | `bash ./gradlew check`（Windows は `gradlew.bat check`） | Kotest・Cucumber・Testcontainers を含む。Docker が要る |
| なでしこ3 | `make check`（最新の段階のみ）／`make check-all` | `package.json` で cnako3 を固定。初回 `npm install` |
| Rust | `just check`（fmt + clippy + test） | `justfile` が自分で `nix develop` に入り直す |
| Flix | `flix test`（型検査は `flix check`） | 第 9 章から PostgreSQL（`docker compose up -d zettai-db`）が要る |
| Scala | `sbt test`（コンパイルを含む） | 第 9 章以降は Docker（Testcontainers）が要る |

## 完成実装の段階構成（途中章から始めるときの土台と照合の参照先）

- **Kotlin**: `zettai-step1-http` … `zettai-step5-monitoring`（5 段）
- **Kotlin FP**: `zettai-step1-foundation` … `zettai-step7-observability`（7 段）
- **なでしこ3**: `src/stepN/`、`test/stepN/`、`doctest/stepN/`（章の段階ごとにディレクトリを分ける。ADR-015）
- **Rust**: `zettai-stepN-domain` と `zettai-stepN-http` の組（N は段）
- **Flix / Scala**: 単一プロジェクト（層ごとのモジュール／パッケージ）。段階では分かれていないため、途中章の土台は git 履歴ではなく記事のコードから組み立てる

章と段の対応は、各対象の記事 `index.md` と章末の記述、および `docs/development/release_plan*.md` で確認する。推測で対応づけない。

## 環境の前提

- Windows は WSL + Nix を第一推奨。困難な場合のみネイティブで JDK / Node / Rust などを個別導入する。
- 第 9 章以降の永続化は対象で異なる（Kotlin・Flix は `docker-compose.yml` の PostgreSQL、Kotlin FP・Scala は Testcontainers、Rust は PostgreSQL、なでしこ3 は追記型イベントログのファイル）。章に入る前に Docker が使えるか確認する。
- 各対象の最新の環境情報は `docs/article/zettai/index.md` の「実行環境一覧」を参照する。

## 関連資料

- 全章構成と進捗: `docs/article/zettai/index.md`
- 章構成の一次情報（節見出し）: `docs/article/draft.md`
- 言語間の比較: `docs/article/zettai/comparison/index.md`
- 設計判断: `docs/adr/`（記事が参照する ADR を設計判断の場面で紹介する）
- 原著コンパニオンコード（`references/fotf/`）は Git 管理外の参照用。学習の土台にはしない
