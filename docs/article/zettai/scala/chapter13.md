---
type: Article
title: "第 13 章 関数型アーキテクチャの設計"
description: "Zettai 連載 Scala 版の最終章。積み上がった量と、層と依存の向き、IO の境界、5 つの代数構造の確かめ方、4 件の ADR を整理する。自作した抽象と Cats の抽象の突き合わせ、IO を使わない副作用を型が止めないこと、テストだけが見つけた欠陥を、実測の数字とともに振り返る。"
tags: [article, zettai, scala, cats-effect, architecture, summary, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T17:00:00Z }
---

# 第 13 章 関数型アーキテクチャの設計

Scala 版の最終章です。第 1 章で立てた問いに答えます。

- 副作用を `IO` の値にすると、核と殻の分離は型で守られるか
- 自作した抽象は、Cats のものと何が違うか
- `IO` の遅延評価は、テストの書き方をどう変えるか
- `Resource` は、後始末を型で保証するか

数字は、この章を書いた時点（2026-11-02）の実装を数えたものです。

## シンプルさを追い求めて

### 積み上がった量

13 章をかけて、1 つの sbt プロジェクトを育てました。`sbt test` の件数は次のように増えました。

| 章 | テーマ | `sbt test` の件数 |
| :--- | :--- | ---: |
| 1 | 準備 | 3 |
| 2 | HTTP | 13 |
| 3 | ドメインとテスト | 16 |
| 4 | 依存の渡し方 | 21 |
| 5 | イベントとモノイド | 30 |
| 6 | コマンド | 43 |
| 7 | エラー | 53 |
| 8 | 射影 | 62 |
| 9 | PostgreSQL | 81 |
| 10 | 文脈 | 90 |
| 11 | バリデーション | 109 |
| 12 | ログと JSON | 130 |

最終の実装は、次のとおりです。

| 項目 | Scala 版 |
| :--- | :--- |
| 本番コード | 26 ファイル、1,000 行（`src/main/scala/`） |
| テストコード | 43 ファイル、1,875 行（`src/test/scala/`）。`test(...)` は 130 本 |
| ADR | 4 件（[ADR-049](../../../adr/ADR-049-scala-acceptance-by-case-class-of-functions.md) から [ADR-052](../../../adr/ADR-052-scala-own-json-and-typed-log-record.md)） |
| 外部の依存 | Cats、Cats Effect、http4s（Ember）、doobie（1.0.0-RC13）。テストに MUnit、Testcontainers、slf4j-nop |

行数は、ファイルの改行の数です。テストのほうが本番より多くなりました。

### 何をシンプルと呼んだか

この連載の題名は、関数型プログラミングで作る「変更を楽に安全にできるソフトウェア」でした。シンプルは、少ないことではなく、**関心が絡み合っていないこと**だと考えました。

- 判断（`Commands.handle`）は、保存先を知らない
- 保存先（`EventStore`）は、判断の中身を知らない
- 読む側（`Projection`）は、書く側の判断を知らない
- ログ（`Logging`）は、ドメインの関数に1行も足さない

「楽」と「シンプル」は別のものです。`Kleisli` や型クラスは、書き慣れれば楽ですが、慣れていない読み手には絡んで見えます。第 4 章でタグレス・ファイナルを採らなかったのは、楽さより、読み手に要る知識の少なさを選んだからです。

## システム全体の設計

### 層と依存の向き

本番コード 26 ファイルのうち、`cats.effect` を取り込むのは 12 ファイルです。残りの 14 ファイルは、`IO` を使いません。

| 層 | ファイル | `cats.effect` |
| :--- | :--- | :--- |
| ドメイン（純粋） | `Domain`、`Events`、`Commands`、`Errors`、`Validate`、`Replay`、`StateTransition`、`Projection`、`Html` | 使わない |
| データ表現（純粋） | `Json`、`Converter`、`Logs`、`Dimap`、`ToDoLists` | 使わない |
| 入口（`IO` を返す） | `Hub`、`CommandHub`、`QueryHub`、`EventStore`、`Process`、`View`、`Foundation` | 使う |
| 殻（外と話す） | `ZettaiRoutes`、`Server`、`Main`、`PostgresStore`、`Logging` | 使う |

依存は、殻から入口、入口からドメインへ、一方向です。ドメインは、入口や殻を取り込みません。

ただし、**この向きを守らせているのは、型ではありません。** ドメインのファイルに `cats.effect` を取り込んで、`println` を書いても、コンパイルは通ります（第 1 章、第 12 章で確かめました）。この表は、結果として数えたものです。

### 効果の境界

`IO` を実行する場所は、アプリケーションの入口 `Main` と、テストの境界だけです。

```scala
object Main extends IOApp:
```

それ以外のコードは、`IO` の値を組み立てるだけです。作っただけでは何も起きず、`IOApp` や `CatsEffectSuite` が実行します。第 1 章で、実行前に `calls` が 0 であることを確かめました。

