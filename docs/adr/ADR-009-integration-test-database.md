---
type: ADR
title: "ADR-009 結合テストの DB を docker-compose と CI のサービスコンテナで用意する"
description: "Zettai 連載 Kotlin 版で、結合テストのデータベースを Testcontainers ではなく docker-compose と GitHub Actions のサービスコンテナで用意する決定。ローカルと CI で判定を一致させる理由、結合テストを check に含める理由、接続できないときに skip しない理由を記録する。"
tags: [adr, zettai, kotlin]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-27T04:37:32Z }
---

# ADR-009 結合テストの DB を docker-compose と CI のサービスコンテナで用意する

日付: 2026-09-27

## ステータス

2026-09-27 提案されました

## コンテキスト

第 9 章で PostgreSQL への永続化を入れると、結合テストが必要になります。データベースをどう用意するかを決めます。

[執筆計画](../article/outline.md) には方針が書いてありました。

> 第 9 章以降で必要になる PostgreSQL は、リポジトリ既存の `docker-compose.yml` にサービスを追加する方式とします。

選択肢を並べました。

| 案 | ローカル | CI | 評価 |
| :--- | :--- | :--- | :--- |
| A. docker-compose + CI のサービスコンテナ | `docker compose up -d zettai-db` | GitHub Actions の `services:` | **採用** |
| B. Testcontainers | テストが自動で起動 | 同じ | ライブラリが増える。Docker in Docker の考慮が要る |
| C. ローカルにインストールした PostgreSQL | 手で用意 | サービスコンテナ | 読者の環境に依存する。再現性が落ちる |

## 決定

**案 A を採ります。**

- ローカル: `docker-compose.yml` に `zettai-db` サービスを追加（既存 3 サービスの定義は変えない）
- CI: `.github/workflows/kotlin-zettai.yml` に**同じ設定**のサービスコンテナを追加
- 接続先は両方とも `localhost:5432`・DB `zettai`・ユーザー `zettai`

**ローカルと CI で接続先の形を揃えます。** テスト側に環境ごとの分岐を入れません。

### 結合テストを `check` に含める

`./gradlew check` に結合テストを含めます。別ジョブに分けません。

**分けると、ローカルの `./gradlew check` と CI の判定が食い違います。** 「ローカルでは通るのに CI で落ちる」が起きる典型的な経路です。

実行時間を計測しました。`clean check` で **48 秒**です。NFR（5 分以内）を満たすので、分ける理由がありません。5 分を超えたら分離を再検討します。

### 接続できないときは skip せず失敗させる

データベースに接続できない場合、テストを skip しません。失敗させます。

**skip にすると「DB が無いから通った」のか「実装が正しいから通った」のか区別できません。** 緑のまま何も確かめていない状態が、いちばん危ないです。

### Testcontainers を採らなかった理由

Testcontainers は「テストが自分で DB を起動する」ので便利です。採らなかったのは次の理由です。

1. **依存が増える。** [ADR-004](ADR-004-static-analysis.md)・[ADR-005](ADR-005-property-based-testing.md) と同じ判断の形です
2. **読者が DB の実体を意識しなくなる。** 第 9 章の主題の 1 つは「データベースを準備する」ことです。`docker compose up -d` を読者に打ってもらうほうが、何が動いているか分かります
3. 既存の `docker-compose.yml` に mkdocs と plantuml のサービスがあり、**このリポジトリの作法が既に compose である**

## 影響

- 読者は第 9 章以降、テストの前に `docker compose up -d zettai-db` が必要です。第 9 章の記事に手順として書きます
- CI にサービスコンテナが増え、ジョブの起動が少し遅くなります（ヘルスチェック待ち）
- **テスト間のデータ残留に注意が必要です。** 各テストの前に `TRUNCATE` します。DDT の各シナリオでも `prepare()` で消します
- 本番環境の設定ではありません。接続情報はローカル用の固定値です

### 安定性の確認

結合テストを **3 回連続で実行して 3 回 green** であることを確認しました（NFR）。不安定さが出たらリトライで隠さず、原因を特定して記事に書きます。

## 再検討の条件

次のいずれかに当たったら、Testcontainers の導入を検討します。

1. 結合テストの実行順序でデータが混ざり、テストごとに独立した DB が必要になった
2. 複数のバージョンの PostgreSQL に対してテストする必要が出た
3. docker-compose の起動待ちが CI の不安定さの原因になった

**再検討は「結合テストが 2 種類以上の DB 状態を要求したとき」を目安にします。**

## コンプライアンス

- `docker-compose.yml` の `zettai-db` と CI のサービスコンテナが同じ設定であること
- 結合テストが `./gradlew check` に含まれていること
- 接続できないときに skip しないこと
- `./gradlew clean check` が 5 分以内であること

## 備考

- 起案: Unit 5 のステップ 5-2.1・5-2.3（[Unit 5 の Bolt 計画](../development/iteration_plan-5.md)）
- 関連: [ADR-008](ADR-008-event-store-single-table.md)、[データモデル設計](../design/data-model.md)
