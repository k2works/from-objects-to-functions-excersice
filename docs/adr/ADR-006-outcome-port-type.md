---
type: ADR
title: "ADR-006 失敗を Outcome で表しポートの型を変える"
description: "Zettai 連載 Kotlin 版で、null によるエラー表現を Outcome に置き換え、ToDoListFetcher の戻り値という公開された型を変更する決定。第 2 章から書いてきた前提を自ら破る理由、影響範囲、実際に変更が必要だったファイル数、段階的に置き換えた順序を記録する。"
tags: [adr, error-handling, zettai, kotlin]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-27T00:53:07Z }
---

# ADR-006 失敗を Outcome で表しポートの型を変える

日付: 2026-09-27

## ステータス

2026-09-27 提案されました

## コンテキスト

第 2 章から、ポート（`ToDoListFetcher`）の戻り値を `ToDoList?` にしていました。`null` は「見つからない」を表します。

第 2 章の記事でこの問題を認識し、第 7 章まで持ち越すと明記していました。

> 「リストが無い」ことを `null` で表しているのが気になるかもしれません。気になって正解です。**null は「なぜ無いのか」を説明しません。**

第 6 章でコマンドを導入したことで、失敗の種類が増えました。

| 失敗 | `null` で表せるか |
| :--- | :--- |
| リストが見つからない | 表せる（ただし理由は不明） |
| リストが既にある（作成の拒否） | **表せない**（コマンドの戻り値が空リストだった） |
| 項目が見つからない | 表せない |
| 許されない状態遷移 | **表せない** |

第 6 章の時点では、コマンドの処理が「空のイベントリスト」を返して拒否を表していました。呼び出し側は「何も起きなかった」と「拒否された」を区別できません。

### これまでの前提との衝突

第 2 章から「**ポートの型が変わらなければ、ハブより上のコードは変わらない**」と書いてきました。関数の型で依存を表すことの利点として説明してきたものです。

`Outcome` を導入すると、**ポートの型そのものが変わります**。前提が効きません。

## 決定

**`Outcome<ZettaiError, T>` を導入し、ポートの型を変更します。**

```kotlin
typealias ToDoListFetcher = (User, ListName) -> Outcome<ZettaiError, ToDoList>
```

1. 失敗の理由を `ZettaiError` の 4 つの実装（`ListNotFound`・`ListAlreadyExists`・`ItemNotFound`・`InvalidTransition`）で区別する
2. ハブの `getList` と `handle` の戻り値も `Outcome` にする
3. HTTP アダプタは失敗の種類に応じて 404 と 400 を返す
4. **`Outcome` は `zettai.fp` パッケージに置き、フレームワークを import しない。** `DomainBoundaryTest` の検査対象に加える

### 置き換えの順序

一度に全部変えず、次の順で進め、各段階で `./gradlew check` を green に保ちました。

1. `Outcome` と `ZettaiError` を作る（既存コードに影響しない）
2. ポート（`ToDoListFetcher`）の型を変える
3. ハブを追従させる
4. インメモリのアダプタを追従させる
5. HTTP アダプタを追従させる（ここで 404 / 400 の区別が入る）
6. テストと DDT の 2 経路を追従させる

### 変更が必要だったファイル

**6 ファイル**でした。

| ファイル | 変更の内容 |
| :--- | :--- |
| `domain/ToDoListHub.kt` | ポートの型定義、`getList` と `handle` の戻り値 |
| `web/InMemoryToDoListFetcher.kt` | `null` を `Failure` に |
| `web/Zettai.kt` | `?:` を `fold` に。404 / 400 の振り分けを追加 |
| `domain/ToDoListHubTest.kt` | 期待値を `Outcome` に |
| `ddt/DomainOnlyActions.kt` | 戻り値の取り出しを `fold` に |
| `ddt/HttpActions.kt` | 同上 |

**ドメインの型（`ToDoList`・`ToDoItem`・イベント・コマンド・ステートマシン）は 1 行も変わりませんでした。** 状態の畳み込みもモノイドのテストも無変更です。

**受け入れテストのシナリオ（`SeeATodoListDdt`・`ToDoListOwner`）も無変更でした。** 経路の実装だけが変わり、シナリオは業務の言葉のままです。

## 影響

- **失敗の理由が利用者に届きます。** 以前はすべて 404 でした
- **呼び出し側が失敗を無視できません。** `Outcome` は `fold` か `map` を通さないと値が取れません
- **第 2 章と第 4 章の記事のコード例が古くなります。** 記事のコード例検査が検出するので、マーカーを足します
- **「ポートの型が変わらなければ上は変わらない」という説明は、条件付きだったことが明らかになりました。** 正確には「**型が変わらなければ変わらない。型が変われば、その型を触る場所だけが変わる**」です。今回変わったのは 6 ファイルで、ドメインの中核と受け入れテストのシナリオには波及しませんでした

最後の点は、設計の弁護ではなく観測です。**波及しなかったのは、ドメインがポートを知らず、シナリオが実装を知らないからです。** もし `ToDoList` 自体が `Outcome` を持つ設計だったら、ドメインの全体に波及していました。

## 検討した代替案

| 案 | 採らなかった理由 |
| :--- | :--- |
| 例外を投げる | 型に現れないので呼び出し側が無視できる。関数合成もできない |
| Kotlin の `Result<T>` | 失敗が `Throwable` に固定される。`ZettaiError` のような閉じた型で表せない |
| `Either` を提供するライブラリ（Arrow など） | 依存が増える。第 7 章の主題は `Outcome` を**自分で作る**ことなので、既製品を使うと教材にならない |
| `null` のまま、失敗の種類を別の戻り値で返す | 呼び出し側が 2 つの値を見る必要がある。忘れられる |

## コンプライアンス

- `ToDoListFetcher` の戻り値が `Outcome<ZettaiError, ToDoList>` であること
- `zettai.fp` がフレームワークを import しないこと（`DomainBoundaryTest`）
- `Outcome` の `map` がファンクタ則を満たすこと（プロパティベーステスト）
- HTTP アダプタが失敗の種類に応じてステータスコードを返すこと（DDT）

## 備考

- 起案: Unit 4 のステップ 4-2.2（[Unit 4 の Bolt 計画](../development/iteration_plan-4.md)）
- 関連: [ADR-005](ADR-005-property-based-testing.md)（ファンクタ則の検証方法）、[ドメインモデル設計](../design/domain-model.md)
