import { Data, Effect } from "effect"
import { createServer } from "node:http"
import type { IncomingMessage, Server, ServerResponse } from "node:http"
import type { AddressInfo } from "node:net"
import type { App } from "./app.ts"

export class ServerError extends Data.TaggedError("ServerError")<{ readonly cause: unknown }> {}

// 本文の大きさの上限です。この版のフォームは、短い入力だけです。
const MAX_BODY_BYTES = 10_000

// 上限を超えた場合は null を返します。残りは読み捨てます。
const readBody = (incoming: IncomingMessage) =>
  new Promise<Buffer | null>((resolve, reject) => {
    const chunks: Array<Buffer> = []
    let size = 0
    incoming.on("data", (chunk: Buffer) => {
      size += chunk.length
      if (size <= MAX_BODY_BYTES) chunks.push(chunk)
    })
    incoming.on("end", () => {
      resolve(size <= MAX_BODY_BYTES ? Buffer.concat(chunks) : null)
    })
    incoming.on("error", reject)
  })

const toRequest = (incoming: IncomingMessage, url: string, body: Buffer): Request => {
  const type = incoming.headers["content-type"]
  const headers: Record<string, string> = type === undefined ? {} : { "content-type": type }
  return incoming.method === "POST"
    ? new Request(url, { method: "POST", headers, body: body.toString("utf-8") })
    : new Request(url, { headers })
}

// 扱うのは GET と POST です。ヘッダーは、content-type だけを渡します。
const toResponse = async (app: App, incoming: IncomingMessage): Promise<Response> => {
  const path = incoming.url ?? ""
  if ((incoming.method !== "GET" && incoming.method !== "POST") || !path.startsWith("/")) {
    return new Response("not found", { status: 404 })
  }
  const body = await readBody(incoming)
  if (body === null) return new Response("payload too large", { status: 413 })
  // パスは origin の後ろに連結します。第 2 引数を base にすると、// で始まるパスがホスト名になります。
  return Effect.runPromise(app(toRequest(incoming, `http://localhost${path}`, body)))
}

const write = async (response: Response, outgoing: ServerResponse): Promise<void> => {
  const body = await response.text()
  outgoing.writeHead(response.status, Object.fromEntries(response.headers))
  outgoing.end(body)
}

// Node の callback は Effect の外にあります。Effect を実行するのは、この最外周だけです。
const handle = (app: App) => (incoming: IncomingMessage, outgoing: ServerResponse) => {
  toResponse(app, incoming)
    .then((response) => write(response, outgoing))
    .catch(() => {
      // 失敗の理由は応答に出しません。記録は第 12 章で扱います。
      outgoing.writeHead(500)
      outgoing.end("internal server error")
    })
}

const listen = (app: App, port: number) =>
  Effect.callback<Server, ServerError>((resume) => {
    const server = createServer(handle(app))
    server.once("error", (cause) => {
      resume(Effect.fail(new ServerError({ cause })))
    })
    server.listen(port, "127.0.0.1", () => {
      resume(Effect.succeed(server))
    })
  })

const close = (server: Server) =>
  Effect.callback<void>((resume) => {
    server.close(() => {
      resume(Effect.void)
    })
  })

// 起動と停止を 1 組にします。スコープを抜けるとき、成功でも失敗でも停止します。
export const serve = (app: App, port: number) =>
  Effect.acquireRelease(listen(app, port), close).pipe(
    Effect.map((server) => ({ port: (server.address() as AddressInfo).port }))
  )
