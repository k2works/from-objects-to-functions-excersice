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

動かして構造化ログ（第 12 章）を見る場合は次を実行し、`http://localhost:8080/todo/uberto/book` を開きます。

```bash
./gradlew :zettai-step5-monitoring:run
```

| 項目 | 内容 |
| :--- | :--- |
| 言語 | Kotlin 2.x |
| JDK | 21 |
| ビルド | Gradle（マルチプロジェクト、`gradle/libs.versions.toml` でバージョン管理） |
| Web | http4k |
| テスト | JUnit 5、Strikt、Pesticide（DDT） |
| 永続化 | PostgreSQL（JDBC 直。第 9 章以降。[ADR-008](../../../adr/ADR-008-event-store-single-table.md) で ORM を使わない判断） |
| JSON | 自前の `Converter`（第 12 章。[ADR-012](../../../adr/ADR-012-own-json-converter.md) で Kondor を採用せず） |

第 9 章以降の結合テストで使う PostgreSQL は、リポジトリ既存の `docker-compose.yml` のサービスを利用します。

## 章の目次

| 章 | タイトル | 状態 |
| :--- | :--- | :--- |
| 1 | [新しいアプリケーションを準備する](chapter01.md) | **公開済み** |
| 2 | [関数を使って HTTP を扱う](chapter02.md) | **公開済み** |
| 3 | [ドメインの定義とテスト](chapter03.md) | **公開済み** |
| 4 | [ドメインとアダプタのモデリング](chapter04.md) | **公開済み** |
| 5 | [イベントで状態を変更する](chapter05.md) | **公開済み** |
| 6 | [コマンドを実行してイベントを生成する](chapter06.md) | **公開済み** |
| 7 | [関数型手法によるエラーハンドリング](chapter07.md) | **公開済み** |
| 8 | [ファンクタを使ってイベントを射影する](chapter08.md) | **公開済み** |
| 9 | [モナドによる安全なデータ永続化](chapter09.md) | **公開済み** |
| 10 | [コンテキストを読み込み、コマンドを処理する](chapter10.md) | **公開済み** |
| 11 | [アプリカティブによるデータバリデーション](chapter11.md) | **公開済み** |
| 12 | [監視と関数型 JSON](chapter12.md) | **公開済み** |
| 13 | [関数型アーキテクチャの設計](chapter13.md) | **公開済み** |
