import { Context, Effect, Result } from "effect"

export const sum = (numbers: ReadonlyArray<number>): number =>
  numbers.reduce((total, n) => total + n, 0)

export const parsePositiveInt = (raw: string): Result.Result<number, string> =>
  /^[1-9]\d*$/.test(raw)
    ? Result.succeed(Number(raw))
    : Result.fail(`not a positive integer: ${raw}`)

export class Greeting extends Context.Service<Greeting, { readonly message: string }>()(
  "Greeting"
) {}

export const greet = (name: string) =>
  Effect.gen(function* () {
    const greeting = yield* Greeting
    return `${greeting.message}, ${name}`
  })
