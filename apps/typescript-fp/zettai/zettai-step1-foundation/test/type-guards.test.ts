import { describe, expect, it } from "vitest"
import { announceScore } from "../src/announce-score.ts"
import { wordScore } from "../src/word-score.ts"

// 型検査が拒否する書き方を固定します。拒否されなくなると、型検査が失敗します。
// 型エラーは ts-expect-error で、Effect の診断は effect-diagnostics の指定で固定します。
// 前者は、Effect の診断を抑えません。指定の名前でコメントを書き始めると、指定として扱われます。
describe("rejected by the type check", () => {
  it("does not allow a score to be used as a string", () => {
    // @ts-expect-error 得点は number なので、string の変数には入れられない
    const score: string = wordScore("Effect")
    expect(score).toBe(6)
  })

  it("does not allow an Effect to be left unrun", () => {
    const lines: Array<string> = []
    // @effect-diagnostics-next-line floatingEffect:off
    announceScore("Effect", (line) => {
      lines.push(line)
    })
    expect(lines).toEqual([])
  })
})
