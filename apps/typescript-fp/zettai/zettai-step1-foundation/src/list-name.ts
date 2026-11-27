import { Result } from "effect"

export const nonBlankName = (name: string): Result.Result<string, string> =>
  name.trim() === "" ? Result.fail("名前は空にできません") : Result.succeed(name)