`Resource` は、サーバー、クライアント、テスト用のテーブルの後始末を担いました。`use` を抜けると、失敗しても止まります。ただし、**Testcontainers の起動は `Resource` ではなく、`lazy val` で行いました。** スイートごとに起動すると遅いので、全スイートで共有し、JVM の終了での片付けは Testcontainers に任せています。

### 5 つの代数構造と、その確かめ方

| 構造 | 章 | Scala 版の確かめ方 |
| :--- | :--- | :--- |
| モノイド | 5 | `TransitionSuite`。Cats の `Monoid` のインスタンス。全パターン。`empty` を壊すと 2 本が落ちる |
| ファンクタ | 7、8 | `FunctorLawsSuite`、`QueryFunctorSuite`。壊れた `map` を見分ける。`Nested` の二重の `map` |
| モナド | 9 | `ConnectionIOSuite`。実際の PostgreSQL に対する、3 つの値の法則。ロールバックの確認 |
| アプリカティブ | 11 | `ValidationSuite`。4 つの法則。「左の誤りだけ」の合成を見分ける |
| プロファンクタ | 12 | `ProfunctorSuite`、`ConverterSuite`。`dimap` は Cats と一致。双方向の束には `imap` |

テストの入力は、乱数ではなく、小さな範囲の**全パターン**を並べました。ScalaCheck は入れていません。範囲の外は何も言えません。モナドの法則は、3 つの値だけで確かめたので、証明ではありません。

## コードへの翻訳

### 型で表した判断

| 判断 | 型 | 守る仕組み |
| :--- | :--- | :--- |
| 業務の失敗の種類 | `enum ZettaiError` | `match` の漏れを `-Werror` の下で止める |
| イベントの種類 | `enum ToDoListEvent` | 同上。ただし、文字列で読み戻す境界は止まらない |
| コマンドと状態遷移 | `enum ToDoListCommand`、`canTransition` | 許す遷移を列挙し、残りを拒否する |
| 入力の誤り | `ValidatedNec[FieldProblem, A]` | 誤りの値を持たない型 |
| ログに載せるもの | `LogRecord` | 載せる場所が型にない |
| 文脈 | `Kleisli[IO, EventStore, A]` | 渡し忘れが実行前に分かる |

### 4 件の ADR

| ADR | 判断 | 状態 |
| :--- | :--- | :--- |
| [ADR-049](../../../adr/ADR-049-scala-acceptance-by-case-class-of-functions.md) | 受け入れテストの経路を、関数値を持つケースクラスで差し替える | 承認 |
| [ADR-050](../../../adr/ADR-050-scala-either-for-domain-failures.md) | 業務の失敗は `Either`、`IO` の失敗は想定外に限る | 承認 |
| [ADR-051](../../../adr/ADR-051-scala-doobie-and-testcontainers.md) | doobie 1.0.0-RC13 と Testcontainers | 承認 |
| [ADR-052](../../../adr/ADR-052-scala-own-json-and-typed-log-record.md) | JSON と `LogRecord` を自作する | 承認 |

### 他の対象との違い

| 論点 | Kotlin FP 版 | Flix 版 | Scala 版 |
| :--- | :--- | :--- | :--- |
| 副作用 | `suspend`（設計） | 効果が型に載る（言語） | `IO` の値（ライブラリ） |
| 失敗の型 | Arrow の `Either` | 標準の `Result` | 標準の `Either`（ADR-050） |
| 文脈の持ち回り | 型引数と引数 | 効果とハンドラ | `Kleisli`（第 10 章） |
| 複数の誤り | `zipOrAccumulate` | `Validation` と `forA` | `Validated` と `mapN`（第 11 章） |
| JSON | `JsonElement` の木 | 自作の木 | 自作の木（ADR-052） |
| 受け入れテスト | Cucumber | 関数値のレコード | 関数値のケースクラス（ADR-049） |
| 永続化のテスト | Testcontainers | docker-compose と CI のサービス | Testcontainers（ADR-051） |

### 採用しなかった道具、試していない道具

| 道具 | 状態 |
| :--- | :--- |
| Cucumber（`cucumber-scala` 9.2.1） | 決定のあとで**試して動いた**（シナリオ 2 件）。`sbt test` には載せず、CLI で実行する確認用に残した |
| タグレス・ファイナル | 3 通りの依存の渡し方で書いて比べ、**採らなかった**（第 4 章） |
| `IOLocal` | 比べて**採らなかった**。設定し忘れが実行時にしか分からない（第 10 章） |
| `EitherT` | **試していない**。連鎖が短く、必要にならなかった |
| circe、log4cats | **試していない**。載せるものを型で決める方針による判断（ADR-052） |
| ScalaCheck | **入れていない**。全パターンを並べた |
| HikariCP（接続プール） | **入れていない**。トランザクションごとに接続を開く |

## 最後の考察

### 扱わなかったこと

- 書き手の同時実行に対する負荷の確認。版の確認と一意制約で断ることは確かめたが、再試行は扱っていない
- 認証と認可
- 外から来る任意の JSON を読むこと。自作の読み込みは、ログの 1 行を読む用途に限る
- 接続プール、ログのレベルの切り替え、分散トレース
- Windows と macOS の Nix での確認。確かめたのは Linux（Docker 上）だけ

