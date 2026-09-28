---
type: Bolt Report
title: "Unit 1 の Bolt 終了報告 - Zettai 連載（なでしこ3 版）"
description: "Zettai 連載なでしこ3 版 Unit 1（実行環境・テスト基盤と第 1 章）の終了報告。全 21 ステップの実績、スパイクで覆った 2 件の仮定と新たに見つかった終了コードの問題、自作したテスト基盤の構成、承認ゲートが同期的に機能しなかったこと、Unit 2 への持ち込み事項を記録する。"
tags: [development, report, ai-dlc, bolt, zettai, nadesiko, unit-1]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-28T03:10:58Z }
---

# Unit 1 の Bolt 終了報告 - Zettai 連載（なでしこ3 版）

**報告日**: 2026-09-28

## 概要

なでしこ3 版 Unit 1（実行環境・テスト基盤と第 1 章）を完了しました。計画は [Unit 1 の Bolt 計画](iteration_plan-nadesiko-1.md) です。

| 項目 | 計画 | 実績 |
| :--- | :--- | :--- |
| ステップ | 21 | **21 完了** |
| 承認ゲート | 10 | **0 / 10（同期的な停止なし）** |
| 検証負荷 | 11 | 11 |
| リードタイム | 10 営業日（参考値） | **1 日** |
| 人が検証に使った時間 | 計測する | **未計測** |
| 公開した章 | 1 | **1**（[第 1 章](../article/zettai/nadesiko/chapter01.md)） |
| ADR | 2 | **2**（[ADR-013](../adr/ADR-013-cnako3-runtime.md)・[ADR-014](../adr/ADR-014-own-test-framework.md)） |

## 成果物

| 種類 | 内容 |
| :--- | :--- |
| 実行環境 | `ops/nix/environments/nadesiko/shell.nix`、`flake.nix` への登録 |
| サンプル実装 | `apps/nadesiko/zettai/`（`package.json`・`Makefile`・`README.md`・`src/`・`test/`・`doctest/`） |
| 記事 | `docs/article/zettai/nadesiko/index.md`、`chapter01.md` |
| ADR | 処理系の選定、テスト基盤の自作 |
| 検査 | `check_article_code.py` の `nako3` 対応、`check_adr_references.py` の多言語対応 |

## スパイクの結果

未解決の仮定 5 件を実機で潰しました。**2 件が想定と違い、計画に無かった仮定が 1 件見つかりました。**

| # | 仮定 | 結果 |
| :--- | :--- | :--- |
| 1 | cnako3 3.8.7 が Node 20 で動く | **違った**。`engines` が **Node 22 以上**を要求する |
| 2 | `ASSERT等` が cnako3 に存在する | そのとおり |
| 3 | `変数型確認` が存在する | そのとおり（`"number"` / `"string"` を返す） |
| 4 | cnako3 に lint / format がある | **違った**。どちらも無い（`gonako` にはあった）。`cnako3 -A` の標準エラー出力で文法検査を代替した |
| 5 | 簡易 HTTP サーバのプラグインが使える | そのとおり。`GET /todo` に `<h1>Zettai</h1>` が返ることを確認した |
| 6 | （計画に無かった）**cnako3 は実行時エラーでも終了コードが 0** | 新たに判明。`プロセス終` で明示できる |

仮定 1・4 は計画と執筆計画に反映しました。仮定 6 が本 Unit の設計を一番大きく変えました。

## 自作したテスト基盤

なでしこ3 にはテストフレームワークがありません。次を自作しました（[ADR-014](../adr/ADR-014-own-test-framework.md)）。

| 部品 | 場所 | 役割 |
| :--- | :--- | :--- |
| 検証関数 | `test/helper.nako3` の `検証` | **値を比べる前に `変数型確認` 同士を比べる**。`=` が型を区別しないため |
| 結果報告 | `test/helper.nako3` の `テスト結果報告` | 失敗が 1 件でもあれば `1でプロセス終` を呼ぶ |
| テストランナー | `Makefile` の `test` | 終了コードと、出力に `[エラー]` が含まれるかの **2 つで判定する** |
| 文法検査 | `Makefile` の `lint` | `cnako3 -A` の標準エラー出力が空でなければ文法エラー |
| 自己テスト | `test/helper_test.nako3` | 検証関数そのものを組み込みの `ASSERT等` で確かめる |

`make check` は 3.3 秒で終わります（NFR は 30 秒以内）。

## デモ項目の実演

