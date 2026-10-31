---
type: ADR
title: "ADR-045 ログは decorator で足し、載せるものを LogRecord の型で決める"
description: "Zettai 連載 Kotlin FP 版の第 12 章で、構造化ログを Hub と event store を包む decorator と、HTTP の filter で足し、載せるものを LogRecord の型で決める決定。ログの枠組みを入れず、出力先と時刻を引数で与える理由、利用者名とリスト名を識別子として載せる判断、例外を記録しない範囲を記録する。"
tags: [adr, kotlin-fp, zettai, logging, observability]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T12:44:15Z }
---

# ADR-045 ログは decorator で足し、載せるものを LogRecord の型で決める

日付: 2026-10-31

## ステータス

2026-10-31 提案されました

## コンテキスト

運用で、command と store の操作が何をして、どう終わったかを、利用者の入力を見ずに追いたいです。ログに機密（項目の説明、永続化の失敗の原因、例外のスタックトレース）が混ざってはいけません。

決めることは 4 つです。

1. ログを、どこに、どう足すか
2. 載せるものを、どう決めるか
3. 出力先と時刻を、どう渡すか
4. 利用者名とリスト名（個人を特定しうる識別子）を載せるか

## 決定

1. **ログは decorator で足す。** Hub の 3 つの操作（`execute`、`getList`、`listRows`）を包む `ToDoListHub.logged` と、`EventStore<Tx>` の読み出し 2 つと `readStream`、`append` を包む `EventStore<Tx>.logged` を、`zettai.adapter.logging` に置く。`transaction` は包まない。HTTP の request は、web の filter（`requestLogged`）で記録する。domain と application は、ログを知らない
2. **載せるものは、`LogRecord`（`zettai.logging`）の型で決める。** 自由な `Map<String, Any>` のフィールドを持たせない。操作の種類、結果の型（成功、または error の型名）、利用者名とリスト名、所要時間、stream の数、event の件数、request の method と path と status だけを持つ。項目の説明、payload、`PersistenceFailure.reason`、スタックトレース、query、body は、持ち場所が無いので、載せられない
3. **出力先は `(String) -> Unit`、時刻は `() -> Instant` を引数で与える。** ログの枠組みは入れない。1 回の操作は 1 回の呼び出しで 1 行を書く。並行しても、行が混ざらない
4. **利用者名とリスト名は、識別子として載せる。** 障害の追跡に必要で、人（kakimomokuri）が停止 2 で承認した。個人を特定しうるので、ログの保管とアクセスの管理は運用で守る
5. **例外は記録せず、そのまま投げ直す。** decorator が扱うのは、`Either` で表した結果だけ。予期しない例外は、HTTP では `CatchAll` が 500 にし、request の filter が 500 として記録する

## 検討した案

| 案 | 判断 |
| :--- | :--- |
| 自由なフィールド（`Map<String, Any>`）を持つログ | 採らない。何が載るかを、型ではなく書く人の規律に頼ることになる |
| domain や application の中でログを出す | 採らない。副作用が純粋な関数に入る。既存の境界テストが禁じている |
| ログの枠組み（SLF4J など）を入れる | 採らない。出力先を引数で渡せば足り、グローバルな状態と新しい依存を避けられる。枠組みとの比較は、この連載では試していない |
| `transaction` も包む | 採らない。境界の意味を変えない。読み出しと追記を包めば、記録したいことは足りる |
| 例外もログに載せる | 採らない。スタックトレースと原因に機密（接続先など）が混ざりうる。HTTP は 500 の status だけを記録する |
| `LogRecord` を adapter に置く | 採らない。web の filter が、adapter に依存できない（既存の境界テスト）。adapter にも web にも属さない `zettai.logging` に置く |

## 結果

- 載せないものは、「書かない」という規律ではなく、型に持ち場所が無いことで守られます。機密を含む入力で、ログに載らないことをテストしました。
- decorator を外しても、Hub と store の戻り値は変わりません（任意の command 列で、decorator ありと無しの結果が一致することを property test で確かめた）。
- 例外が投げられた操作は、decorator のログに残りません。HTTP の経路では、request の filter が 500 として記録します。それ以外の経路（例えば Hub を直接呼ぶ batch）では、例外は記録されません。
- 利用者名とリスト名が載るため、ログは個人情報を含みうるデータとして扱います。保管の期間とアクセス権は、この連載の範囲外です。
- 分散トレースとメトリクスの収集基盤は、扱いません。

## 関連

- [ADR-044 ログと converter の JSON に kotlinx-serialization-json の JsonElement だけを使う](ADR-044-json-tree-for-logs-and-converters.md)
- [ADR-039 関数型 DI は suspend の関数型と constructor function にし、runBlocking は web の最外周に置く](ADR-039-suspend-function-ports.md)
- [Unit 7 の Bolt 計画](../development/iteration_plan-kotlin-fp-7.md)