### 問いに答える

**副作用を `IO` の値にすると、核と殻の分離は型で守られるか。**

一部は守られました。`IO` を返す関数と返さない関数は、型で区別できます。`IO` を実行する場所は、`Main` とテストだけに寄せられました。

守られなかったのは、`IO` を使わない副作用です。純粋に見える関数に `println` を入れても、コンパイルは通ります。ドメインにログや例外を混ぜることも止められません。**分離を守らせたのは、`IO` の型ではなく、「ドメインのファイルに `cats.effect` を取り込まない」という規律と、ログを外から包む設計でした。** この点で、Flix の効果システムとは違います。

**自作した抽象は、Cats のものと何が違うか。**

| 抽象 | 自作と Cats の関係 | 判断 |
| :--- | :--- | :--- |
| モノイド（第 5 章） | 自作の `StateTransition` に Cats の `Monoid` のインスタンスを与えた。Cats の `Endo` は合成の向きが逆 | 自作を残した。向きの違いを 1 か所に閉じ込める |
| ファンクタ（第 7、8 章） | `Either` と `Nested` の `map` を Cats の `Functor` で確かめた | Cats を使った |
| アプリカティブ（第 11 章） | `Validated` と `mapN` を使った | Cats を使った。自作の `Validation` は作っていない |
| プロファンクタ（第 12 章） | 自作の `dimap` が Cats の `Profunctor` と一致した | 自作を残した（7 行）。`Converter` には `Invariant` を与えた |
| JSON、ログ | Cats に無い | 自作した（ADR-052） |

**置き換えられるものが多い一方で、置き換えなかったものにも理由がありました。** 向きの違いを閉じ込める、ドメインの名前を持たせる、載せるものを型で決める、のどれも、ライブラリが与えない設計の判断です。

**`IO` の遅延評価は、テストの書き方をどう変えるか。**

テストが `IO` を返し、`CatsEffectSuite` が実行する形になりました。値を作っただけでは実行されないので、「実行前は 0 回、実行後は 1 回」のような確認が書けます。時計とログの出力先を引数にすることで、時間と出力もテストが握れました。

**`Resource` は、後始末を型で保証するか。**

サーバー、クライアント、テスト用のテーブルでは、保証しました。`use` を抜けると、失敗しても止まります。保証が及ばなかったのは、`lazy val` で起動した Testcontainers です。Flix 版が Java 相互運用の境界で保証が切れたのと同じく、**型の保証は、使った範囲にしか届きません。**

### テストだけが見つけた欠陥

| 欠陥 | 見つけた章 | 見つけた手段 |
| :--- | :--- | :--- |
| 読み戻しの文字列 `match` が、新しいイベントの種類を読めない | 11 | 全種類のイベントの往復のテスト。コンパイラは見つけなかった |
| コマンドの実行が、読み出しを 2 回していた | 12 | 保管場所のログの並びを確かめるテスト |

どちらも、コンパイラは見逃しました。**1 つの題材、1 つの実装、Scala 3.3.9 と Cats Effect 3.7.1 での観察で、一般論の根拠はありません。**

### 6 つの実装を並べて

| 対象 | 型 | 関数型の道具 | `Outcome` 相当 | 代数的データ型 | 副作用 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Kotlin | あり | あり | 自前で作る | `sealed interface` | 規律 |
| Kotlin FP | あり | Arrow / coroutine | `Either` / Raise | `sealed interface` | `suspend`（設計） |
| なでしこ3 | なし | なし | 辞書で作る | 無い | 規律 |
| Rust | あり | あり | `Result` が言語にある | `enum` | 所有権 |
| Flix | あり | あり | `Result` が標準にある | `enum` | 効果として型に載る |
| Scala | あり | Cats / Cats Effect | `Either` が標準にある | `enum` | `IO` の値（強制はされない） |

「言語がどこまで与えるか」という軸では、Scala は言語そのものより、**ライブラリが型クラスの語彙を与える**位置にあります。Scala 自体は、純粋さを強制しません。

## 連載を終えて

6 つの実装で、同じ 13 章を書きました。

- 設計は変わらず、決めることが変わった
- 言語が与えるものと、設計が決めるものは、別である
- 自作するか借りるかは、一度決めて終わりではなく、その場で確かめて、記録する

最後に、この章の数字を、再び確かめられるようにしておきます。

```bash
nix develop .#scala
cd apps/scala/zettai
sbt test
```

結合テストは Docker が要ります。初回は PostgreSQL のイメージの取得で、時間がかかります。

## この章で書いたコード

- `apps/scala/zettai/src/main/scala/zettai/app/Main.scala`
- `apps/scala/zettai/README.md`

## 参照

- [Scala 版 実行計画](../../../development/release_plan-scala.md)
- [Scala 版 リリース完了報告書](../../../development/release_report-scala-1.0.0.md)
- [横断比較](../comparison/index.md)