| # | デモ項目 | 結果 |
| :--- | :--- | :--- |
| 1 | `nix develop .#nadesiko` でバージョンが表示される | Node v22.21.1 / cnako3 v3.8.7 |
| 2 | `make check` が green | 文法検査 5 ファイル・テスト 3 本すべて green |
| 3 | `3` と `「3」` を比べるテストが**失敗する** | `FAIL 型が違えば失敗する: 型が違う 実際=number 期待=string` と出て `make test` が終了コード 1 |
| 4 | 記事のコードが実装と一致 | `check_article_code.py` 違反 0 件 |
| 5 | nav から第 1 章に到達できる | `gulp mkdocs:build` が通り、`site/article/zettai/nadesiko/chapter01/` が生成される |

## 判断と学び

### 1. スパイクを最初に置いたことが効いた

未解決の仮定 5 件のうち **2 件が外れました**。とくに Node のバージョンは、devShell を書いてから気づくと `flake.nix` からやり直しになります。**外れた仮定を「実装前」に見つけられたのが、このステップの価値です。**

### 2. テストランナーが最初、失敗を成功と数えた

`bowling_test.nako3` の Red を確認したとき、取り込みエラーで落ちているのに **「テスト 2 本成功 / 0 本失敗」と表示されました。** cnako3 が終了コード 0 を返すためです。

最初の実装は終了コードだけを見ていました。`テスト結果報告` にたどり着かない失敗（文法エラー・取り込みエラー）を取りこぼします。判定を 2 つ重ねて直しました。

**Red を書いた瞬間にこれに気づけたのは運です。** 先に Green を書いていたら、しばらく気づかないままだったはずです。TDD の「失敗を必ず目で確認する」が、テストランナー自身のバグを捕まえました。

### 3. 1 言語目に書いた検査が、2 言語目で穴になった

`check_adr_references.py` は章のディレクトリを `docs/article/zettai/kotlin` と決め打ちしていました。なでしこ3 の第 1 章が ADR-013・014 を参照しても、検査の対象外だったのです。

多言語シリーズを前提に書いたはずの仕組みでも、**検査スクリプトだけは 1 言語目の形が残っていました。** 全言語のディレクトリを見るように直しました。

### 4. 承認ゲートが 1 つも同期的に機能しなかった

計画では 10 箇所で人が止まることになっていました。**実際には 0 箇所でした。** 計画の承認後に「Unit 1 を完了させる」という指示で連続実行したためです。

結果として Unit 1 は 1 日で終わりましたが、**AI-DLC の損失関数としてのゲートは働いていません。** 誤りがあれば、この報告書を読む時点まで刈り取られずに残っています。これは速度と引き換えに受け入れたリスクであり、記録に残します。

### 5. 組み込み命令名との衝突は、実行するまで分からない

`合計` という変数名が、組み込み命令 `合計` と衝突して文法エラーになりました。`得点合計` に直しました。

型の無い言語の日常です。**名前を短くしたいという圧力が減るので、結果として意味の分かる名前に落ち着く**という副作用もありました。

## Unit 2 への持ち込み

| # | 持ち込み事項 | 理由 |
| :--- | :--- | :--- |
| 1 | `.github/workflows/nadesiko-zettai.yml` の新設 | 計画どおり Unit 2。第 2 章の実装が入った直後に作る |
| 2 | 章をまたぐ引用の検査（Kotlin 版 Try 1） | 記事が 2 章以上になる Unit 2 から |
| 3 | 節構成 ↔ マインドマップの一致検査（Kotlin 版 Try 2） | 同上 |
| 4 | 記事に書く数値の検査（Kotlin 版 Try 3） | 同上 |
| 5 | `doctest/` の受け入れシナリオを書き始める | Unit 1 は 0 件。カバレッジの代替指標なので Unit 2 から増やす |
| 6 | 簡易 HTTP サーバのフォーム POST とクエリ文字列の実機確認 | スパイクでは GET のみ確認した |
| 7 | 承認ゲートの運用形態を Bolt 計画に明記する | 本 Unit で同期的に機能しなかったため |
| 8 | `docs/design/architecture_backend.md` にファイル単位のモジュール分割を追記 | 開発戦略の方針 |

## 関連ドキュメント

- [Unit 1 の Bolt 計画](iteration_plan-nadesiko-1.md) / [ふりかえり](retrospective-nadesiko-1.md)
- [リリース計画（なでしこ3 版）](release_plan-nadesiko.md) / [開発戦略](development_strategy.md)
- [第 1 章](../article/zettai/nadesiko/chapter01.md) / [なでしこ3 版トップ](../article/zettai/nadesiko/index.md)
- [ADR-013](../adr/ADR-013-cnako3-runtime.md) / [ADR-014](../adr/ADR-014-own-test-framework.md)
