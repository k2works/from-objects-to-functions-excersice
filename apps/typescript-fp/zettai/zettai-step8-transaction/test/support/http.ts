import { request } from "node:http"

export interface Received {
  readonly status: number
  readonly contentType: string
  readonly body: string
}

// fetch は URL を書き換えることがあるので、node:http でパスを書いたとおりに送ります。
export const send = (port: number, path: string, method = "GET") =>
  new Promise<Received>((resolve, reject) => {
    const outgoing = request(
      { host: "127.0.0.1", port, path, method, agent: false },
      (response) => {
        const chunks: Array<Buffer> = []
        response.on("data", (chunk: Buffer) => {
          chunks.push(chunk)
        })
        response.on("end", () => {
          resolve({
            status: response.statusCode ?? 0,
            contentType: response.headers["content-type"] ?? "",
            body: Buffer.concat(chunks).toString("utf-8")
          })
        })
      }
    )
    outgoing.on("error", reject)
    outgoing.end()
  })
