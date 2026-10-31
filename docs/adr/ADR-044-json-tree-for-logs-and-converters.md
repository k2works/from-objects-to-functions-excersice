---
type: ADR
title: "ADR-044 ログと converter の JSON に kotlinx-serialization-json の JsonElement だけを使う"
description: "Zettai 連載 Kotlin FP 版の第 12 章で、構造化ログの 1 行と、辞書と JSON の双方向 converter に、kotlinx-serialization-json の JsonElement の木だけを使う決定。ADR-040 の「JSON ライブラリを採らない」を、DB の payload の用途に限って残し、ログと converter の用途には別の決定を足す。"
tags: [adr, kotlin-fp, zettai, json, logging, dependency]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T12:28:33Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-31T12:28:23Z }
---

# ADR-044 ログと converter の JSON に kotlinx-serialization-json の JsonElement だけを使う

日付: 2026-10-31

## ステータス

2026-10-31 承認されました

## コンテキスト

[ADR-040](ADR-040-jdbc-blocking-boundary.md) は、「kotlinx.serialization などの JSON ライブラリは採らない」と決めました。理由は、新しい依存は確認が必要なことと、event の payload は SQL 側（`jsonb_build_object` と bind parameter）で組み立てられるので要らないことでした。

第 12 章の用途は違います。

1. 構造化ログの 1 行を、特殊文字（引用符、バックスラッシュ、改行、絵文字）を含む値からでも、壊れない 1 行の JSON として組み立てる
2. 辞書と JSON の双方向の `Converter` の `from` で、JSON の文字列を読み、壊れた入力を `Left` にする

2 は parser を必要とします。SQL 側では代わりになりません。理由が変わるので、ADR-040 を置き換えるのではなく、用途を分けて決めます。

## 決定

1. **ログと converter の JSON は、`kotlinx-serialization-json` の `JsonElement` の木だけで表す。** 注釈（`@Serializable`）、plugin、`Json` の型ごとの serializer は使わない。木の組み立てと、木から文字列への変換、文字列から木への読み取りだけを使う
2. **ADR-040 の決定は、DB の payload の用途について変えない。** event の payload は SQL 側で組み立て、`payload->>'key'` で読む。この用途に JSON ライブラリを使わない
3. **依存は `zettai-step7-observability` だけに足す。** 版は `gradle/libs.versions.toml` に `kotlinx-serialization = "1.12.0"` として置く。root の共通設定と、他の module の依存は変えない
4. **壊れた入力は、例外を境界で `Either` の `Left` に変える。** `Json.parseToJsonElement` は `SerializationException`（実測では `JsonDecodingException`）を投げるので、`Either.catch` で包む

スパイクで確かめたこと（`JsonSpikeTest`、使い捨て）：版 1.12.0 は Kotlin 2.4.21 でコンパイルでき、引用符、バックスラッシュ、改行、タブ、日本語、絵文字、`U+0000`、`U+2028` を含む木を、改行を含まない 1 行の文字列にして、同じ木に読み戻せた。壊れた JSON は `JsonDecodingException` になった。

## 検討した案

| 案 | 判断 |
| :--- | :--- |
| `kotlinx-serialization-json`（`JsonElement` の木だけ） | **採る**。parser と printer が揃い、escape を任せられる |
| 自前の JSON の ADT と printer（parser なし） | 採らない。`from` を持てず、converter が双方向にならない |
| 自前の ADT、printer、parser | 採らない。escape と数値の扱いを自前で守ることになり、過剰 |
| http4k の JSON module | 採らない。web 層の依存が増え、logging の decorator が web に依存してしまう |
| 既存 Kotlin 版の Kondor | 採らない。この版は、ライブラリへ委ねる範囲を最小にし、`JsonElement` の木に留める |

## 結果

- ログの 1 行は、値に何が入っても、1 行の正しい JSON になります。escape を手で書きません。
- 依存が 1 つ増えます。`zettai-step7-observability` の外には広がりません。
- ログに載る値の `U+2028`（行区切り）は、そのまま出力されます。JSON としては正しいですが、行区切りとして扱う道具では 1 行に見えないことがあります。ログを読む側の道具で確かめます。
- 木の `JsonObject` はキーの順序を保ちます。ログのキーの順序は、組み立てた順になります。
- 注釈と plugin を使わないので、`@Serializable` による型ごとの変換は、この連載の範囲外です。

## 関連

- [ADR-040 JDBC を直接使い、blocking は Dispatchers.IO に閉じ込め、connection は use で閉じる](ADR-040-jdbc-blocking-boundary.md)（DB の payload の用途は変えない）
- [Unit 7 の Bolt 計画](../development/iteration_plan-kotlin-fp-7.md)
