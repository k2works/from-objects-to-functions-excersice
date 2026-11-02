---
type: Article
title: "第 4 章 ドメインとアダプタのモデリング"
description: "Zettai 連載 Scala 版の第 4 章。依存の渡し方を、関数を持つ値、Kleisli、タグレス・ファイナルの 3 通りで書いて比べ、採る方式を決める。IO#debug で実行中の値を見る。"
tags: [article, zettai, scala, cats-effect, dependency-injection, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T22:00:00Z }
---

# 第 4 章 ドメインとアダプタのモデリング

Scala 版には、この章で比べたい問いがあります。**依存を「引数」で渡すか、「環境」に隠すか、「型パラメータ」に委ねるか**です。同じ処理を 3 通りに書いて並べます。

## ToDo リストを変更するための新しいストーリーを始める

これから ToDo リストの作成や項目追加へ進みます。その前に、読み取り側の依存関係を整理します。HTTP のルーティングがデータの置き場所を知っていると、変更操作を加えるたびに HTTP と保存の都合が絡みます。

必要な契約は「利用者とリスト名を受け取り、見つかればリストを返す」だけです。第 2 章の `Hub` は、これを関数を持つ値として表していました。

<!-- code-check: ignore この章を書いた時点のコード（コミット 296e924）。第 7 章で Hub.getList の戻り値を Either に変えた -->
```scala
final case class Hub(getList: (User, ListName) => IO[Option[ToDoList]])
```

## 関数型の「依存性の注入」を使う

「リストを HTML にする」処理を、依存の渡し方を変えて 3 通りに書きます。結果は同じで、依存の見え方が違います。

### 1. 関数を持つ値を、引数で渡す

<!-- code-check: ignore この章を書いた時点のコード（コミット 296e924）。第 7 章で Hub.getList の戻り値を Either に変えた -->
```scala
  def byHub(hub: Hub, user: User, name: ListName): IO[Option[String]] =
    hub.getList(user, name).map(_.map(Html.render))
```

依存は引数に現れます。呼び出す側は、必ず `Hub` を渡さなければならず、渡し忘れるとコンパイルが通りません。

### 2. 依存を Kleisli に隠す

`Kleisli[IO, Hub, A]` は、`Hub => IO[A]` を包んだ値です。**依存を引数ではなく、処理の型に載せます。**

```scala
  def byReader(user: User, name: ListName): Kleisli[IO, Hub, Option[String]] =
    Kleisli(hub => byHub(hub, user, name))
```

この値を作った時点では、`Hub` はまだ要りません。実行するときに、`run(hub)` で初めて渡します。複数の処理を `flatMap` でつないでも、`Hub` を渡すのは最後の 1 回だけです。長い呼び出しの連鎖で、引数の持ち回りが減ります。

### 3. 依存を型クラスの形で渡す（タグレス・ファイナル）

依存を、効果の種類 `F[_]` をパラメータに持つ `trait` で表します。

```scala
trait ToDoLists[F[_]]:
  def get(user: User, name: ListName): F[Option[ToDoList]]
```

```scala
  def inMemory[F[_]: Applicative](lists: Map[User, List[ToDoList]]): ToDoLists[F] =
    (user, name) => Applicative[F].pure(Domain.findToDoList(lists, user, name))
```

使う側は、`F` が何かを知らず、`Functor` であることだけを要求します。

```scala
  def byTagless[F[_]: Functor](lists: ToDoLists[F], user: User, name: ListName): F[Option[String]] =
    Functor[F].map(lists.get(user, name))(_.map(Html.render))
```

`F` を `IO` にすれば本物の効果で、`cats.Id` にすれば**効果のない純粋な計算**として動きます。

### 3 つの書き方は同じ結果を返す

テストを先に書きました。`View` がまだ無いので Red はコンパイルエラーです（パスは省略しています）。

```bash
[error] -- [E006] Not Found Error: src/test/scala/zettai/ViewSuite.scala:20:4
[error] 20 |    View.byHub(hub, User("uberto"), ListName("book")).map { html =>
```

3 つの版が同じ結果を返すことを、3 つの入力（存在するリスト、存在しないリスト、存在しない利用者）で確かめます。

```scala
  test("Kleisli で渡す版は、引数で渡す版と同じ結果を返す") {
    cases.foldLeft(IO.unit) { case (done, (user, name)) =>
      done *> View.byHub(hub, user, name).flatMap { expected =>
        View.byReader(user, name).run(hub).map(actual => assertEquals(actual, expected))
      }
    }
  }
```

```scala
  test("タグレス・ファイナル版は、IO でも Id でも同じ結果を返す") {
    cases.foldLeft(IO.unit) { case (done, (user, name)) =>
      val pure: Option[String] = View.byTagless[Id](ToDoLists.inMemory[Id](lists), user, name)
      done *> View.byTagless[IO](ToDoLists.inMemory[IO](lists), user, name).map(actual => assertEquals(actual, pure))
    }
  }
```

### どちらを採るか

