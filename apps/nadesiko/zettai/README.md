# Zettai（なでしこ3 版）

連載 [Zettai — 関数型プログラミングで作る変更を楽に安全にできるソフトウェア](../../../docs/article/zettai/nadesiko/index.md) のなでしこ3 版サンプル実装です。

## 実行環境

```text
nix develop .#nadesiko
cd apps/nadesiko/zettai
make check
```

`nix develop` の初回だけ `npm install` が走り、`node_modules/` に cnako3 が入ります。

| 項目 | 内容 |
| :--- | :--- |
| 処理系 | cnako3（npm パッケージ `nadesiko3` 3.8.7、Node 実装） |
| Node | 22 以上（`nadesiko3` の `engines` が要求する） |
| バージョンの固定先 | `package.json` の 1 箇所のみ |

## タスク

| コマンド | 内容 |
| :--- | :--- |
| `make check` | `lint`・`test`・`doctest` をまとめて実行する（全段階） |
| `make lint` | 全 `.nako3` を構文解析だけして文法エラーを見つける |
| `make test` | `test/stepN/*_test.nako3` を 1 本ずつ実行する |
| `make doctest` | `doctest/stepN/` の受け入れシナリオを段階ごとに実行する |
| `make run` | 最新の段階のアプリケーションを実行する |
| `make clean` | 生成物を削除する |

## ディレクトリ

**章の段階ごとにディレクトリを分けています**（[ADR-015](../../../docs/adr/ADR-015-step-directories.md)）。過去の章のコードがその場に残るので、記事のコード例が実装から消えません。

```text
apps/nadesiko/zettai/
├── package.json         cnako3 のバージョンを固定する
├── Makefile             タスクランナー兼テストランナー
├── src/step1/*.nako3    第 1〜3 章（Unit 1〜2）
├── src/step2/*.nako3    第 4〜7 章（Unit 3〜4）
├── test/stepN/
│   ├── helper.nako3     検証関数とテスト結果報告
│   ├── ddt/             受け入れテストの入口と実行経路
│   └── *_test.nako3     テスト
└── doctest/stepN/       受け入れシナリオ（HTTP 経由）
```

段階の区切りは Kotlin 版のモジュール（`zettai-stepN-*`）と揃えています。**過去の段階は凍結します。**

`make check` は全段階を対象にしますが、**作業中は `make check STEP=step3` のように 1 段階だけを回します。** ファイルごとに cnako3 のプロセスを起こすため、段階が増えると全段階の実行は長くなります（8 コアで 3 段階・約 30 秒）。1 段階なら約 12 秒です。全段階は CI が毎回確かめます。並列数は `make check JOBS=4` のように変えられます。

## この実装の約束

なでしこ3 には型システムがありません。Kotlin 版でコンパイラが担っていた保証を、この実装ではテストが肩代わりします。

1. **値を比べる前に型を比べる。** `test/helper.nako3` の `検証` は、`変数型確認` 同士を先に比べます。なでしこ3 の `=` は型を区別せず、`3` と `「3」` が等しくなるためです
2. **辞書を返す関数には契約テストを 1 本置く。** キーの有無と値の型を確かめます（第 4 章以降）
3. **識別子にひらがなを使わない。** ひらがなは助詞として切られ、文法エラーになります
4. **過去の段階は凍結する。** `src/step1/` は第 1〜3 章の記事が説明しているコードです

## 注意

なでしこ3 は**実行時エラーでも終了コードが 0** になります。テストの合否は `テスト結果報告` が失敗時に `1でプロセス終` を呼ぶことで、文法エラーは `cnako3 -A` の標準エラー出力が空でないことで判定しています。どちらも `Makefile` に実装しています。
