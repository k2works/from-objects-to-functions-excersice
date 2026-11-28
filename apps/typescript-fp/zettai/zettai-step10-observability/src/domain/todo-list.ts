import { Brand, Option, Result } from "effect"
import { check, mapProblems, zipWith } from "./validation.ts"
import type { Validation } from "./validation.ts"

// 中身は同じ string ですが、型の上では別の型です。区別は型検査の中だけにあります。
export type User = Brand.Branded<string, "User">
export type ListName = Brand.Branded<string, "ListName">
export type DueDate = Brand.Branded<string, "DueDate">

// NUL は PostgreSQL が保存できません。対になっていないサロゲートは、別の値に変わるか拒否されます。
const UNSTORABLE = /[\0\p{Surrogate}]/u

const isRealDate = (raw: string): boolean => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(raw)) return false
  const date = new Date(`${raw}T00:00:00Z`)
  // 2 月 30 日のような日付は、読めても別の日になることがあります。書き戻して比べます。
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(raw)
}

const dueDateBrand = Brand.make<DueDate>(
  (raw) => isRealDate(raw) || "実在する YYYY-MM-DD の日付ではありません"
)

export type NameProblem = "Blank" | "TooLong" | "ForbiddenCharacter"

const MAX_NEW_NAME_LENGTH = 40

const notBlank = (raw: string) => check<NameProblem>(raw.length > 0, "Blank")
const storable = (raw: string) => check<NameProblem>(!UNSTORABLE.test(raw), "ForbiddenCharacter")
// 長さは、コードポイントの数で数えます。
const shortEnough = (raw: string) =>
  check<NameProblem>([...raw].length <= MAX_NEW_NAME_LENGTH, "TooLong")

// 保存できる名前かを確かめます。保存した行を読むときも、同じ条件を使います。
const storedName = <A>(raw: string, name: (raw: string) => A): Validation<A, NameProblem> =>
  zipWith(notBlank(raw), storable(raw), () => name(raw))

const userBrand = Brand.nominal<User>()
const listNameBrand = Brand.nominal<ListName>()

export const parseUser = (raw: string): Validation<User, NameProblem> => storedName(raw, userBrand)

export const parseListName = (raw: string): Validation<ListName, NameProblem> =>
  storedName(raw, listNameBrand)

// 利用者が新しく入力する名前には、長さの制限も掛けます。
export const validateNewListName = (raw: string): Validation<ListName, NameProblem> =>
  zipWith(
    zipWith(notBlank(raw), shortEnough(raw), () => null),
    storable(raw),
    () => listNameBrand(raw)
  )

export interface InputProblem {
  readonly field: "user" | "newName"
  readonly problem: NameProblem
}

const onField = (field: InputProblem["field"]) => (problem: NameProblem) => ({ field, problem })

// 名前の変更の 2 つの入力を、独立に確かめます。
export const validateRename = (
  rawUser: string,
  rawNewName: string
): Validation<{ readonly user: User; readonly newName: ListName }, InputProblem> =>
  zipWith(
    mapProblems(parseUser(rawUser), onField("user")),
    mapProblems(validateNewListName(rawNewName), onField("newName")),
    (user, newName) => ({ user, newName })
  )

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
