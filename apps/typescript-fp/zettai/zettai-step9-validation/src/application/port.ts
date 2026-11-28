import { Result } from "effect"

const DEFAULT_PORT = 8080
const MAX_PORT = 65535

export const parsePort = (raw: string | undefined): Result.Result<number, string> => {
  if (raw === undefined) return Result.succeed(DEFAULT_PORT)
  const port = /^\d+$/.test(raw) ? Number(raw) : Number.NaN
  return port >= 1 && port <= MAX_PORT
    ? Result.succeed(port)
    : Result.fail(`ポート番号が正しくありません: ${raw}`)
}
