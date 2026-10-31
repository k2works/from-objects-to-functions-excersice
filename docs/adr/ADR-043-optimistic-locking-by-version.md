---
type: ADR
title: "ADR-043 楽観ロックを stream ごとの版と一意制約で行い、複数の stream を 1 つの transaction で書く"
description: "Zettai 連載 Kotlin FP 版の第 10 章で、並行する command の競合を stream ごとの版と UNIQUE (entity_id, version) で検出する決定。複数の stream への書き込みを 1 つの transaction で確定させる理由と、再試行を呼び出し側の判断とする理由を記録する。"
tags: [adr, kotlin-fp, zettai, postgresql, optimistic-locking]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T11:37:33Z }
---

# ADR-043 楽観ロックを stream ごとの版と一意制約で行い、複数の stream を 1 つの transaction で書く

日付: 2026-10-31

## ステータス

2026-10-31 提案されました

## コンテキスト

読み出し、判断、保存の間に版の確認が無いため、並行する command が互いの書き込みを知らないまま追記します。`CreateToDoList` が同時に走ると `ListCreated` が 2 件記録されることを、`ConcurrentCommandTest` で再現しました。

名前変更（第 11 章）は、旧い名前の stream と新しい名前の stream の 2 つに書きます。2 つへの書き込みは、どちらも成功するか、どちらも残らないかのどちらかでなければなりません。

## 決定

1. **stream ごとに連番の版を持つ。** 版は、その stream に追記済みの event の件数に等しい。読み出しは event と一緒に版を返す
2. **保存は、触れる stream ごとに「読んだ時点の版」を受け取る。** 追記する event には、`読んだ版 + 1` からの連番を付ける
3. **競合は `UNIQUE (entity_id, version)` の違反として検出する。** 読んだ後に別の command が追記していれば、同じ版の挿入が衝突する。SQLState `23505` を `ConcurrentModification` にし、それ以外の失敗は `PersistenceFailure` にする
4. **複数の stream への書き込みは、1 つの transaction で行う。** どれか 1 つでも衝突したら、すべて rollback する。`ConcurrentModification` には、衝突を検出した stream を持たせる。PostgreSQL は最初の衝突で transaction が中断されるため、持つのは最初に検出した 1 つ。in-memory は版の古い stream をすべて持つ。件数は store ごとに異なり、契約テストは件数ではなく型で比べる
5. **自動の再試行はしない。** `ConcurrentModification` を返すところまで。再試行するか、利用者に伝えるかは、呼び出し側の業務判断
6. **in-memory の store も同じ契約を満たす。** `Mutex` で同期し、期待する版と現在の版を比べる。契約テストを PostgreSQL と共有する

スキーマは、`todo_list_event` に `version INTEGER NOT NULL` を足し、`UNIQUE (entity_id, version)` を付ける。結合テストの DB は Testcontainers が毎回作るので、既存データの移行は要らない。`CREATE TABLE IF NOT EXISTS` は既存のテーブルを変えないため、既存データを持つ DB には使えない。

## 検討した案

| 案 | 判断 |
| :--- | :--- |
| `SELECT ... FOR UPDATE` で行をロックする（悲観ロック） | 採らない。新しい stream（行が無い）をロックできない。`CreateToDoList` の競合が、まさにその場合 |
| advisory lock | 採らない。database 固有の機能に依存し、in-memory と同じ契約で扱えない |
| `SERIALIZABLE` | ADR-042 のとおり採らない |
| stream をユーザー単位にして、1 stream にする | 採らない。第 5〜9 章の設計（リスト名ごとの stream）と読み出しの絞り込みを作り直すことになる。複数 stream の transaction で、同じ整合性が得られる |
| 版を event の件数ではなく、列 `id` から導く | 採らない。`id` は全 stream で共有の連番で、stream ごとの連続した版にならない |

## 結果

- 競合は型（`ConcurrentModification`）で現れ、HTTP は 409 に写像できます。
- 同じ stream に並行して追記する command は、片方が拒否されます。追記の内容が互いに干渉しない場合（別々の項目の追加）も拒否します。楽観ロックは保守的です。
- 版の件数は、event を数える以上の意味を持ちません。stream に event を削除する操作は無いので、件数と最後の版は一致します。手作業の修復などで版に欠番ができると、件数から導いた版では追記が衝突し続けます。その場合は `MAX(version)` から導く形に見直します。
- `CREATE TABLE IF NOT EXISTS` は、`version` 列を持たない既存のテーブルを変えません。そのようなテーブルに対しては、最初の追記が実行時に `PersistenceFailure` になります。移行は扱わない決定なので、この連載では、DB を作り直すことを前提にします。
- 名前変更は、旧い stream と新しい stream の両方の版を確認します。変名先が同時に作られた場合も、競合として検出されます。

## 関連

- [ADR-042 transaction の境界を command 1 つの処理に置き、環境を型引数で通す](ADR-042-transaction-scope-and-environment.md)
- [ADR-032 イベントを 1 テーブルに追記し、外のエラーを境界で包む](ADR-032-postgres-event-store.md)（Rust 版。同じ問いに別の言語で答えた例）
- [データモデル設計](../design/data-model.md)
