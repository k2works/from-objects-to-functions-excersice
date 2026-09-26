# Zettai — Kotlin 版

Kotlin で Zettai を実装しながら、関数型の設計手法を積み上げていきます。原著『From Objects to Functions』と同じ題材・章構成ですが、コードはすべて本リポジトリで書き起こした自作実装です。

- シリーズ索引：[Zettai シリーズ索引](../index.md)
- 執筆計画：[outline.md](../../outline.md)
- サンプル実装：`apps/kotlin/zettai/`
- 参照元の原著コード：`references/fotf/`（読み取り専用）

## 開発環境

```bash
nix develop .#kotlin
cd apps/kotlin/zettai
./gradlew check
```

| 項目 | 内容 |
| :--- | :--- |
| 言語 | Kotlin 2.x |
| JDK | 21 |
| ビルド | Gradle（マルチプロジェクト、`gradle/libs.versions.toml` でバージョン管理） |
| Web | http4k |
| テスト | JUnit 5、Strikt、Pesticide（DDT） |
| 永続化 | PostgreSQL、Exposed（第 9 章以降） |
| JSON | Kondor（第 12 章） |

第 9 章以降の結合テストで使う PostgreSQL は、リポジトリ既存の `docker-compose.yml` のサービスを利用します。

## 章の目次

| 章 | タイトル | 状態 |
| :--- | :--- | :--- |
| 1 | [新しいアプリケーションを準備する](chapter01.md) | **公開済み** |
| 2 | [関数を使って HTTP を扱う](chapter02.md) | **公開済み** |
| 3 | ドメインの定義とテスト | 未着手 |
| 4 | ドメインとアダプタのモデリング | 未着手 |
| 5 | イベントで状態を変更する | 未着手 |
| 6 | コマンドを実行してイベントを生成する | 未着手 |
| 7 | 関数型手法によるエラーハンドリング | 未着手 |
| 8 | ファンクタを使ってイベントを射影する | 未着手 |
| 9 | モナドによる安全なデータ永続化 | 未着手 |
| 10 | コンテキストを読み込み、コマンドを処理する | 未着手 |
| 11 | アプリカティブによるデータバリデーション | 未着手 |
| 12 | 監視と関数型 JSON | 未着手 |
| 13 | 関数型アーキテクチャの設計 | 未着手 |
