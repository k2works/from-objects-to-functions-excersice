---
type: ADR
title: "ADR-045 ログは decorator で足し、載せるものを LogRecord の型で決める"
description: "Zettai 連載 Kotlin FP 版の第 12 章で、構造化ログを Hub と event store を包む decorator と、HTTP の filter で足し、載せるものを LogRecord の型で決める決定。ログの枠組みを入れず、出力先と時刻を引数で与える理由、利用者名とリスト名を識別子として載せる判断、例外を記録しない範囲を記録する。"
tags: [adr, kotlin-fp, zettai, logging, observability]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T13:17:18Z }
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
5. **例外が投げられたら、型名だけの 1 行を書いて、そのまま投げ直す。** `outcome` に `exception:` と例外のクラス名（例：`exception:IllegalStateException`）を載せる。メッセージとスタックトレースは載せない。coroutine のキャンセルは失敗ではないので、記録せず投げ直す。HTTP では `CatchAll` が 500 にし、request の filter が 500 として記録する
6. **出力先は投げない契約とする。** 出力先が例外を投げると、本体の結果が失われる。握りつぶす実装は、障害の原因を隠すので採らない
7. **組み立ては `zettai.app.observedApp` で行う。** store と Hub を decorator で包み、request の filter を最も外に付ける。この連載には起動する `main` が無いので、組み立ては関数として示し、結合して確かめる

## 検討した案

| 案 | 判断 |
| :--- | :--- |
| 自由なフィールド（`Map<String, Any>`）を持つログ | 採らない。何が載るかを、型ではなく書く人の規律に頼ることになる |
| domain や application の中でログを出す | 採らない。副作用が純粋な関数に入る。既存の境界テストが禁じている |
| ログの枠組み（SLF4J など）を入れる | 採らない。出力先を引数で渡せば足り、グローバルな状態と新しい依存を避けられる。枠組みとの比較は、この連載では試していない |
| `transaction` も包む | 採らない。境界の意味を変えない。読み出しと追記を包めば、記録したいことは足りる |
| 例外のメッセージとスタックトレースもログに載せる | 採らない。原因に機密（接続先など）が混ざりうる |
| 例外を記録しない（初版の決定） | 改めた。例外が投げられた操作が、ログから丸ごと消え、どの操作で落ちたかが分からなくなるため。型名だけなら、機密の懸念なしに足せる |
| `LogRecord` を adapter に置く | 採らない。web の filter が、adapter に依存できない（既存の境界テスト）。adapter にも web にも属さない `zettai.logging` に置く |

## 結果

- 載せないものは、「書かない」という規律ではなく、型に持ち場所が無いことで守られます。機密を含む入力で、ログに載らないことをテストしました。
- decorator を外しても、Hub の `execute` の結果は変わりません（任意の command 列で、decorator ありと無しの結果が一致することを property test で確かめた）。query と store の戻り値が変わらないことは、例で確かめた範囲です。
- 例外が投げられた操作は、型名だけの 1 行が残ります。decorator を通らない経路の例外（組み立ての外で起きる失敗）は、記録されません。
- `append` の記録は、transaction の中で書かれます。追記が成功と記録されたあとで、commit の失敗などで rollback されても、その行は `success` のまま残ります。運用では、Hub の command の行を、結果の正とします。この食い違いは、テストで再現していません。
- 複数の stream への追記は、利用者名だけを載せ、リスト名は載せません。`streams` が 2 以上のときは、他にも対象があると読みます。
- `kind`、`name`、`outcome` は文字列です。型が決めているのは、載せられる項目の種類で、値の集合ではありません。呼び出し側が、入力の値を `name` に渡す誤りは、型では防げません。
- ログの行の順序は、操作の終了順です。`at` は開始時刻なので、時刻順にはなりません。
- 相関 ID（request ID）はありません。1 回の request で出る行（store、query、request）は、時刻と利用者名でしか結べません。
- 利用者名とリスト名が載るため、ログは個人情報を含みうるデータとして扱います。保管の期間とアクセス権は、この連載の範囲外です。
- path に利用者名とリスト名が含まれるため、request の行にも識別子が載ります。
- 値に `U+2028` などの行区切りが入ると、行区切りを特別に扱う道具で、1 行に見えないことがあります。ログを読む道具の側で確かめる課題として残します。
- 分散トレースとメトリクスの収集基盤は、扱いません。

## 関連

- [ADR-044 ログと converter の JSON に kotlinx-serialization-json の JsonElement だけを使う](ADR-044-json-tree-for-logs-and-converters.md)
- [ADR-039 関数型 DI は suspend の関数型と constructor function にし、runBlocking は web の最外周に置く](ADR-039-suspend-function-ports.md)
- [Unit 7 の Bolt 計画](../development/iteration_plan-kotlin-fp-7.md)
