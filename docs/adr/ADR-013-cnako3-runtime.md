---
type: ADR
title: "ADR-013 なでしこ3 版の処理系に cnako3 を採用する"
description: "Zettai 連載なでしこ3 版のサンプル実装で、Go 実装の gonako ではなく Node 実装の cnako3（npm パッケージ nadesiko3）を採用する決定。簡易 HTTP サーバのプラグインの有無が Zettai の題材の成立を左右すること、Node 22 以上という制約、再検討の条件を記録する。"
tags: [adr, nadesiko, zettai]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-28T03:10:58Z }
---

# ADR-013 なでしこ3 版の処理系に cnako3 を採用する

日付: 2026-09-28

## ステータス

2026-09-28 提案されました

## コンテキスト

[Zettai 連載](../article/outline.md) の 2 言語目として、日本語プログラミング言語なでしこ3 で同じ 13 章を書き起こします。なでしこ3 には実装が複数あり、手本にした既存連載 `references/getting-started-tdd/docs/article/nadesiko/` は **Go 実装の `gonako`**（CLI）を使っています。

Zettai の題材は **HTTP の ToDo リスト Web アプリケーション**です。第 2 章「関数を使って HTTP を扱う」でウォーキングスケルトンを通し、以降の 11 章はその骨格の内側を作り込みます。つまり **HTTP サーバが書けるかどうかが、13 章構成をそのまま辿れるかどうかを決めます。**

- `gonako` の記事群・実装には、ネットワーク機能の記述が 1 つもありません。扱っているサブコマンドは `run` / `lint` / `format` / `doctest` / `doc` だけです
- npm パッケージ `nadesiko3`（CLI は `cnako3`）には簡易 HTTP サーバのプラグインがあります

## 決定

**処理系に cnako3（npm パッケージ `nadesiko3` 3.8.7）を採用します。**

1. バージョンは `apps/nadesiko/zettai/package.json` の 1 箇所で固定する
2. devShell（`ops/nix/environments/nadesiko/shell.nix`）は Node を提供するだけにし、cnako3 は `npm install` で入れる
3. 第 2 章以降の HTTP は、`plugin_httpserver` の『簡易HTTPサーバ起動時』『簡易HTTPサーバ受信時』『簡易HTTPサーバ出力』『簡易HTTPサーバヘッダ出力』『簡易HTTPサーバ移動』と `HTTPメソッド`・`GETデータ`・`POSTデータ` で実装する

### スパイクで確かめたこと

Unit 1 / ステップ 1-1.0 で実機検証しました。

| 確かめたこと | 結果 |
| :--- | :--- |
| cnako3 3.8.7 が動く | **動く**。ただし `engines` が **Node 22 以上**を要求する |
| 組み込みの表明命令 `ASSERT等` がある | **ある**（`plugin_node`） |
| `変数型確認` がある | **ある**。`3` は `"number"`、`「3」` は `"string"` を返す |
| 簡易 HTTP サーバのプラグインが使える | **使える**。ルーティングした URL に `GET` して `<h1>Zettai</h1>` が返ることを確認した |

プラグインの取り込みには相対パスが要ります（`!「./node_modules/nadesiko3/src/plugin_httpserver.mjs」を取り込む`）。パッケージ名だけでは解決しません。

### 検討した代替案

| 案 | 採らなかった理由 |
| :--- | :--- |
| `gonako`（Go 実装 CLI。既存連載と同じ） | ネットワーク機能の根拠が無い。第 2 章以降を CLI 題材に全面再編することになり、Kotlin 版との章の対称性が崩れる。対称性は本シリーズが比較コンテンツを成立させる前提である |
| ブラウザ版（`wnako3`） | ブラウザで動かす前提になり、TDD のテストランナーを CLI で回せない |
| HTTP を諦めて題材を変える | Zettai という題材そのものを変えることになる。それは 2 言語目ではなく別の連載である |

## 影響

- **13 章構成をそのまま辿れます。** 章の番号・タイトル・焦点を Kotlin 版と一致させられ、`comparison/` の比較が成立します
- **Node 22 以上が必要です。** 計画では Node 20 を想定していましたが、スパイクで `engines` が Node 22 以上と判明したため `nodejs_22` に変更しました
- **既存連載のなでしこ3 実装（`gonako` 向け）はそのまま流用できません。** 組み込み命令の実在は個別に確かめる必要があります。実際、`lint` / `format` は cnako3 に存在しませんでした（[ADR-014](ADR-014-own-test-framework.md)）
- npm への依存が入ります。`node_modules/` は git 管理外とし、devShell の起動時に導入します

## コンプライアンス

- `package.json` 以外の場所に cnako3 のバージョンのリテラルが無いこと
- `nix develop .#nadesiko` から `make check` までが追加手順なしで通ること
- 第 2 章で簡易 HTTP サーバによるウォーキングスケルトンが通ること

## 再検討の条件

時期では区切りません。次のいずれかが起きたら再検討します。

1. 簡易 HTTP サーバのプラグインで**書けない要件が第 2 章以降に出てくる**（フォーム POST、クエリ文字列、ステータスコードの制御のいずれかが不足する）
2. cnako3 の実行が遅く、`make check` が 30 秒を超える
3. `gonako` に HTTP サーバが実装され、CLI として同じ題材が書けるようになる

## 備考

- 起案: Unit 1 / Bolt 1-1 のスパイク（ステップ 1-1.0）
- 関連: [執筆計画](../article/outline.md) の「なでしこ3 追加執筆計画」、[Unit 1 の Bolt 計画](../development/iteration_plan-nadesiko-1.md)、[ADR-014](ADR-014-own-test-framework.md)
