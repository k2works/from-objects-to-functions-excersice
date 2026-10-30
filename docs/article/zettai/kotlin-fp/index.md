---
type: Article Index
title: "Zettai Kotlin FP 版"
description: "Kotlin、Arrow、kotlinx.coroutines を使い、functional core / effectful shell として Zettai を組み立てる全 13 章の連載索引。"
tags: [article, zettai, kotlin, arrow, functional-programming]
status: draft
generated: { by: openai-codex/gpt-5, at: 2026-10-30T00:00:00Z }
---

# Zettai Kotlin FP 版

既存の Kotlin 版と同じ ToDo リストアプリケーションを、Kotlin の関数、Arrow、kotlinx.coroutines を使って作ります。自前の関数型抽象を組み立てる代わりにライブラリへ委ねると、設計判断がどう変わるかを比較します。

## 公開済み

1. [第 1 章 新しいアプリケーションを準備する](chapter01.md)

## 実行方法

```bash
nix develop .#kotlin-fp
cd apps/kotlin-fp/zettai
bash ./gradlew check
```
