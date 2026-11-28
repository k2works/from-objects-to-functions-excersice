import { Option, Result } from "effect"
import { describe, expect, it } from "vitest"
import { makeToDoItem, parseDueDate } from "../src/domain/todo-list.ts"
import type { ToDoItem } from "../src/domain/todo-list.ts"

describe("parseDueDate", () => {
  it.each(["2026-11-28", "2028-02-29", "2000-02-29", "0099-03-01"])("accepts %j", (raw) => {
    expect(Result.getOrUndefined(parseDueDate(raw))).toBe(raw)
  })

  it.each([
    "2026-02-29",
    "2100-02-29",
    "2026-02-30",
    "2026-04-31",
    "2026-13-01",
    "2026-00-10",
    "2026-01-00",
    "2026-1-5",
    "2026-11-28T00:00:00Z",
    " 2026-11-28",
    "2026-11-28\n",
    "tomorrow",
    ""
  ])("rejects %j", (raw) => {
    expect(Result.isFailure(parseDueDate(raw))).toBe(true)
  })
})

describe("makeToDoItem", () => {
  it("starts as Todo without a due date", () => {
    expect(makeToDoItem("write chapter")).toEqual({
      description: "write chapter",
      status: "Todo",
      dueDate: Option.none()
    })
  })

  it("keeps a status and a due date", () => {
    const dueDate = Result.getOrThrow(parseDueDate("2026-12-01"))

    const item = makeToDoItem("publish book", { status: "Blocked", dueDate })

    expect(item.status).toBe("Blocked")
    expect(item.dueDate).toEqual(Option.some("2026-12-01"))
  })
})

// 型検査で拒否される書き方です。実行はしません。
export const rejectedItems = (item: ToDoItem) => {
  // @ts-expect-error 状態は、決めた 4 つの値だけです
  makeToDoItem("x", { status: "Finished" })
  // @ts-expect-error 検査していない文字列は、期限にできません
  makeToDoItem("x", { dueDate: "2026-02-30" })
  // @ts-expect-error 項目は読み取り専用です
  item.status = "Done"
}
