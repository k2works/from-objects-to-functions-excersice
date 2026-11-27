import { Effect } from "effect"
import { wordScore } from "./word-score.ts"

export const announceScore = (word: string, record: (line: string) => void) =>
  Effect.sync(() => {
    const score = wordScore(word)
    record(`${word}: ${score}`)
    return score
  })
