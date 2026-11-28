import { Brand, Option, Result } from "effect"

// 中身は同じ string ですが、型の上では別の型です。区別は型検査の中だけにあります。
export type User = Brand.Branded<string, "User">
export type ListName = Brand.Branded<string, "ListName">
export type DueDate = Brand.Branded<string, "DueDate">

// NUL は PostgreSQL が保存できません。対になっていないサロゲートは、別の値に変わるか拒否されます。
const UNSTORABLE = /[\0\p{Surrogate}]/u

const validName = (raw: string) => {
  if (raw.length === 0) return "名前が空です"
  return !UNSTORABLE.test(raw) || "名前に使えない文字があります"
}

const isRealDate = (raw: string): boolean => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(raw)) return false
  const date = new Date(`${raw}T00:00:00Z`)
  // 2 月 30 日のような日付は、読めても別の日になることがあります。書き戻して比べます。
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(raw)
}

// 生成の関数は公開しません。そのまま呼ぶと、失敗が例外になるためです。
const userBrand = Brand.make<User>(validName)
const listNameBrand = Brand.make<ListName>(validName)
const dueDateBrand = Brand.make<DueDate>(
  (raw) => isRealDate(raw) || "実在する YYYY-MM-DD の日付ではありません"
)

export const parseUser = (raw: string): Result.Result<User, Brand.BrandError> =>
  userBrand.result(raw)

export const parseListName = (raw: string): Result.Result<ListName, Brand.BrandError> =>
  listNameBrand.result(raw)

export const parseDueDate = (raw: string): Result.Result<DueDate, Brand.BrandError> =>
  dueDateBrand.result(raw)

// 値の一覧を 1 か所に置き、型はここから作ります。行の読み取りも、同じ一覧を使います。
export const TODO_STATUSES = ["Todo", "InProgress", "Done", "Blocked"] as const

export type ToDoStatus = (typeof TODO_STATUSES)[number]

export interface ToDoItem {
  readonly description: string
  readonly status: ToDoStatus
  readonly dueDate: Option.Option<DueDate>
}

export const makeToDoItem = (
  description: string,
  options: { readonly status?: ToDoStatus; readonly dueDate?: DueDate } = {}
): ToDoItem => ({
  description,
  status: options.status ?? "Todo",
  dueDate: Option.fromUndefinedOr(options.dueDate)
})

export interface ToDoList {
  readonly listName: ListName
  readonly items: ReadonlyArray<ToDoItem>
}
