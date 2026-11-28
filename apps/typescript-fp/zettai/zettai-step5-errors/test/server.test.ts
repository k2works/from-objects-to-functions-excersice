import { Effect, Exit } from "effect"
import { describe, expect, it } from "vitest"
import type { App } from "../src/web/app.ts"
import { ServerError, serve } from "../src/web/server.ts"
import { send } from "./support/http.ts"

// 受け取ったパスを、そのまま本文にする応答です。接続の部分だけを確かめます。
const echo: App = (request) => Effect.succeed(new Response(new URL(request.url).pathname))

const withServer = <A>(app: App, use: (port: number) => Promise<A>) =>
  Effect.runPromise(
    Effect.scoped(
      Effect.gen(function* () {
        const server = yield* serve(app, 0)
        return yield* Effect.promise(() => use(server.port))
      })
    )
  )

describe("serve", () => {
  it("stops the server when the scope is closed", async () => {
    const port = await withServer(echo, async (port) => port)

    await expect(send(port, "/")).rejects.toThrow("ECONNREFUSED")
  })

  it("stops the server even when the use of it fails", async () => {
    let port = 0
    const program = Effect.gen(function* () {
      const server = yield* serve(echo, 0)
      port = server.port
      return yield* Effect.fail("broken while serving")
    })

    const exit = await Effect.runPromiseExit(Effect.scoped(program))

    expect(Exit.isFailure(exit)).toBe(true)
    await expect(send(port, "/")).rejects.toThrow("ECONNREFUSED")
  })

  it("fails with a typed ServerError when the port is already used", async () => {
    const error = await withServer(echo, (port) =>
      Effect.runPromise(Effect.flip(Effect.scoped(serve(echo, port))))
    )

    expect(error).toBeInstanceOf(ServerError)
  })

  it("does not take a path starting with two slashes for a host name", async () => {
    const response = await withServer(echo, (port) => send(port, "//todo/uberto/book"))

    expect(response.body).toBe("//todo/uberto/book")
  })

  it.each(["POST", "TRACE"])("returns 404 for %s", async (method) => {
    const response = await withServer(echo, (port) => send(port, "/todo/uberto/book", method))

    expect(response.status).toBe(404)
  })

  it("hides the reason when the application dies", async () => {
    const dying: App = () => Effect.die(new Error("password=secret"))

    const response = await withServer(dying, (port) => send(port, "/"))

    expect(response).toMatchObject({ status: 500, body: "internal server error" })
  })
})
