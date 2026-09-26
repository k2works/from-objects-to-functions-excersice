---
type: ADR
title: "ADR-002 受け入れテストを Cucumber ではなく DDT / Pesticide で書く"
description: "Zettai 連載 Kotlin 版で、受け入れテストを Gherkin + Cucumber ではなく Pesticide による DDT（Domain Driven Test）で書く決定。第 3 章の主題との衝突、受け入れテスト層を二重に持つコスト、同一シナリオを複数経路で実行する利点、Pesticide の保守状況に対する備えを記録する。"
tags: [adr, testing, zettai, kotlin]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-26T14:02:14Z }
---

# ADR-002 受け入れテストを Cucumber ではなく DDT / Pesticide で書く

日付: 2026-09-26

## ステータス

2026-09-26 提案されました

## コンテキスト

本プロジェクトには [BDD 導入ガイド](../reference/BDD導入ガイド.md) があり、Gherkin と Cucumber-JVM による受け入れテストの書き方が整理されています。Kotlin から使える処理系もあります。

一方、連載の第 3 章の主題は「受け入れテストを改善する／高階関数を使う／ドメインとインフラストラクチャの分離／ドメインからテストを駆動する／DDT を Pesticide に変換する」です。**受け入れテストを組み立てる過程そのものが教材**になっています。

Unit 2 のスパイク（ステップ 2-1.0）で [Pesticide](https://github.com/uberto/pesticide) 1.6.6 の動作を確認しました。

| 確認項目 | 結果 |
| :--- | :--- |
| Kotlin 2.2 での動作 | 問題なし（Pesticide 自体は Kotlin 1.5.30 stdlib に依存しているが実行時に支障なし） |
| JUnit 5.12 での動作 | 問題なし（Pesticide は JUnit 5.7.2 に依存） |
| API | `DdtActions` / `DdtActor` / `DomainOnly` / `Http` / `Ready` / `ddtScenario` / `play` / `step` |

## 決定

受け入れテストは **Pesticide による DDT** で書きます。Gherkin + Cucumber は採用しません。

1. シナリオは `DdtActor` を継承したアクター（`ToDoListOwner`）の言葉で書く
2. 実行経路は `DdtActions` の実装として表し、**同じシナリオをすべての経路で実行する**（現在は「ドメイン直接」と「HTTP 経由」の 2 経路）
3. シナリオの記述に HTTP やドメインの実装を登場させない
4. 各経路は `prepare()` で状態を初期化する。テスト間で状態が漏れると実行順序で結果が変わる

### 採らなかった理由（Cucumber）

1. **第 3 章の主題と衝突する。** 原著が示す受け入れテストの組み立て過程が教材なので、外から完成した仕組みを持ち込むと、読者が学ぶべき部分が「すでに解決済みの問題」に見えてしまう
2. **受け入れテスト層を二重に持つコストに見合わない。** Cucumber と DDT の両方を維持すると、同じシナリオを 2 箇所で表現することになる。1 名体制で全 13 章を書き切るには、層を増やさない判断が要る

BDD が狙う「シナリオが仕様であり、そのまま実行できる」性質は DDT でも満たせます。**むしろ DDT のほうが強い性質を持ちます。** 同じシナリオを複数の経路で実行できるため、経路を差し替えても結果が変わらないことが、ドメインとインフラが分離できている証拠になります。Gherkin のシナリオは通常 1 経路でしか実行しません。

### シナリオの記述言語

コードの語彙は原著のユビキタス言語（`ToDoList`・`ToDoItem`・`ListName`）に合わせ、**英語のメソッド名をバッククォートで囲んで**書きます（`` `has a list` ``・`` `can see the list` ``）。記事の本文は日本語ですが、読者が原著と行き来できるようにするための判断です。

## 影響

- **第 3 章の構成を原著どおりに保てる。** DDT を自前で組み立ててから Pesticide に載せ替える流れをそのまま書ける
- **経路の追加が容易。** 第 9 章で永続化が入ったとき、「PostgreSQL 経由」の経路を足せば、既存のシナリオがそのまま新しい経路でも実行される
- **Pesticide は原著者の個人プロジェクトである。** 更新が止まる、あるいは新しい Kotlin / JUnit で動かなくなるリスクがある。その場合は `DdtActions` 相当のインターフェースを自前で持つ形に退避する。**シナリオの記述（`ToDoListOwner` のステップ）は Pesticide に依存していないため、退避の影響は受け入れテストの基盤部分にとどまる**
- BDD 導入ガイドは本プロジェクトでは参照しない。他のプロジェクトでの利用を妨げるものではない

## コンプライアンス

- `zettai.ddt` パッケージのシナリオに HTTP の語彙が出てこないこと
- 同じシナリオが `DomainOnlyActions` と `HttpActions` の両方で green であること
- 各 `DdtActions` の `prepare()` が状態を初期化していること

## 備考

- 起案: [開発戦略](../development/development_strategy.md) で方針を決め、Unit 2 のステップ 2-2.5 で実装とともに確定した
- 関連: [BDD 導入ガイド](../reference/BDD導入ガイド.md)（不採用）、[バックエンドアーキテクチャ設計](../design/architecture_backend.md)
