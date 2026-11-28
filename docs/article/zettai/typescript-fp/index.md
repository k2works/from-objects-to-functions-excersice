# Zettai TypeScript FP 版

ほかの対象と同じ ToDo リストアプリケーションを、**TypeScript と [Effect](https://effect.website/)** で作ります。型が実行時に消え、型付けが構造的な言語で、副作用・エラー・依存を `Effect<A, E, R>` の型に載せると、設計のどこが型で守られ、どこが規約とテストに残るのかを確かめます。

この連載に、Effect を使わない TypeScript 版はありません。「FP」は、Effect を使う版という意味です。

## 公開済み

1. [第 1 章 新しいアプリケーションを準備する](chapter01.md)
2. [第 2 章 関数を使って HTTP を扱う](chapter02.md)
3. [第 3 章 ドメインの定義とテスト](chapter03.md)
4. [第 4 章 ドメインとアダプタのモデリング](chapter04.md)
5. [第 5 章 イベントで状態を変更する](chapter05.md)

全 13 章のうち 5 章を公開しています。第 3 章までの比較は [対象言語の横断比較](../comparison/index.md) にあります。計画は [執筆計画](../../outline.md) の「TypeScript FP 追加執筆計画」にあります。

## 使う道具

| 道具 | 版 | 決定 |
| :--- | :--- | :--- |
| Effect | 4.1.6 | [ADR-053](../../../adr/ADR-053-typescript-effect-version-and-stability.md) |
| TypeScript / `@effect/tsgo` | 7.0.2 / 0.73.7 | [ADR-054](../../../adr/ADR-054-typescript-toolchain-and-check.md) |
| Oxlint / Prettier | 1.95.0 / 3.10.1 | 同上 |
| vitest / `fast-check` | 5.1.2 / 4.11.0 | [ADR-055](../../../adr/ADR-055-typescript-testing-tools.md) |
| Testcontainers（PostgreSQL） | 12.2.2 | 同上 |

## 実行方法

Node 22.19 以上と Docker が必要です。テストが Testcontainers で PostgreSQL を起動するためです。devShell と CI は Node 24 を使います。

```bash
cd apps/typescript-fp/zettai
npm ci --ignore-scripts
npm run check
```

Nix を使う場合は、リポジトリの直下で `nix develop .#typescript-fp` を実行してから、同じコマンドを実行します。手順は [第 1 章](chapter01.md) にあります。
