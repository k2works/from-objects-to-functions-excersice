---
name: zettai-kotlin-fp-review
description: Zettai Kotlin FP 版（Arrow）記事レビューで繰り返し見るべき観点と過去の指摘傾向
metadata:
  type: project
---

Kotlin FP 版の各章は「既存 Kotlin 版との選択差を説明する」ことが成功条件。過去レビュー（2026-10-31、第 6・7 章）で弱かった点：

- 既存 Kotlin 版／原著が段階的に導入する仕組み（空 list による拒否 → 自前 Outcome）を FP 版は最初から Arrow で置き換えるため、前倒し導入した旨と比較表が抜けやすい
- Arrow Raise の説明で「例外ではない」と書きがち。実装は内部で例外ベースの非局所脱出なので、観測可能な契約（戻り値が Either）として書くよう指摘した
- outline.md の章別成功条件（例: 第 7 章は map / flatMap）と本文の網羅を突き合わせる

**Why:** 計画書の Try に「章数・固有名詞・参照先・著者名・公開章数の fact check」があり、毎 Unit レビュー対象。
**How to apply:** 次章以降のレビューでは outline.md の章表（成功条件列）、既存 Kotlin 版の同章見出し、実装 module のコードを必ず三点照合する。[[zettai-kotlin-fp-review]]
