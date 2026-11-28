---
type: Article
title: "第 9 章 モナドによる安全なデータ永続化"
description: "Zettai 連載 TypeScript FP 版の第 9 章。出来事を PostgreSQL に追記し、接続とトランザクションの後始末を Effect で組み立てる。モナド則を実行の記録で確かめ、中断が問い合わせの終了を待つことと、追記順の指定の欠落をテストが見つけられないことを示す。"
tags: [article, zettai, typescript, effect, postgresql, monad, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T05:22:54Z }
---

# 第 9 章 モナドによる安全なデータ永続化

第 8 章までのイベントは、サーバーのメモリにありました。停止すると消えます。この章では、**PostgreSQL に保存します。** 保存は失敗することがあり、途中で止まることもあります。接続は借り物で、返す必要があります。これらを、実行を表す値 `Effect` で組み立てます。

この章のコードは `zettai-step7-persistence` にあります。第 8 章の step を複製して始めました。版は、`effect` 4.1.6、`pg` 8.23.1、`@types/pg` 8.23.1、テストの PostgreSQL は `postgres:18-alpine` です。

## 安全に永続化する

保存先が増えても、ハブが外に頼る操作は変わりません。出来事の読み取り 2 つと、追記です。第 8 章で決めた 3 つの型を、PostgreSQL で実装します。**ハブも web も、この章では変えていません。**

「安全」に求めることを、先に決めます。

- 出来事は追記だけして、追記順に読み戻せる
- 1 回の保存は、すべて残るか、何も残らないか
- 壊れた行は、黙って捨てない
- 借りた接続は、成功しても、失敗しても、中断されても返す
- 失敗の理由を、応答に出さない

### 同じ型なら、同じ振る舞いか

型が同じでも、振る舞いは同じとは限りません。そこで、**保管が満たすべき振る舞いを、テストの関数にします。** メモリの保管と PostgreSQL の保管に、同じものを当てます。

```typescript
// どの保管も満たすべき振る舞いです。メモリと PostgreSQL に、同じものを当てます。
// emptyStore は、何も入っていない保管を作ります。
export const storeContract = (emptyStore: () => Promise<ToDoListPorts>) => {
```

契約には、追記順、区切り文字を含む名前、生成した説明の読み戻しを入れます。

### PostgreSQL が受け付けない文字

実装の前に、どの文字列が保存できるかを確かめました。

| 文字列 | `text` の列 | `jsonb` の列 |
| :--- | :--- | :--- |
| NUL を含む | 拒否 | 拒否 |
| 対になっていないサロゲート | **エラーなしで、別の値になる** | 拒否 |
| 絵文字、`%`、`/` | そのまま | そのまま |

NUL は、URL の `%00` から入ります。そのままでは、`/todo/uberto/a%00b` がメモリでは 404、PostgreSQL では 500 になります。**型が同じ 2 つの保管で、振る舞いがずれます。**

そこで、名前の生成で、保存できない文字を拒否します。

```typescript
// NUL は PostgreSQL が保存できません。対になっていないサロゲートは、別の値に変わるか拒否されます。
const UNSTORABLE = /[\0\p{Surrogate}]/u
```

これで、名前に NUL を含む要求は、どちらの保管でも 404 です。**項目の説明には、まだ制約がありません。** 画面から項目を足す操作はありませんが、保存できない文字を持つコマンドは、保存で失敗します。長さなどの制約と合わせて、第 11 章で扱う予定です。

## PostgreSQLと結合テスト

テストの PostgreSQL は、Testcontainers で起動します。Docker に接続できない場合は、**テストを飛ばさずに失敗させます。** 飛ばすと、保存を確かめないまま全件成功になります。

```typescript
// Docker が無い場合は、skip せずに失敗します。
let database: TestDatabase
beforeAll(async () => {
  database = await startDatabase()
}, 120_000)
```

テストの前に表を空にするので、テストの順序に結果が依存しません。

## TypeScript でデータベースに接続する

driver は `pg` を使い、SQL を直接書きます。Effect には SQL の仕組みがありますが、PostgreSQL 用の `@effect/sql-pg` の最新版 4.1.7 は、`effect` の 4.1.7 以上を求めます。この版は 4.1.6 に固定しているので、使いません（[ADR-063](../../../adr/ADR-063-typescript-postgresql-persistence.md)）。

`pg` は `Promise` を返します。`Effect` に包む方法は 2 つあり、**失敗の扱いが違います。**

| 包み方 | `Promise` が reject すると |
| :--- | :--- |
| `Effect.promise` | defect |
| `Effect.tryPromise` | 型に現れる失敗 |

第 7 章で、業務の失敗だけを型で運ぶと決めました。データベースの失敗は、想定外の失敗です。`Effect.promise` を使います。

