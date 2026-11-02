---
type: ADR
title: "ADR-051 Scala 版は doobie 1.0.0-RC13 と Testcontainers で PostgreSQL に永続化する"
description: "Zettai 連載 Scala 版で、PostgreSQL の読み書きに doobie の 1.0.0-RC13 を使い、結合テストの PostgreSQL を Testcontainers で起動する決定。RC 版を使う理由と、確かめたこと・確かめていないことを記録する。"
tags: [adr, scala, zettai, postgresql]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T13:00:00Z }
---

# ADR-051 Scala 版は doobie 1.0.0-RC13 と Testcontainers で PostgreSQL に永続化する

日付: 2026-11-02

## ステータス

提案（人の承認待ち）

## コンテキスト

第 9 章で、イベントストアを PostgreSQL に置き換えます。判断が要る点は 3 つあります。

| 点 | 候補 |
| :--- | :--- |
| PostgreSQL へのアクセス | doobie / 素の JDBC を `IO` で包む |
| doobie の版 | 1.0.0-RC13（RC 版）。安定版の 1.0 は Maven Central に無い |
| 結合テスト用の PostgreSQL | Testcontainers / docker-compose と CI のサービスコンテナ |

既存の対象の手段は次のとおりです。

| 対象 | アクセス | テスト用 DB |
| :--- | :--- | :--- |
| Kotlin FP | JDBC 系の道具 | Testcontainers |
| Rust | 同期の PostgreSQL クライアント | docker-compose |
| Flix | Java 相互運用で JDBC を直接 | docker-compose と CI のサービスコンテナ |

### 確かめたこと

| 項目 | 結果 |
| :--- | :--- |
| doobie-core と doobie-postgres の最新版 | どちらも 1.0.0-RC13（2026-11-02、Maven Central）。安定版の 1.0.0 は無い |
| RC13 のパッケージ名 | `org.typelevel.doobie`。それ以前の RC12 は groupId が `org.tpolecat` で、パッケージは `doobie` だった。`import doobie.*` はコンパイルが通らなかった |
| `ConnectionIO` が `Monad` であること | 左単位元・右単位元・結合律を、3 つの値で、実際の PostgreSQL に対して確かめた |
| トランザクションの原子性 | `ConnectionIO` の途中で失敗すると、同じトランザクションの前の書き込みが取り消される |
| 版の競合 | 2 つの接続が同じ版から同時に書くと、ちょうど一方だけが成功する（主キーの一意制約が止める） |
| Testcontainers | 2.0.5（artifact は `testcontainers-postgresql`、クラスは `org.testcontainers.postgresql.PostgreSQLContainer`）。Windows の Docker Desktop で `postgres:17-alpine` を起動できた |

### 確かめていないこと

- GitHub Actions での Testcontainers の動作。CI で確かめる
- 接続プール（HikariCP）。今は `Transactor.fromDriverManager` で、トランザクションごとに接続を開く
- doobie の型安全な検査（`check`）の機能
- 安定版の doobie 1.0 が出たときの移行の影響

## 決定

1. **PostgreSQL へのアクセスに doobie 1.0.0-RC13 を使う。** RC 版であることを、記事と README に明記する
2. **結合テストの PostgreSQL は Testcontainers で起動する。** テスト側が自分でコンテナを起動し、JVM の終了で片付く
3. **保存の失敗（接続切れ・SQL の失敗・読めない行）は `IO` の失敗にする。** [ADR-050](ADR-050-scala-either-for-domain-failures.md) の分類で「想定外の失敗」に当たるため。`ZettaiError` に `StorageFailure` を足さない
4. **想定外の失敗は、HTTP で 500 にして、本文に理由を出さない**
5. **テーブル名は SQL に組み込むため、英小文字・数字・下線だけを許す検査を通す**

### 採った理由

- **doobie は `ConnectionIO` というモナドで、複数の SQL を 1 つのトランザクションに合成できる。** 第 9 章の主題「モナドによる安全な永続化」に合う
- **RC 版を選ぶ理由は、安定版が存在しないため。** 1.0 以前の 0.13 系は Cats Effect 2 世代で、3.7 と組み合わせられない
- **Testcontainers はテストが自己完結する。** 環境変数・ポート・既存コンテナとの衝突を考えなくてよい
- **保存の失敗を `IO` の失敗に寄せると、`ZettaiError` の `match` に呼び出し側が対処できない失敗が混ざらない**

### 検討した代替案

| 案 | 採らなかった理由 |
| :--- | :--- |
| 素の JDBC を `IO` で包む | 接続・文・結果集合の後始末を自分で書くことになり、トランザクションの合成が学べない |
| doobie の安定版を待つ | 安定版の 1.0 の時期が分からず、連載が止まる |
| docker-compose と CI のサービスコンテナ | 環境変数とポートの衝突への対処が要る。Flix 版では実際に 5432 番の衝突があった |
| `StorageFailure` を `ZettaiError` に足す | 呼び出し側が型で場合分けできない失敗を、業務の失敗と並べることになる |

## 影響

- 依存が増える（doobie-core、doobie-postgres、Testcontainers）。テストは Docker が動く環境でしか通らない
- 初回のテストは、PostgreSQL のイメージの取得で時間がかかる
- doobie の安定版が出たとき、パッケージ名や API の変更を取り込む必要がありうる
- 応答の本文に保存の失敗の理由を出さない（Flix 版で見つけた情報漏えいと同じ穴を作らない）

### この決定が解かないこと

| 解かないこと | どうするか |
| :--- | :--- |
| 接続プール | 本番の運用を扱う範囲を超える。必要になったら HikariCP を足す |
| 読んで・判断して・書く 3 手順の同時実行 | 版の確認と一意制約で、書き込みを断る。断られたコマンドの再試行は扱わない |
| Docker が使えない環境でのテスト | 結合テストは通らない。インメモリのストアのテストは通る |

## コンプライアンス

- `ZettaiError` に保存の失敗のケースを足さないこと
- 応答の本文に、接続先・SQL・例外のメッセージを出さないこと（`UnexpectedFailureSuite` で確かめる）
- テーブル名を SQL に組み込む前に、`validTableName` を通すこと
