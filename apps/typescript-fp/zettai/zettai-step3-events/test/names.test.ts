import { Result } from "effect"
import type { Brand } from "effect"
import { describe, expect, it } from "vitest"
import type { ToDoListHub } from "../src/domain/hub.ts"
import { parseListName, parseUser } from "../src/domain/todo-list.ts"
import type { ListName, User } from "../src/domain/todo-list.ts"

type Parse = (raw: string) => Result.Result<string, Brand.BrandError>

describe.each<[string, Parse]>([
  ["parseUser", parseUser],
  ["parseListName", parseListName]
])("%s", (_name, parse) => {
  it.each(["uberto", "利用者", "買い物 メモ", "a/b", " "])("accepts %j", (raw) => {
    expect(Result.getOrUndefined(parse(raw))).toBe(raw)
  })

  it("returns a failure as a value for an empty name", () => {
    expect(Result.isFailure(parse(""))).toBe(true)
  })
})

// 型検査で拒否される書き方です。実行はしません。
export const swappedNames = (hub: ToDoListHub, user: User, listName: ListName) => {
  // @ts-expect-error 利用者の位置に、リスト名は渡せません
  hub.getList(listName, listName)
  // @ts-expect-error リスト名の位置に、利用者は渡せません
  hub.getList(user, user)
}

export const unchecked = (hub: ToDoListHub, listName: ListName) => {
  // @ts-expect-error 生の文字列は、生成の関数を通さずに名前として使えません
  hub.getList("uberto", listName)
}