```typescript
// Promise の失敗は、defect になります。データベースの失敗は、想定外の失敗として扱います。
const query = (target: Pool | PoolClient, text: string, values: ReadonlyArray<unknown> = []) =>
  Effect.promise(() => target.query<EventRow>(text, [...values]))
```

## データベースを準備する

テーブルは 1 つです。

```typescript
// 出来事は追記だけします。UPDATE も DELETE もしません。順序の根拠は id です。
const CREATE_TABLE = `
  CREATE TABLE IF NOT EXISTS todo_list_event (
    id          BIGSERIAL PRIMARY KEY,
    entity_id   TEXT        NOT NULL,
    event_type  TEXT        NOT NULL,
    payload     JSONB       NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
  )`
```

`entity_id` は、利用者とリスト名を `/` で結んだ文字列です。名前の中の `/` と `%` は置き換えます。置き換えないと、`a/b` の `c` と、`a` の `b/c` が混ざります。

```typescript
// "/" で区切る前に、名前の中の "%" と "/" を置き換えます。
const escapePart = (part: string): string => part.replaceAll("%", "%25").replaceAll("/", "%2F")
```

利用者の出来事は、`starts_with` で前方一致させます。`LIKE` では、置き換えた名前の `%` がワイルドカードになります。

起動の入口は、接続先の環境変数 `ZETTAI_DATABASE_URL` があれば、PostgreSQL を使います。無ければ、見本の出来事を入れたメモリの保管です。テーブルは起動時に作ります。マイグレーションの道具は、使いません。

## 関数型スタイルでリモートデータにアクセスする

### 借りたものは、返す

接続は、借りて、使って、返します。`Effect.acquireUseRelease` は、この 3 つを 1 つの `Effect` にします。

```typescript
// 借りた接続は、成功、失敗、中断のどれでも返します。失敗した接続は、状態が分からないので捨てます。
const withClient = <A, E>(pool: Pool, use: (client: PoolClient) => Effect.Effect<A, E>) =>
  Effect.acquireUseRelease(
    Effect.promise(() => pool.connect()),
    use,
    (client, exit) => Effect.sync(() => client.release(Exit.isFailure(exit)))
  )
```

解放の関数は、`use` がどう終わったかを `Exit` で受け取ります。トランザクションも、同じ形です。

```typescript
const transaction = <A, E>(client: PoolClient, body: Effect.Effect<A, E>) =>
  Effect.acquireUseRelease(
    query(client, "BEGIN"),
    () => body,
    // 本体が成功した場合だけ確定します。失敗と中断は、取り消します。
    (_begin, exit) => Effect.asVoid(query(client, Exit.isSuccess(exit) ? "COMMIT" : "ROLLBACK"))
  )
```

`try`・`finally` と似ていますが、違いは中断です。`Effect` の中断でも、解放の処理が実行されます。

接続の集まりは、`Scope` の寿命にします。

```typescript
// 接続の集まりを、Scope の寿命にします。スコープを抜けるときに、すべて閉じます。
export const postgresPool = (connectionString: string) =>
  Effect.acquireRelease(
```

この版の仮説は「接続の寿命を `Scope` と `Layer` で表せる」でした。**`Scope` で表せました。** `Layer` でも取得と解放ができることは、小さなテストで確かめました。ただし、依存は第 4 章の決定どおり引数で渡しているので、本体では `Layer` を使っていません。

### 中断は、問い合わせの終わりを待つ

追記が、ほかの接続のロックで待たされている状態を作り、100 ミリ秒のタイムアウトを掛けました。

| ロックを持っていた時間 | 戻るまでの時間 | 保存されたイベント |
| :--- | :--- | :--- |
| 500 ミリ秒 | 511 ミリ秒 | 0 件 |
| 1,500 ミリ秒 | 1,516 ミリ秒 | 0 件 |
| 3,000 ミリ秒 | 3,012 ミリ秒 | 0 件 |

保存は残りません。しかし、**タイムアウトの時間では戻りません。** 戻ったのは、ロックが解放された後でした。送った問い合わせは、`Effect` を中断してもデータベースで動き続けます。取り消しの `ROLLBACK` は、その後に実行されます。問い合わせの時間を区切るには、データベースの側の設定が必要です。この版は設定していません。開発機で 1 回ずつ測った結果です。

## モナドの力を探る

追記は、「接続を借りる」「始める」「1 件ずつ入れる」「確定する」の順に実行します。前の処理の結果を受けて次の処理を決め、失敗したら後ろを飛ばします。これを組み立てるのが `flatMap` です。

`map` は、値を普通の関数で変えます。`flatMap` は、**次の `Effect` を返す関数**をつなぎます。包むこと（`Effect.succeed`）と `flatMap` があり、次の 3 つの法則を満たすものを**モナド**と呼びます。

