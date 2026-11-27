import { Effect } from "effect"
import { announceScore } from "./announce-score.ts"

// ビルドした出力が Node で起動できることを確かめる smoke です。Effect の実行は、ここ（最外周）で行います。
const program = announceScore("Zettai", (line) => {
  console.log(line)
})

await Effect.runPromise(program)
