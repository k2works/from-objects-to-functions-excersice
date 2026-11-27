import { Context, Effect } from "effect"

export class Greeting extends Context.Service<Greeting, { readonly message: string }>()(
  "Greeting"
) {}

export const greet = (name: string) =>
  Effect.gen(function* () {
    const greeting = yield* Greeting
    return `${greeting.message}, ${name}`
  })