- **左単位元:** 値を包んでから続きにつなぐことは、続きを直接呼ぶことと同じ
- **右単位元:** 何もせず包み直す続きを足しても、変わらない
- **結合律:** つなぐ処理を、どこからまとめても同じ

結合律があるので、長い処理を途中で関数に切り出せます。切り出し方で、実行の順序も結果も変わりません。

### 法則は、実行して確かめる

`Effect` の法則は、第 7 章と同じく、結果と**実行の記録**で比べます。生成する処理には、失敗するものを混ぜます。失敗の後ろが実行されないことも、記録に現れます。試行回数は 500 回です。

```typescript
// 左単位元: 値を包んでから続けることは、続きを直接呼ぶことと同じです。
const leftIdentity = (bind: Bind, same: Same) =>
  fc.asyncProperty(value, step, async (n, f) =>
    same(
      await observe((log) => bind(Effect.succeed(n), f.of(log))),
      await observe((log) => f.of(log)(n))
    )
  )
```

記録が要る理由は、次の壊し方で分かります。

```typescript
// 続きの処理を 2 回実行します。返す値は、2 回目の結果です。
const runsTwice: Bind = (self, f) => Effect.flatMap(self, (n) => Effect.flatMap(f(n), () => f(n)))
```

この `flatMap` は、**結果だけを比べると左単位元を満たします。** 記録を比べると失敗します。続きが追記なら、2 回保存することになります。

Kotlin 版は、接続を受け取るまで実行しない型（`ContextReader`）を自作します。この版は作りません。`Effect` が、実行を後回しにする値と、その `flatMap` を持っているためです。

## 読み戻す

データベースから返る値には、型がありません。`pg` に型引数を渡しても、**その型は検査されません。** そこで、読み戻した行は、2 段で調べます。

1. **形:** Effect の `Schema` で、必要な値があるか、状態が決めた値かを調べる
2. **意味:** domain の生成の関数で、名前と日付を調べる

```typescript
// 行の payload の形です。形の検査は Schema が、値の意味の検査は domain の生成の関数がします。
const names = { user: Schema.String, listName: Schema.String }
const status = Schema.Literals(TODO_STATUSES)
```

状態の値の一覧は、domain の 1 か所に置き、型もそこから作ります。一覧と型がずれません。

```typescript
// 値の一覧を 1 か所に置き、型はここから作ります。行の読み取りも、同じ一覧を使います。
export const TODO_STATUSES = ["Todo", "InProgress", "Done", "Blocked"] as const
```

種類ごとの読み方は、`Record` に並べます。

```typescript
// 出来事の種類を足すと、キーが足りないので型検査が失敗します。
const decoders: Record<ToDoListEvent["_tag"], (payload: unknown) => Decoded> = {
```

イベントの種類を 1 つ足して型検査すると、4 か所で失敗しました。読み方の表（`TS2741`）、書き込みの形、状態の適用、射影（どれも `TS2366`）です。**保存だけして読めない種類は、作れません。**

読めない行は、黙って捨てずに defect にします。

```typescript
// 読めない行は、黙って捨てずに defect にします。
const decodeRow = ({ event_type, payload }: EventRow): Effect.Effect<ToDoListEvent> =>
```

未知の種類、値の欠落、決めていない状態、空の名前、実在しない日付の 5 つの行で確かめます。捨てるように壊すと、5 件とも失敗しました。代償は、**読めない行が 1 つあると、そのリストと、その利用者の一覧が 500 になる**ことです。

第 5 章の `apply` は、対象の無いイベントを無視します。この規則は変えていません。**壊れた入力を見つけるのは、読み出しの境界の仕事です。**

## ３経路目

受け入れシナリオを、データベースでも実行します。経路ごとに違うのは、保管の作り方だけにします。

```typescript
// 経路ごとに違うのは、出来事を入れた保管の作り方だけです。
export type StoreOf = (history: ReadonlyArray<ToDoListEvent>) => Promise<ToDoListPorts>
```

```typescript
export const databaseRoute = httpRouteOn(async (history) => {
  if (database === undefined) throw new Error("the database is not started")
  // 表を空にしてから、準備の出来事を保存します。
  const store = await database.emptyStore()
  await Effect.runPromise(store.persist(history))
  return store
})
```

3 経路目は、HTTP の経路を PostgreSQL の保管の上で実行します。各シナリオの中身は変えていません。テストのファイルに足したのは、データベースの起動と停止だけです。8 つのシナリオが、3 つの経路で通ります。

### テストが見つけるものと、見つけないもの

保存の実装を、わざと壊しました。

