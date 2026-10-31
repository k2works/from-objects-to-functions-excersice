---
type: ADR
title: "ADR-038 Arrow Core を typed error の表現と合成に限って使う"
description: "Zettai 連載 Kotlin FP 版で、Arrow Core の Either と Raise DSL を domain と application に入れる範囲を決める決定。既存 Kotlin 版 ADR-006 が既製品を使わなかった理由と逆の選択をする根拠と、Arrow を domain rule の代わりにしない制約を記録する。"
tags: [adr, kotlin-fp, zettai, arrow]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T05:57:53Z }
---

# ADR-038 Arrow Core を typed error の表現と合成に限って使う

日付: 2026-10-31

## ステータス

2026-10-31 提案されました

## コンテキスト

既存 Kotlin 版は第 7 章で `Outcome` を自作しました。[ADR-006](ADR-006-outcome-port-type.md) は、`Either` を提供する Arrow などを採らない理由を「第 7 章の主題は `Outcome` を自分で作ることなので、既製品を使うと教材にならない」と書いています。

Kotlin FP 版の Intent は逆です。自前の抽象を作る代わりに、Kotlin の標準機能、Arrow、kotlinx.coroutines に委ねたとき、設計判断がどう変わるかを同じ 13 章で示します。Unit 1 で Arrow Core 2.2.3 を version catalog に固定し、Unit 4 で `Either` と Raise DSL を command handler と Hub に入れました。この決定を、入れた範囲と入れない範囲と共に記録します。

## 決定

Arrow Core は **typed error の表現と合成**に限って使います。

| 使う | 使わない |
| :--- | :--- |
| `Either<ZettaiError, A>` で業務上あり得る失敗を戻り値に出す | domain rule の判断そのもの。遷移表と条件式は Arrow に委ねない |
| Raise DSL の `ensure`、`ensureNotNull`、`bind()` で fail-fast な成功経路を書く | `either { }` の中で `Throwable` や `runCatching` を使うこと。Raise の打ち切りを握りつぶす |
| HTTP 境界で `fold` し、sealed error を網羅した `when` で status へ写像する | Arrow の型クラスや高カインド型の前提。モノイド、ファンクタ、モナドは操作と法則を先にテストする |
| | 自前の `Outcome`、`ContextReader`、`Validation` |

`domain` と `application` は Arrow Core を import してよいものとします。http4k と coroutine runner は引き続き adapter に閉じ込め、境界テストで検査します。

Arrow Fx（`Resource` など）と、第 11 章のエラー蓄積は、この ADR では決めません。それぞれ必要になった章で、実物を試してから判断します。

## 検討した案

| 案 | 判断 |
| :--- | :--- |
| 自前の `Outcome` を作る（既存 Kotlin 版と同じ） | 採らない。比較の価値は既存 Kotlin 版が担っている。同じものを作ると、ライブラリに委ねた差が見えない |
| Kotlin 標準の `Result` | 採らない。失敗が `Throwable` に固定され、`ZettaiError` のような閉じた集合を型に出せない |
| 例外で表す | 採らない。signature に現れず、呼び出し側が失敗を無視できる |

## 結果

- 共変の落とし穴を自分で設計し直さずに済みます。既存 Kotlin 版は `orElse` で `@UnsafeVariance` を使い、実行時に `ClassCastException` を踏みました。代わりに、型がどう共変になるかを自分で設計する経験は得られません。
- 依存が増え、Arrow の API 変更に追従する必要があります。version catalog に固定し、記事に載せるコードは固定した版でコンパイルしたものに限ります。
- error type を足すと、網羅した `when` が compile error で未対応の箇所を示します。第 9 章で `PersistenceFailure` を足すとき、この効果を確かめます。

## 関連

- [ADR-006 ポートの戻り値を `Outcome` にする](ADR-006-outcome-port-type.md)（既存 Kotlin 版。逆の選択をした理由の比較元）
- [ADR-039 関数型 DI は suspend の関数型と constructor function にする](ADR-039-suspend-function-ports.md)
