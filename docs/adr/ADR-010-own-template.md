---
type: ADR
title: "ADR-010 テンプレート機構を自前で書き既製のテンプレートエンジンを使わない"
description: "Zettai 連載 Kotlin 版で、Handlebars・Pebble・Thymeleaf などの既製テンプレートエンジンを使わず、自前の小さなテンプレート機構を書く決定。未適用のタグを失敗として扱うという要求、既製品で満たせるか確かめた結果、失うものを記録する。"
tags: [adr, zettai, kotlin]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-27T02:59:39Z }
---

# ADR-010 テンプレート機構を自前で書き既製のテンプレートエンジンを使わない

日付: 2026-09-27

## ステータス

2026-09-27 提案されました

## コンテキスト

第 11 章「ユーザインタフェースの改善」で、HTML の組み立てを Kotlin の文字列テンプレート（第 2 章から使用）から置き換えます。

[UI 設計](../design/ui_design.md) には第 2 章の時点でこう書いていました。

> 第 11 章でバリデーションのエラー表示が必要になった時点で置き換えます。

当初の計画（[Unit 5 の Bolt 終了報告](../development/bolt_report-5.md) の持ち込み）には「**テンプレートエンジンを選定する**（新規ライブラリなのでゲートを置く）」と書いていました。

### 前提が誤っていた

原著のコンパニオンコード（`references/fotf/zettai_step6_validation/src/main/kotlin/com/ubertob/fotf/zettai/ui/TemplateEngine.kt`）を確認したところ、**既製のライブラリを使わず自前で書いていました。**

| 原著の実装 | 内容 |
| :--- | :--- |
| `Template = CharSequence` | テンプレートは文字列そのもの |
| `TemplateTag`（`StringTag`・`ListTag`・`BooleanTag`） | 差し込む値の種類 |
| `renderTemplate(data: TagMap): TemplateOutcome` | 適用結果を `Outcome` で返す |
| `checkForUnappliedTags()` | **未適用のタグが残っていたら失敗** |

第 11 章の主題は「テンプレートエンジンの使い方」ではなく、**タグの適用を型で安全にすること**でした。

### 既製品で満たせるか確かめた

[ADR-004](ADR-004-static-analysis.md) の再検討で得た教訓（**試してから「入れない」と決める**）に従い、既製品で要求を満たせるかを確かめました。

要求は 1 つです。**テンプレートにタグを書いたのにデータを渡し忘れたら、失敗として扱う。**

| 候補 | 未適用タグの扱い |
| :--- | :--- |
| `http4k-template-handlebars` | Handlebars は未定義の変数を**空文字**に展開する。厳格モードはあるが例外を投げる形で、`Outcome` に乗せるにはラップが必要 |
| `http4k-template-pebble` | Pebble も既定では空文字。`strictVariables` で例外を投げられる |
| `http4k-template-thymeleaf` | 未定義の式は評価エラーになるが、例外ベース |

**どれも「例外を投げる」形で、`Outcome` を返す形にはなりません。** ラップすれば乗せられますが、そのラッパを書くくらいなら機構全体が 80 行で書けます。

加えて、既製品を使うと**テンプレートの構文（Handlebars なら `{{#each}}` など）の説明が必要**になり、第 11 章の主題から注意が逸れます。

## 決定

**自前で小さなテンプレート機構を書きます。** 既製のテンプレートエンジンは使いません。

```kotlin
data class Template(val text: String) {
    /** タグを適用する。未適用のタグが残っていたら失敗。 */
    fun render(data: Map<String, TemplateTag>): Outcome<TemplateError, String>
}
```

タグは 3 種類です。

| タグ | 記法 | 用途 |
| :--- | :--- | :--- |
| `StringTag` | `{{name}}` | 値を差し込む |
| `ListTag` | `{{#items}}...{{/items}}` | 繰り返す |
| `BooleanTag` | `{{#hasError}}...{{/hasError}}` | 出し分ける |

**適用後に残ったタグを検出し、名前を挙げて失敗にします。** リストの中の未適用タグも検出します。

## 影響

- **画面が壊れる前に気づけます。** 未適用のタグは実装の誤りなので、HTTP では 500 を返します。利用者に壊れた画面を見せません
- **テンプレートの表現力は低いです。** 条件分岐のネスト、フィルタ、部分テンプレートの読み込みはありません。必要になったら足しますが、Zettai の画面では要りません
- **エスケープ処理がありません。** 利用者の入力をそのまま HTML に差し込むので、XSS の余地があります。**本連載では扱いませんが、実務では必須です。** この限界を第 11 章の記事に書きます
- 既製品を使う場合の選択肢（`http4k-template-*`）は調べてあるので、要求が変わったら乗り換えられます

## 失うもの

正直に書きます。

| 失うもの | 影響 |
| :--- | :--- |
| HTML エスケープ | **XSS の余地が残る。** 記事に明記する |
| テンプレートの構文の豊富さ | Zettai の画面では足りている |
| 実績のあるライブラリの安心感 | 80 行なので読めば分かる |
| エディタの補完・構文チェック | テンプレートは文字列なので支援がない |

## コンプライアンス

- `render` が `Outcome<TemplateError, String>` を返すこと
- 未適用のタグが残ったら失敗になること（リストの中も含む）
- 失敗のメッセージに未適用タグの名前が含まれること
- `zettai.ui` がフレームワークを import しないこと

## 備考

- 起案: Unit 6 のステップ 6-2.3（[Unit 6 の Bolt 計画](../development/iteration_plan-6.md)）
- 関連: [ADR-004](ADR-004-static-analysis.md)（試してから入れないと決める）、[ADR-008](ADR-008-event-store-single-table.md)（ORM を使わない判断）、[UI 設計](../design/ui_design.md)
