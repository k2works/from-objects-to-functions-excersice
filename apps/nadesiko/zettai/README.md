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
| `make check` | `lint` と `test` をまとめて実行する |
| `make lint` | 全 `.nako3` を構文解析だけして文法エラーを見つける |
| `make test` | `test/*_test.nako3` を 1 本ずつ実行する |
| `make doctest` | `doctest/` の受け入れシナリオを実行する（第 2 章から） |
| `make run` | `src/main.nako3` を実行する |
| `make clean` | 生成物を削除する |

## ディレクトリ

```text
apps/nadesiko/zettai/
├── package.json      cnako3 のバージョンを固定する
├── Makefile          タスクランナー兼テストランナー
├── src/*.nako3       実装
├── test/
│   ├── helper.nako3  検証関数とテスト結果報告
│   └── *_test.nako3  テスト
└── doctest/          受け入れシナリオ（第 2 章から）
```

## この実装の約束

なでしこ3 には型システムがありません。Kotlin 版でコンパイラが担っていた保証を、この実装ではテストが肩代わりします。

1. **値を比べる前に型を比べる。** `test/helper.nako3` の `検証` は、`変数型確認` 同士を先に比べます。なでしこ3 の `=` は型を区別せず、`3` と `「3」` が等しくなるためです
2. **辞書を返す関数には契約テストを 1 本置く。** キーの有無と値の型を確かめます（第 4 章以降）
3. **識別子にひらがなを使わない。** ひらがなは助詞として切られ、文法エラーになります

## 注意

なでしこ3 は**実行時エラーでも終了コードが 0** になります。テストの合否は `テスト結果報告` が失敗時に `1でプロセス終` を呼ぶことで、文法エラーは `cnako3 -A` の標準エラー出力が空でないことで判定しています。どちらも `Makefile` に実装しています。
