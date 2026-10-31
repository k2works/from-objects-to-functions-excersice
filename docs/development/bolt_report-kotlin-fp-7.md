---
type: Bolt Report
title: "Unit 7 の Bolt 終了報告 - Zettai 連載（Kotlin FP 版）"
description: "Kotlin FP 版 Unit 7 の構造化ログの decorator、JSON の Converter とプロファンクタ、request log、組み立て関数、第 12〜13 章、横断比較、連載全体の品質ゲートの完了結果を記録する。"
tags: [development, report, zettai, kotlin, arrow, logging, unit-7]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T13:24:35Z }
---

# Unit 7 の Bolt 終了報告 - Zettai 連載（Kotlin FP 版）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Unit | 7：監視と総括 |
| Bolt | 7-1 第 12 章 / 7-2 第 13 章 |
| 実施日 | 2026-10-31 |
| 見積もり | US-012: 5 SP / US-013: 3 SP |
| 状態 | **完了**（ADR-045 は人が承認済み。push 後の CI の結果は、下の表に記録する） |

## 成果

- Hub と event store を包む構造化ログの decorator（`zettai.adapter.logging`）と、HTTP の request log の filter（`zettai.web`）を作りました。domain と application は、変えていません。
- ログの 1 行の型 `LogRecord`（`zettai.logging`）で、載せるものを決めました。項目の説明、payload、`PersistenceFailure.reason`、スタックトレース、query、body は、持ち場所がなく、載りません。
- 例外が投げられた操作は、型名だけの 1 行を書いて投げ直します。キャンセルは記録しません。
- `Encoder`、`Decoder`、`Converter`（`zettai.json`）を作りました。`Encoder` と `Decoder` はプロファンクタの形で、法則を property test で確かめました。
- JSON は、`kotlinx-serialization-json` の `JsonElement` の木だけを使いました。承認済みの ADR-040 を置き換えず、用途を分けて ADR-044 で足しました。人の承認を得ています。
- 本番の組み立て箇所 `zettai.app.observedApp` を作り、store、Hub、request の行が揃うことを結合して確かめました。
- 第 12 章、第 13 章、横断比較の Kotlin FP 版の節、ADR-044、ADR-045、設計文書を、実装と同期しました。連載は 13 / 13 章で完結しました。

## 品質結果

| ゲート | 結果 |
| :--- | :--- |
| `gradlew.bat clean check --no-daemon`（全 module） | 成功（1 分 49 秒） |
| Unit 7 のテスト（最終 module） | 220 件成功 |
| step7 の `check` の 3 回連続 | 成功（29〜31 秒。最後のコード変更の後に実行） |
| 記事検査（コード・コード形式・節構成・数値・引用・ADR 参照）、全 13 章 | 違反 0 |
| OKF | ERROR 0、既存 WARN 10 |
| `mkdocs build --strict` | 成功 |
| 5 観点レビュー | 重複を含めて高 11 / 中 約 18 / 低 約 16。高の残件 0（ADR-045 の承認で解消する 2 件を除く）。[レビュー](../review/kotlin-fp-unit7_review_20261031.md) |
| 変異による確認 | request の path に query を含めると `query と body は、ログに載らない` が失敗することを確認。`dimap` の実装を壊す変更は、型が合わず、コンパイルが通らなかった |
| CI（push 後の GitHub Actions） | 未実施（push は人の承認を得て行う） |

## 受入条件とテストの対応

