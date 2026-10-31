---
type: ADR
title: "ADR-041 結合テストの DB は Testcontainers で用意する"
description: "Zettai 連載 Kotlin FP 版の第 9 章で、結合テストの PostgreSQL を docker-compose の service ではなく Testcontainers で用意する決定。既存 Kotlin 版 ADR-009 が退けた 3 つの理由への答えと、失うものを記録する。"
tags: [adr, kotlin-fp, zettai, postgresql, testcontainers]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T06:37:42Z }
---

# ADR-041 結合テストの DB は Testcontainers で用意する

日付: 2026-10-31

## ステータス

2026-10-31 提案されました

## コンテキスト

第 9 章で PostgreSQL の結合テストを `./gradlew check` に含めます。DB の用意の仕方は、既存 Kotlin 版と Kotlin FP 版で分かれました。

既存 Kotlin 版の [ADR-009](ADR-009-integration-test-database.md) は、`docker-compose.yml` に service を足し、Testcontainers を使いませんでした。依存を増やさないこと、読者に実体を見せたいこと、リポジトリの作法に合わせることが理由です。

Kotlin FP 版の Intent は、自前の抽象や手順を作る代わりに、ライブラリに委ねたとき設計がどう変わるかを見ることです。Unit 1 で Testcontainers の PostgreSQL module を version catalog に固定し、container が公開する接続情報だけでつながる最小の結合テスト（`PostgreSqlContainerTest`）を置きました。この結合テストは `clean check` で通っています。

## 決定

結合テストの PostgreSQL は、Testcontainers が起動する container を使う。

1. container は JVM で 1 つだけ起動し、全テストで共有する。起動に時間がかかるため
2. 接続情報は container が公開する JDBC URL、ユーザー、パスワードだけを使い、ポートも認証情報もコードに書かない
3. テストごとにテーブルを作り直し、他のテストの状態に依存しない。テストは逐次で実行する前提とし、並列に実行するなら spec ごとに schema を分ける
4. Docker が使えず container を起動できないとき、テストは skip ではなく失敗するように作る。skip にすると、「DB が無いから通った」のか「実装が正しいから通った」のかが区別できない。実際の見え方は確認していない
5. `docker-compose.yml` に service を足さず、CI にも固定の PostgreSQL service を定義しない

## 検討した案

この表は、ADR-009 にあった案ではなく、この ADR を書くときに整理した案です。

| 案 | 判断 |
| :--- | :--- |
| `docker-compose.yml` に service を足す（既存 Kotlin 版） | 採らない。固定のポートと認証情報が要り、起動と停止の手順が別に要る |
| CI のサービスコンテナだけを使う | 採らない。ローカルと CI の用意の仕方が分かれ、判定が食い違う |
| 組み込みの PostgreSQL 互換 DB | 採らない。本物の PostgreSQL の `jsonb` と `starts_with` の挙動を確かめられない |

## 結果

- ローカルと CI で、同じ手順で DB が用意される設計です。固定の値がコードに残りません。CI での実行は確認していません。
- **失うものがあります。** `docker compose up -d` で DB が動いている様子を見る機会が無くなります。読者が `check` を通すには Docker daemon が必要になり、初回は image の取得に時間とネットワークが要ります。
- `postgres:18-alpine` は動く tag です。minor 版や digest で固定すると、再現性は上がります。
- **Docker が無い環境で、失敗がどう見えるかは確認していません。** Testcontainers は成功した接続方法を記憶するため、環境変数だけでは Docker が無い状態を再現できませんでした。診断を足すかどうかは、実際に困った時点で判断します。
- push 後の CI での挙動は確認していません。

## 関連

- [ADR-009 結合テストの DB](ADR-009-integration-test-database.md)（既存 Kotlin 版。逆の選択をした理由の比較元）
- [ADR-040 JDBC を直接使い、blocking は Dispatchers.IO に閉じ込め、connection は use で閉じる](ADR-040-jdbc-blocking-boundary.md)
