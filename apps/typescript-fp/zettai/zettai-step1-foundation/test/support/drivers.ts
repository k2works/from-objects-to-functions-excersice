import { Effect, Layer } from "effect"
import { Greeting, greet } from "../../src/foundation.ts"

// 基盤の確認用の 2 経路です。Zettai の業務シナリオではありません。
export interface Driver {
  readonly greet: (name: string) => Promise<string>
}

const layer = Layer.succeed(Greeting, { message: "Hello" })

const run = (name: string) => Effect.runPromise(greet(name).pipe(Effect.provide(layer)))

// サーバーは起動せず、Request と Response を通すことだけを確かめます。HTTP の実装方式は第 2 章で決めます。
const handle = async (request: Request): Promise<Response> =>
  new Response(await run(new URL(request.url).searchParams.get("name") ?? ""))

export const drivers: Record<"domain" | "http", Driver> = {
  domain: { greet: run },
  http: {
    greet: async (name) => {
      const url = `http://localhost/greet?name=${encodeURIComponent(name)}`
      const response = await handle(new Request(url))
      return response.text()
    }
  }
}
