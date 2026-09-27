---
type: Design
title: "UI 設計 - Zettai"
description: "Zettai（Kotlin 版）の UI 設計。画面一覧、ワイヤーフレーム、画面遷移図、URL 規約、HTML の組み立て方針を記述する。現時点の画面は ToDo リスト表示の 1 つで、章の進行にあわせて追加する。"
tags: [design, ui, zettai, kotlin]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-26T14:01:38Z }
---

# UI 設計 - Zettai

Zettai の画面を定義します。連載の進行にあわせて画面が増えるため、**現時点の画面と、どの章で何が増えるか**を並べて書きます。

## 画面一覧

| 画面 | URL | 内容 | 章 |
| :--- | :--- | :--- | :--- |
| ToDo リスト表示 | `GET /todo/{user}/{listname}` | 指定した利用者の ToDo リストの項目を一覧表示する。**作成直後の空のリストも表示できる**（第 5 章） | 2・5 |

章の進行で増える予定の画面は次のとおりです。

| 画面 | URL（予定） | 章 |
| :--- | :--- | :--- |
| ToDo リスト一覧 | `GET /todo/{user}` | 未定 |
| 新しいリストの作成 | `POST /todo/{user}` | 未定 |
| 項目の追加 | `POST /todo/{user}/{listname}` | 未定 |
| **リスト名の変更フォーム** | `GET` / `POST /todo/{user}/{listname}/rename` | **11** |

第 6 章でコマンドを導入しましたが、**画面から操作できるようにはしていません**。受け入れテストがハブを直接呼んでいます。第 11 章でリスト名の変更フォームを作り、**画面からコマンドを送る経路を初めて通します**。

## URL 規約

- リソースの階層をそのままパスにする（`/todo/{user}/{listname}`）
- 利用者はパスに含める。認証は本連載の対象外
- 一覧は複数形にせず、`/todo` で統一する

## ワイヤーフレーム

### ToDo リスト表示

```plantuml
@startsalt
{+
  Zettai
  ==
  book
  {#
    write chapter
    insert code
    publish book
  }
}
@endsalt
```

作られたばかりのリストは、項目が 0 件で表示されます（第 5 章）。

```plantuml
@startsalt
{+
  Zettai
  ==
  shopping
  {#
  }
}
@endsalt
```

リスト名と、項目の説明・状態・期限を表示します（状態と期限は第 6 章で追加）。

```plantuml
@startsalt
{+
  Zettai
  ==
  book
  {#
  . | <b>状態 | <b>期限
  write chapter | InProgress | 2026-12-31
  publish book | Todo | .
  }
}
@endsalt
```

第 11 章でテンプレートエンジンを導入し、HTML の組み立て方が変わります。**見た目の作り込みは連載の主題ではない**ため、必要になるまで最小限にとどめます。

## 画面遷移

```plantuml
@startuml
title 画面遷移（第 2 章時点）

[*] --> ToDoリスト表示 : GET /todo/{user}/{listname}

state ToDoリスト表示 : リスト名と項目を表示する
state NotFound : 404

state BadRequest : 400（ListAlreadyExists・InvalidTransition）

state リスト名変更フォーム : 新しい名前を入力する

ToDoリスト表示 --> リスト名変更フォーム : 名前を変更する
リスト名変更フォーム --> ToDoリスト表示 : 成功
リスト名変更フォーム --> リスト名変更フォーム : **バリデーションエラー**\n（すべてのエラーを表示）
ToDoリスト表示 --> NotFound : リストが無い（ListNotFound）
ToDoリスト表示 --> BadRequest : 許されない操作
NotFound --> [*]
BadRequest --> [*]
ToDoリスト表示 --> [*]

note bottom
  現時点の画面は 1 つだけ。
  第 5 章で「作られたが空のリスト」を表示できるようになった。
  第 6 章でコマンドにより項目の追加と状態の変更ができるようになり、
  第 7 章で失敗の種類に応じて 404 と 400 を返すようになった。
  第 11 章でリスト名の変更フォームが加わり、バリデーションエラーは
  フォームに留まってすべてのエラーを表示する（自己ループ）
end note
@enduml
```

## HTML の組み立て方針

現時点では Kotlin の文字列テンプレートで組み立てています（`zettai.web.renderHtml`）。テンプレートエンジンは使っていません。

| 段階 | 方式 | 章 |
| :--- | :--- | :--- |
| 第 2〜10 章 | Kotlin の文字列テンプレート | 2 |
| 第 11 章以降 | **自前の小さなテンプレート機構** | 11 |

第 2 章の時点では「HTML を返せること」が本質で、その作り方は本質ではないためです。

第 11 章で置き換えますが、**既製のテンプレートエンジン（Handlebars・Pebble・Thymeleaf）は使いません。** 自前で小さな機構を書きます。狙いは「テンプレートの書き方」ではなく、**タグの適用を型で安全にすること**です。

| 要素 | 役割 |
| :--- | :--- |
| `Template` | テンプレート本体（文字列） |
| `TemplateTag`（`StringTag`・`ListTag`・`BooleanTag`） | 差し込む値の種類 |
| `renderTemplate(data): Outcome<TemplateError, String>` | 適用結果。**未適用のタグが残っていたら失敗** |

最後の性質が肝です。テンプレートにタグを書いたのにデータを渡し忘れると、既製のエンジンでは空文字になったり、そのまま出力されたりします。**失敗として扱えば、画面が壊れる前に気づけます。**

判断は ADR-010 に記録しています。

## ナビゲーション

現時点では画面が 1 つしかないため、ナビゲーションはありません。第 5 章で ToDo リストの一覧画面が加わった時点で定義します。

## 関連ドキュメント

- [バックエンドアーキテクチャ設計](architecture_backend.md)
- [ドメインモデル設計](domain-model.md)
- [第 2 章 関数を使って HTTP を扱う](../article/zettai/kotlin/chapter02.md)
