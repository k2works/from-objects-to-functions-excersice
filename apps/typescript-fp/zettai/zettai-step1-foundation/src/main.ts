import { Effect, Layer } from "effect"
import { Greeting, greet } from "./foundation.ts"

// ビルドした出力が Node で起動できることを確かめる smoke です。Effect の実行は、ここ（最外周）で行います。
const layer = Layer.succeed(Greeting, { message: "Hello" })

console.log(await Effect.runPromise(greet("Zettai").pipe(Effect.provide(layer))))
