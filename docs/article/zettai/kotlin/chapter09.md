---
type: Article
title: "第 9 章 モナドによる安全なデータ永続化"
description: "Zettai 連載 Kotlin 版の第 9 章。イベントを PostgreSQL に保存する。接続がある状態でしか実行できない計算を ContextReader として表し、flatMap で繋げてモナドに到達する。1 テーブルだけのイベントストア、結合テストの用意、JSONB の正規化で実際に踏んだ失敗を TDD で示す。"
tags: [article, zettai, kotlin, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-27T05:12:58Z }
---

# 第 9 章 モナドによる安全なデータ永続化

ここまで、データはメモリの中にありました。アプリケーションを再起動すると消えます。

この章で保存します。そして、この連載で 3 つめの抽象概念が現れます。

## 安全に永続化する

何を保存するかを決めます。ここが設計の分かれ道です。

### 状態を保存しない

普通の設計なら、こうします。

| テーブル | 内容 |
| :--- | :--- |
| `todo_list` | リストの名前 |
| `todo_item` | 項目の説明・期限・状態。`todo_list` への外部キー |

項目の状態が変わったら `todo_item` を UPDATE します。

**この設計は採りません。** 第 5 章で決めたことと矛盾するからです。

> 状態を上書きするのではなく、起きたことを並べて残す。

メモリの中では出来事を残しているのに、データベースでは上書きする。これでは「なぜ今この状態なのか」がデータベースを見ても分かりません。

### 出来事を保存する

保存するのは出来事だけです。**テーブルは 1 つになります。**

```sql
CREATE TABLE IF NOT EXISTS todo_list_event (
    id          BIGSERIAL PRIMARY KEY,
    entity_id   TEXT        NOT NULL,
    event_type  TEXT        NOT NULL,
    payload     JSONB       NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

現在の状態は、出来事を読み出して畳み込んで得ます。第 5 章の `replayFrom` と第 8 章の `projectFrom` が、そのまま使えます。

判断したことを書き残します。

**`id` を順序の根拠にしました。** `recorded_at` は使いません。同じ時刻に複数の出来事が記録されうるからです。

**`payload` を `JSONB` にしました。** 出来事の種類ごとにカラムを作ると、出来事を 1 つ足すたびにスキーマ変更が必要になります。

**正規化を論じません。** 追記だけで UPDATE も DELETE もしないので、**正規化が解決する問題（更新時の不整合）が起きません**。正規化を考えるのは、射影を永続化するようになったときです。

詳細は [データモデル設計](../../../design/data-model.md) と [ADR-008](../../../adr/ADR-008-event-store-single-table.md) にあります。

### この設計の限界

正直に書きます。**状態を読むたびに全出来事を畳み込みます。** 出来事が増えると遅くなります。

実務では、ある時点の状態を保存しておく（スナップショット）か、射影を永続化する（リードモデル）ことになります。本連載では扱いません。**「イベントを残す設計には、読み出しのコストという代償がある」ことを知っておいてください。**

## PostgreSQL と結合テスト

データベースを用意します。読者が手元で同じものを動かせることを優先します。

### docker-compose に追加する

リポジトリの `docker-compose.yml` にサービスを足します。

```yaml
  # Zettai（第 9 章）の結合テスト用。既存サービスの定義は変えていない。
  zettai-db:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: zettai
      POSTGRES_USER: zettai
      POSTGRES_PASSWORD: zettai
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U zettai -d zettai"]
      interval: 2s
      timeout: 3s
      retries: 30
```

起動します。

```bash
docker compose up -d zettai-db
```

`healthcheck` を書いているのは、**起動直後はまだ接続を受け付けないから**です。CI で「コンテナは起動したがテストが接続できない」が起きるのを防ぎます。

### Testcontainers を使わなかった

[Testcontainers](https://testcontainers.com/) を使えば、テストが自分でコンテナを起動します。便利ですが採りませんでした。

| 理由 | 内容 |
| :--- | :--- |
| 依存を増やさない | この連載は既製のライブラリを入れる前に「自分で書けないか」を考えます |
| 読者に実体を見せたい | この章の主題の 1 つは「データベースを準備する」ことです。`docker compose up -d` を打つほうが、何が動いているか分かります |
| リポジトリの作法 | 既存の `docker-compose.yml` に mkdocs と plantuml があります |

判断は [ADR-009](../../../adr/ADR-009-integration-test-database.md) に記録しました。

### 接続できないとき skip しない

結合テストは、データベースに接続できないとき **失敗させます**。skip にしません。

```kotlin
/**
 * 結合テスト用のデータベース接続。
 *
 * docker-compose.yml の zettai-db サービスに繋ぐ。
 * 接続できない場合、テストは skip ではなく失敗させる。skip にすると
 * 「DB が無いから通った」のか「実装が正しいから通った」のか区別できない。
 */