| 壊し方 | 失敗したテスト |
| :--- | :--- |
| トランザクションを使わない | まとまりの原子性 |
| 追記を、トランザクションと別の接続で実行する | 原子性、中断 |
| いつも `COMMIT` する | **中断のテストだけ** |
| 読めない行を捨てる | 壊れた行の 5 件 |
| 名前の `/` を置き換えない | 区切り文字を含む名前の契約 |
| 読み出しの `ORDER BY` を消す | **無し。すべて通る** |

3 行目の理由は、PostgreSQL の振る舞いです。文が失敗したトランザクションは、`COMMIT` を送っても取り消されます。そのため、NUL を含むまとまりのテストは、いつも `COMMIT` する実装でも通ります。違いが出るのは、**文は成功したのに、途中でやめる**場合です。この版では、中断のテストだけがそれを作っています。

最後の行は、テストの限界です。小さな表では、順序を指定しなくても追記順に返りました。**指定の無い順序は、保証されません。** テストが通ることは、順序の保証になりません。ここは SQL を読んで守ります。

### 手順どおりに動かす

開発機の中だけで使う PostgreSQL を起動し、接続先を指定してサーバーを起動しました。保存したリストは、サーバーを再起動しても表示されました。表を削除すると、応答は `internal server error` の 500 でした。理由は応答に出ません。**理由は、どこにも記録していません。** 記録は第 12 章で扱います。

## まとめ

この章では、次を確かめました。

- 保管を差し替えても、ハブと web は変わらない。同じ型の 2 つの実装には、同じ契約のテストを当てる
- 接続とトランザクションは、`acquireUseRelease` で、失敗と中断を含めて後始末できる
- 中断は、実行中の問い合わせの終わりを待つ
- `flatMap` の法則は、結果と実行の記録で確かめる
- 読み戻した行は、形と意味を調べ、読めなければ失敗にする
- テストが通っても、追記順の指定の欠落は見つからない

`npm run check` は、テスト 130 ファイル 993 件が通ります（2026-11-28、Windows、Node 22.23.4）。所要時間は約 40 秒で、上限の 60 秒に収まっています。

ここまでで、読んで、判断して、保存する流れがそろいました。ただし、**読んでから保存するまでの間に、別の保存が入れます。** 次の章では、この 3 つを 1 つのトランザクションにします。

### 型で守れたこと・守れなかったこと

| 対象 | 型検査 | 守ったもの |
| :--- | :--- | :--- |
| イベントを足して、読み方や書き方を直さない | **止める** | `Record` と、戻り値の型を書いた `switch` |
| データベースから返る値の形 | **通る**（`pg` の型引数は検査されない） | `Schema` と domain の生成の関数 |
| トランザクションの欠落、別の接続の使用 | **通る** | 原子性と中断のテスト |
| いつも `COMMIT` する | **通る** | 中断のテスト |
| 追記順の指定の欠落 | **通る** | この版では無し |
| 保存できない文字を含む名前 | **通る** | 名前の生成の関数 |
| 保存できない文字を含む項目の説明 | **通る** | この版では、保存の失敗（500） |

### Effectに置き換えたこと・自作で残したこと

| 対象 | この章での扱い |
| :--- | :--- |
| 実行を後回しにする値と `flatMap` | `Effect` を使う。`ContextReader` は作らない |
| 接続とトランザクションの後始末 | `Effect.acquireUseRelease`、`Effect.acquireRelease`、`Scope` を使う |
| 行の形の検査 | Effect の `Schema` を使う |
| 値の意味の検査 | 自作の生成の関数（`Brand`） |
| SQL と driver | `pg` を使い、SQL は自分で書く。Effect の SQL の仕組みは使わない |
| 依存の組み立て | 引数で渡す。`Layer` は使わない |

## この章で書いたコード

- `apps/typescript-fp/zettai/zettai-step7-persistence/src/adapter/postgres/`（`event-store.ts`、`codec.ts`）
- `apps/typescript-fp/zettai/zettai-step7-persistence/src/main.ts`、`src/domain/todo-list.ts`
- `apps/typescript-fp/zettai/zettai-step7-persistence/test/`（`postgres-store.test.ts`、`monad-laws.test.ts`、`support/store-contract.ts`、`support/database.ts`、`acceptance/database-route.ts`）

## 参照

- Uberto Barbini『From Objects to Functions』第 9 章。章の題材と順序、モナドで保存を組み立てる考え方は本書に拠っています。本連載のコードはすべて書き起こした自作実装です
- [node-postgres](https://node-postgres.com/)
- [PostgreSQL の JSON 型](https://www.postgresql.org/docs/current/datatype-json.html)
- [Testcontainers for Node.js](https://node.testcontainers.org/)
- [Effect の公式サイト](https://effect.website/)
