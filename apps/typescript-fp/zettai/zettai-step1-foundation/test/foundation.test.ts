import { Effect, Layer } from "effect"
import { describe, expect, it } from "vitest"
import { Greeting, greet } from "../src/foundation.ts"

// 基盤の確認用です。Layer で依存を差し替えられることを確かめます。依存の注入は第 4 章で扱います。
describe("Effect and Layer", () => {
  const greetWith = (message: string) =>
    Effect.runPromise(greet("Zettai").pipe(Effect.provide(Layer.succeed(Greeting, { message }))))

  it("uses the greeting provided by the layer", async () => {
    expect(await greetWith("Hello")).toBe("Hello, Zettai")
  })

  it("changes the result when the layer is replaced", async () => {
    expect(await greetWith("Bonjour")).toBe("Bonjour, Zettai")
  })
})