object TestDatabase {
```

**緑のまま何も確かめていない状態が、いちばん危ないです。**

### 結合テストを check に含める

`./gradlew check` に結合テストを含めます。別ジョブに分けません。

分けると、**ローカルの `check` と CI の判定が食い違います**。「ローカルでは通るのに CI で落ちる」の典型的な経路です。

実行時間を測りました。`clean check` で **48 秒**です。分ける理由がありません。

CI にも同じ設定のサービスコンテナを置きます。

```yaml
    services:
      zettai-db:
        image: postgres:17-alpine
        env:
          POSTGRES_DB: zettai
          POSTGRES_USER: zettai
          POSTGRES_PASSWORD: zettai
```

接続先の形をローカルと CI で揃えるので、**テスト側に環境ごとの分岐が要りません。**

## Kotlin でデータベースに接続する

ORM を使うかどうかを決めます。

### ORM を使わない

原著は [Exposed](https://github.com/JetBrains/Exposed) を使っています。本連載は使いません。

理由は 2 つです。

**1. ORM が解決する問題が起きません。** テーブルが 1 つ、操作が追記と読み出しの 2 つだけです。関連の読み込みも変更の追跡も N+1 も発生しません。

**2. この章の主題が「自分で組み立てる」ことです。** ORM を使うと「Exposed の使い方」の説明になります。

使うのは PostgreSQL の JDBC ドライバだけです。

### 素朴に書くと何が困るか

JDBC で追記を書くと、こうなります。

<!-- code-check: ignore 問題を示すための擬似コード。この形を避けるのが本節の目的 -->

```kotlin
fun append(events: List<ToDoListEvent>) {
    val connection = DriverManager.getConnection(URL, USER, PASSWORD)
    connection.prepareStatement(INSERT).use { statement ->
        // ...
    }
    connection.close()
}
```

困ることが 3 つあります。

| 困ること | 内容 |
| :--- | :--- |
| 接続の管理が操作の中にある | 操作を 2 つ続けて実行したいとき、接続が 2 回作られる |
| トランザクションにできない | 同じ接続で実行しないと、まとめてコミットできない |
| 失敗が例外で飛ぶ | 第 7 章で `Outcome` にしたのに、ここで例外に戻る |

1 つめと 2 つめが同じ根っこです。**操作が「どの接続で実行するか」を自分で決めてしまっている**のです。

## データベースを準備する

解決策は、**接続を引数にする**ことです。

### 実行を後回しにする

「接続を受け取ってから結果を返す計算」を型にします。

```kotlin
/**
 * 文脈を受け取ってから値を返す計算。
 *
 * データベースアクセスは「接続がある状態で」しか実行できない。
 * その「接続がある状態」を文脈として引数に取り、実行を後回しにする。
 *
 * map で繋ぐとファンクタ、flatMap で繋ぐとモナドになる。
 * flatMap があると「前の結果を使って次を決める」計算を繋げられる。
 */
fun interface ContextReader<CTX, out T> {
    fun runWith(context: CTX): T
```

`ContextReader` は**まだ実行されていない計算**です。`runWith` を呼ぶまで何も起きません。

データベース用に名前を付けます。

```kotlin
/** 接続がある状態で実行できる計算。 */
typealias DbAction<T> = ContextReader<Connection, T>
```

### 操作を書く

追記はこうなります。

```kotlin
    fun append(entityId: EntityId, events: List<ToDoListEvent>): DbAction<Unit> =
        ContextReader { connection ->
            connection.prepareStatement(EventTable.INSERT).use { statement ->
                events.forEach { event ->
                    statement.setString(1, entityId.value)
                    statement.setString(2, event::class.simpleName)
                    statement.setString(3, event.toJson())
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
```

**`append` は SQL を実行しません。** 「接続をもらったら実行する計算」を返すだけです。接続をどこから持ってくるかを、この関数は知りません。

### 実行する

実行は別の場所でします。

```kotlin
/**
 * 接続を用意して計算を実行する。
 *
 * 失敗を Outcome に載せる。例外を投げないので、呼び出し側が失敗を無視できない。
 */
fun <T> DbAction<T>.runOn(connect: () -> Connection): Outcome<ZettaiError, T> =
    try {
        connect().use { connection -> runWith(connection).asSuccess() }
    } catch (e: Exception) {
        PersistenceError("データベースの操作に失敗しました: ${e.message}").asFailure()
    }
```

**ここが唯一の例外の境界です。** JDBC は例外を投げるので、それを `Outcome` に変換します。これより内側では例外を扱いません。

第 7 章で作った `ZettaiError` に、失敗を 1 つ足しました。

```kotlin
/** データベースへの接続・読み書きの失敗（第 9 章）。 */
data class PersistenceError(override val message: String) : ZettaiError
```

`ZettaiError` は `sealed` なので、**足した瞬間に第 7 章の `toResponse` がコンパイルエラーになりました。** 扱い忘れに気づけるのが `sealed` を使う理由です。500 を返す分岐を足しました。

## 関数型スタイルでリモートデータにアクセスする

`ContextReader` を繋ぎます。

### map で繋ぐ

結果を変換したいだけなら `map` です。

```kotlin
    fun <U> map(f: (T) -> U): ContextReader<CTX, U> = ContextReader { context -> f(runWith(context)) }
```

第 7 章の `Outcome.map`、第 8 章の射影の `map` と同じ形です。**中身に関数を適用して、構造は変えません。**

### map では足りない

しかし `map` では書けないことがあります。

**「前の結果を使って、次に実行する計算を決める」**場合です。

たとえばテスト用の初期化で、「テーブルを作ってから、中身を空にする」をやりたい。

```kotlin
        PostgresEventStore.createSchema()
            .flatMap { PostgresEventStore.truncate() }
            .runOn(::connect)
            .fold({ error("テスト用データベースを初期化できません: $it") }, { })
```

最後の `fold` は、**初期化の失敗を捨てないため**です。捨てるとスキーマが作れていないままテストが通ってしまい、「接続できないなら失敗させる」という方針と矛盾します。

`truncate()` は `DbAction<Unit>` です。`map` で繋ぐと、結果が `DbAction<DbAction<Unit>>` になります。**箱が二重になります。**

### flatMap で繋ぐ

二重の箱を潰しながら繋ぐ操作が要ります。

```kotlin
    /** 前の結果を使って次の計算を決める。これがあるとモナド。 */
    fun <U> flatMap(f: (T) -> ContextReader<CTX, U>): ContextReader<CTX, U> =
        ContextReader { context -> f(runWith(context)).runWith(context) }
```

**同じ文脈（接続）を両方に渡しています。** これが効きます。2 つの操作が同じ接続で実行されるので、トランザクションにできます（第 10 章で扱います）。

### 何もしない計算

もう 1 つ部品が要ります。**文脈を使わずに値を返す計算**です。

```kotlin
/** 文脈を使わずに値を返す。モナドの単位元にあたる。 */
fun <CTX, T> pure(value: T): ContextReader<CTX, T> = ContextReader { value }
```

## モナドの力を探る

ここまでの部品を整理します。

1. ある型があり、その中に値を入れられる（`ContextReader<CTX, T>`）
2. 値を包む操作がある（`pure`）
3. 前の結果から次の計算を決めて繋ぐ操作がある（`flatMap`）

### 3 つの性質

この 3 つの部品には、性質があります。

**性質 1: 包んでから繋ぐのは、直接呼ぶのと同じ。**

```text
pure(x).flatMap(f)   は   f(x) と同じ
```

**性質 2: 繋いでから包むのは、何もしないのと同じ。**

```text
m.flatMap { pure(it) }   は   m と同じ
```

**性質 3: 繋ぐ順序をどう括っても同じ。**

```text
m.flatMap(f).flatMap(g)   は   m.flatMap { f(it).flatMap(g) } と同じ
```

第 5 章のモノイドと同じで、当たり前に見えます。そして第 5 章・第 7 章と同じで、**例では足りません。**

```kotlin
    @Test
    fun `結合律が成り立つ`() {
        forAllRandom { random ->
            val context = random.nextInt(-100, 100)
            val m = ContextReader<Int, Int> { it * 2 }

            expectThat(m.flatMap(f).flatMap(g).runWith(context))
                .isEqualTo(m.flatMap { f(it).flatMap(g) }.runWith(context))
        }
    }
```

**テストの形が第 5 章・第 7 章とまったく同じです。** `forAllRandom` も生成の考え方も変えていません。

### 名前を与える

**この構造をモナドと呼びます。**

条件は 3 つです。`pure` がある。`flatMap` がある。上の 3 つの法則を満たす。

### モナドも既に身の回りにある

| 型 | pure | flatMap | 章 |
| :--- | :--- | :--- | :--- |
| `List<T>` | `listOf(x)` | `flatMap` | （標準ライブラリ） |
| `T?` | `x` | `?.let` | （言語機能） |
| `ContextReader<CTX, T>` | `pure` | `flatMap` | 9 |

**`List.flatMap` は誰でも使っています。** それがモナドだったというだけです。第 5 章のモノイド、第 7 章のファンクタと同じで、知っていたことに名前が付きました。

### ファンクタとの関係

`map` は `flatMap` と `pure` で書けます。

```kotlin
    @Test
    fun `map は flatMap と pure で書ける`() {
        forAllRandom { random ->
            val context = random.nextInt(-100, 100)
            val m = ContextReader<Int, Int> { it * 2 }
            val h: (Int) -> String = { "値は $it" }

            expectThat(m.map(h).runWith(context)).isEqualTo(m.flatMap { pure<Int, String>(h(it)) }.runWith(context))
        }
    }
```

**モナドはファンクタでもあります。** `flatMap` があれば `map` は導出できるので、モナドはファンクタより強い構造です。

| 構造 | 持つもの | できること |
| :--- | :--- | :--- |
| ファンクタ | `map` | 中身を変換する |
| モナド | `map` + `flatMap` + `pure` | **前の結果から次の計算を決める** |

「強い」というのは「できることが多い」という意味で、「偉い」という意味ではありません。**`map` で足りるなら `map` を使います。**

## 読み戻す

読み出しを書きます。

```kotlin
    fun readAll(): DbAction<List<ToDoListEvent>> =
        ContextReader { connection ->
            connection.prepareStatement(EventTable.SELECT_ALL).use { statement ->
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(eventFrom(rows.getString("event_type"), rows.getString("payload")))
                        }
                    }
                }
            }
        }
```

読み戻した出来事から、状態を復元できることを確かめます。

```kotlin
    @Test
    fun `読み戻した出来事から状態を復元できる`() {
        val events: List<ToDoListEvent> = listOf(
            ListCreated(user, book),
            ItemAdded(user, book, ToDoItem("write chapter")),
            ItemStatusChanged(user, book, "write chapter", ToDoStatus.InProgress)
        )
        PostgresEventStore.append(entityId, events).runOn(TestDatabase::connect)

        val restored = readAll().fold({ error(it.message) }, { it.replayFrom(ToDoListState.empty) })

        expectThat(restored.listFor(user, book)?.items?.single()?.status).isEqualTo(ToDoStatus.InProgress)
    }
```

**第 5 章の `replayFrom` をそのまま使っています。** 出来事がメモリから来るかデータベースから来るかを、`replayFrom` は知りません。

### JSONB で踏んだこと

JSON のシリアライズは手書きです（第 12 章で双方向変換の型 `Converter` に置き換えます）。読む側の正規表現でつまずきました。

**PostgreSQL の `JSONB` は、保存時に JSON を正規化します。** 書き込んだ文字列とそのまま同じものは返ってきません。コロンの後に空白が入ります。

```text
書き込み: {"user":"uberto","listName":"book"}
読み出し: {"user": "uberto", "listName": "book"}
```

自作のパーサが空白を許していなかったので、結合テストが落ちました。

```kotlin
/**
 * 入れ子のない JSON のフィールドを拾う。
 *
 * コロンの前後の空白を許している。PostgreSQL の JSONB は保存時に JSON を
 * 正規化するため、書き込んだ文字列とそのまま同じものは返ってこない。
 * 実際にこれで結合テストが落ちた。
 */
```

**`JSONB` を選ぶなら、保存した文字列がそのまま返る前提を置けません。** 文字列として厳密に往復させたいなら `TEXT` にします。

## 3 経路目

第 3 章で作った受け入れテストに、**PostgreSQL 経由の経路**を足します。

```kotlin
    companion object {
        val allActions = listOf(DomainOnlyActions(), HttpActions(), PostgresActions())
    }
```

シナリオは **1 文字も変えていません。** 経路の実装だけを足しました。

```kotlin
    /** ハブを組み立てる。出来事の保存先と読み出し元がデータベースになる。 */
    private fun hub(user: User, listName: ListName): ToDoListHub {
        val entityId = EntityId.of(user, listName)

        return ToDoListHub(
            fetchState = { storedEvents().replayFrom(ToDoListState.empty) },
            fetchProjection = { storedEvents().projectFrom(ToDoListProjection.empty) },
            persist = { events -> PostgresEventStore.append(entityId, events).runOn(TestDatabase::connect) }
        )
    }
```

3 経路すべてで同じシナリオが通ります。**永続化を入れても、外から見た振る舞いが変わっていない**ということです。

第 3 章でこう書きました。

> 分離できたことは、同じシナリオが 2 経路で通ることで確かめられます。片方だけ落ちたら、そこに業務ロジックが漏れています

6 章後に、その証拠が 3 つめの経路で確かめられました。

## まとめ

この章でやったことを振り返ります。

- **状態を保存せず、出来事だけを保存しました。** テーブルは 1 つです。正規化を論じないのは、追記だけで更新時の不整合が起きないからです
- **限界も書きました。** 状態を読むたびに全出来事を畳み込みます。実務ではスナップショットかリードモデルが必要になります
- **データベースを `docker compose up -d` で用意しました。** Testcontainers を使わなかったのは、読者に実体を見せたかったからです。接続できないときは skip せず失敗させます
- **ORM を使いませんでした。** テーブルが 1 つ、操作が 2 つで、ORM が解決する問題が起きません
- **接続を引数にして、実行を後回しにしました。** `ContextReader` です。`flatMap` で繋ぐと、同じ接続で複数の操作を実行できます
- **3 つの法則を確かめてから、モナドという名前を与えました。** `List.flatMap` も同じ構造です。テストの形は第 5 章・第 7 章と同じでした
- **`JSONB` の正規化で結合テストが落ちました。** 保存した文字列がそのまま返る前提は置けません
- **受け入れテストに 3 経路目を足しました。** シナリオは 1 文字も変えていません

`flatMap` で「同じ接続を両方に渡す」ことを書きました。これを使えば、複数の操作を 1 つのトランザクションにまとめられます。次の章でそれをやります。

---

## この章で書いたコード

- `ContextReader`: `apps/kotlin/zettai/zettai-step3-persistence/src/main/kotlin/zettai/fp/ContextReader.kt`
- イベントストア: `.../persistence/`（`PostgresEventStore.kt`・`EventTable.kt`・`EventSerialization.kt`・`EventStore.kt`）
- 結合テスト: `.../test/kotlin/zettai/persistence/`（`PostgresEventStoreTest.kt`・`ContextReaderMonadTest.kt`・`TestDatabase.kt`）
- 3 経路目: `.../test/kotlin/zettai/ddt/PostgresActions.kt`
- スキーマ: [データモデル設計](../../../design/data-model.md)

### 動かす手順

```bash
docker compose up -d zettai-db
cd apps/kotlin/zettai
./gradlew check
```

## 参照

- Uberto Barbini『From Objects to Functions』第 9 章。`ContextReader` によるデータベースアクセスとモナドに至る流れは本書に拠っています。本連載のコードはすべて書き起こした自作実装です
- [ADR-008 イベントストアを 1 テーブルで持ち状態を保存しない](../../../adr/ADR-008-event-store-single-table.md)
- [ADR-009 結合テストの DB を docker-compose と CI のサービスコンテナで用意する](../../../adr/ADR-009-integration-test-database.md)