| 条件 | 成功側 | 失敗側 | 端の経路 |
| :--- | :--- | :--- | :--- |
| Hub のログ | `command の成功が、1 行の構造化ログになる` | `command の拒否も 1 行で出る。結果の型が載る` | `query も 1 行で出る`、時計は `steppingClock` で決定的 |
| 機密の除外 | 載せるものが載る（上の完全一致） | `項目の説明は、ログに載らない`、`永続化の失敗の原因は、ログに載らない`、`記録の行に現れるキーは、許可した集合の中だけ` | `利用者名とリスト名に特殊文字があっても、…同じ値に読み戻せる`、`header は、ログに載らない` |
| 例外 | `操作が例外を投げたら、型名だけを載せた 1 行を書いて、そのまま投げ直す` | 同左（メッセージが載らない） | `coroutine のキャンセルは、失敗として記録せず、そのまま投げ直す` |
| store のログ | `追記と読み出しが、stream の数、event の件数、所要時間とともに記録される` | `追記の競合も記録され、結果の型が載る` | `名前変更は、複数の stream への追記を 1 行で記録する`、`stream が無い追記は…`、`payload は載らない` |
| converter | `JSON の木と文字列の Converter は、任意の文字列を含む木を往復する`、`任意の記録は、1 行の文字列にして読み戻すと同じ記録になる` | `壊れた JSON は例外ではなく Left になり、失敗の理由に入力を含めない`、`項目があるのに型が違う行は…` | `Encoder の dimap は合成則を満たす`、`Decoder の dimap は合成則を満たす`、`Int の範囲を超える件数は…` |
| request log | `成功した request が、method、path、status、所要時間とともに 1 行で出る` | `404、400、409、500 も、status とともに記録される` | `query と body は、ログに載らない`、`path に特殊文字があっても…` |
| 境界 | `ログの adapter は、他の adapter と web に依存せず、JDBC も使わない` | `ログの記録の型は、adapter と web と HTTP と JDBC に依存しない（どちらからも使える中立の場所）` | `JSON の変換は、…依存しない`、`app の層は、…どこからも import されない`、`注釈による serializer（@Serializable）は使わない` |
| 組み立て | `1 つの request で、store、query、request の行が、内側から外側の順に出る` | `組み立てた app は、予期しない例外を 500 として request の行に残し、原因を載せない` | — |
| 総括 | 第 13 章の 5 つの代数構造の表の各行にテスト名がある | 対応が無い（検出率を数えていない）ことを明記 | 横断比較の数は、再計測した値（テスト 220、24 ファイル、1599 行） |

decorator を外しても結果が変わらないことは、`decorator を付けても、任意の command 列に対する結果は変わらない` で、Hub の `execute` について確かめました。query と store は、例で確かめた範囲です。

## Unit 6 からの持ち越しの結果

| # | 内容 | 結果 |
| :--- | :--- | :--- |
| 1〜6 | 作業規則（Gradle の実行場所、スクリプト、`sealed`、Red の確認、受入条件の表、レビューの基準） | 守った。ただし、バックスラッシュの問題と Gradle の実行場所は、守り切れなかった（ふりかえり参照） |
| 7 | `showList` と `showRenameForm` の重複 | `userAndList` に寄せた |
| 8 | 名前変更の成功の知らせ | 含めない（計画どおり） |
| 9 | HTTP と PostgreSQL の組み合わせ | 検証せず、第 13 章に「確かめていない」と記載 |
| 10 | `Transaction<Tx>` の戻り値 | 必要が現れなかった |
| 11 | 失敗応答の形式 | プレーンテキストのままと決定。request log はステータスだけを記録 |
| 12 | `describeError` の `else` | 網羅的にして、`else` を除いた |

## 未了と持ち越し

- ADR-045 は、人（kakimomokuri）が承認し、`stable` にしました。第 12 章と第 13 章の承認の記述は、これで事実になります。
- 中の持ち越しは、`kind`・`name`・`outcome` の型化、encoder と decoder のキー名の重複、失敗応答の HTML 化、`U+2028` の扱い、`architecture_backend.md` の既存 Kotlin 版の記述との取り違えの恐れ、です。
- 相関 ID、認証・認可、CSRF 対策は、扱っていません（第 13 章に記載）。
- 他の 3 版が行った、法則を壊して検出率を数えることを、この版は行っていません。

## 関連ドキュメント

- [Unit 7 の Bolt 計画](iteration_plan-kotlin-fp-7.md)
- [ふりかえり](retrospective-kotlin-fp-7.md)
- [開発成果物レビュー](../review/kotlin-fp-unit7_review_20261031.md)