| 観点 | 関数を持つ値（Hub） | Kleisli | タグレス・ファイナル |
| :--- | :--- | :--- | :--- |
| 依存の見え方 | 引数に現れる | 型 `Kleisli[IO, Hub, A]` に現れる | 型パラメータと制約に現れる |
| 渡し忘れ | コンパイルエラー | `run` を呼ばないと動かない | 型クラスのインスタンスが無いとコンパイルエラー |
| 他の対象との共通性 | Kotlin FP 版、Rust 版、Flix 版と同じ形 | Scala 固有 | Scala 固有 |
| 読み手に要る知識 | 少ない | `Kleisli` の理解 | 高階型 `F[_]` と型クラス |
| 効果の差し替え | `IO` に固定 | `IO` に固定 | `IO` と `Id` を切り替えられる |
| 複数の依存 | 値にフィールドを足す | 環境の型を広げる | 型パラメータや制約が増える |

**この連載では、関数を持つ値（Hub）を採ります。** 他の対象と同じ形で比較できることと、読み手に要る知識が最も少ないことが理由です。

Kleisli は残します。第 10 章で「文脈を持ち回す」ときに、改めて検討します。

タグレス・ファイナルは、効果を `Id` に差し替えられる点が魅力です。ただし、この題材では `IO` を差し替える場面がまだありません。`F[_]` の制約が積み重なる負担のほうが、今は大きいと判断しました。必要になったら入れ直します。

## 関数型コードをデバックする

関数値を渡す設計では、呼び出し先のクラス名から処理を追う方法が使えません。代わりに、関数の入力と出力を観測します。Hub のテストは、モックを使わず、値の比較だけで済みます。

```scala
    View.byHub(hub, User("uberto"), ListName("book")).map { html =>
      assertEquals(html.map(_.contains("<li>write chapter</li>")), Some(true))
    }
```

Flix 0.79.0 には実行中の値を覗く `dbg` が見つかりませんでしたが、Cats Effect の `IO` には `debug` があります。値を変えずに標準出力へ出します。

<!-- code-check: ignore 実行して出力を確かめた単発の例。テストには入れていない -->
```scala
IO.pure(41).map(_ + 1).debug()
```

実行すると、次の 1 行が出ました。

```bash
DEBUG: Succeeded: 42
```

`debug()` は `IO` の途中に挟める観測用の手段です。本番のログではないので、調べ終わったら外します。ログは第 12 章で扱います。

## 関数型ドメインモデリング

依存関係を整理したので、これから変更する値も太くします。ToDo 項目には状態があります。第 2 章で `enum` として置いた状態を使います。

```scala
enum ToDoStatus:
  case Todo, InProgress, Done, Blocked
```

状態を `String` にすると、`"done"`、`"Done"`、`"DONE"` が別の値になります。`enum` なら、許した有限個の状態以外は作れません。`match` で全ケースを書かなければ、コンパイラが報告します。`InProgress` を抜かした `match` を試すと、`-Werror` の下で `[E029] Pattern Match Exhaustivity Error` になり、コンパイルが通りませんでした。

新しい項目は `Todo` から始まります。

```scala
  test("新しい項目は Todo から始まる") {
    assertEquals(ToDoItem.todo("write chapter"), ToDoItem("write chapter", ToDoStatus.Todo))
  }
```

`enum` は、すべての状態を返す `values` を最初から持ちます。Flix 版は一覧を返す関数を自分で書きましたが、Scala では不要です。

```scala
  test("状態はすべて列挙できる") {
    assertEquals(
      ToDoStatus.values.toList,
      List(ToDoStatus.Todo, ToDoStatus.InProgress, ToDoStatus.Done, ToDoStatus.Blocked)
    )
  }
```

状態遷移の規則はまだ実装しません。第 6 章でコマンドを導入するまで、型に場所を作るだけに留めます。

### 不変性は、境界で切る必要がない

Kotlin FP 版では、インメモリの取得関数を作るときに、受け取った `Map` と `List` を `copy` しました。Scala の標準の `Map` と `List` は、デフォルトで不変です。可変な参照を受け取る口が型にないので、**境界で切る処理が要りません。** `Hub.inMemory` は、受け取った `lists` をそのまま閉じ込めています。

## まとめ

この章では、依存の渡し方を 3 通りに書いて比べ、次を確認しました。

- 関数を持つ値、Kleisli、タグレス・ファイナルは、同じ入力に同じ結果を返す
- 違いは、依存が引数・型・型パラメータのどこに現れるか
- この連載は、他の対象と揃えやすく、読み手の負担が少ない Hub を採る。Kleisli は第 10 章で再検討する
- タグレス・ファイナルは、`Id` に差し替えて純粋に動かせる。今は必要がないので採らない
- `IO#debug` で、実行中の値を見られる。Flix には無かった手段
- `enum` は `values` を持ち、不変の `Map` と `List` は境界で切る必要がない

`sbt test` は 21 本が通ります（第 3 章の 16 本に、5 本を足しました）。

次章では、変更をイベントとして残し、現在の状態を畳み込みで導出します。ここで連載が自作してきたモノイドを、Cats のものと突き合わせます。

## この章で書いたコード

- `apps/scala/zettai/src/main/scala/zettai/ToDoLists.scala`
- `apps/scala/zettai/src/main/scala/zettai/View.scala`
- `apps/scala/zettai/src/test/scala/zettai/ViewSuite.scala`

## 参照

- [Cats の Kleisli](https://typelevel.org/cats/datatypes/kleisli.html)
- [Cats Effect の IO](https://typelevel.org/cats-effect/docs/std/io)
