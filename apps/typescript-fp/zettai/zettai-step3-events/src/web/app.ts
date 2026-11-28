import { Effect, Option, Result } from "effect"
import type { ToDoListHub } from "../domain/hub.ts"
import { parseListName, parseUser } from "../domain/todo-list.ts"
import type { ListName, ToDoList, User } from "../domain/todo-list.ts"
import { renderPage } from "./html.ts"

export type App = (request: Request) => Effect.Effect<Response>

export type Failure = "bad-request" | "not-found"

export interface ListRequest {
  readonly user: User
  readonly listName: ListName
}

const decodeSegment = Option.liftThrowable(decodeURIComponent)

// 生の文字列を domain の型にします。名前にできない値の要求は、無いものとして扱います。
const toNames = (rawUser: string, rawListName: string): Result.Result<ListRequest, Failure> =>
  Result.all({ user: parseUser(rawUser), listName: parseListName(rawListName) }).pipe(
    Result.mapError((): Failure => "not-found")
  )

// 矢印 1: Request から、利用者とリスト名を取り出します。
export const extractListRequest = (request: Request): Result.Result<ListRequest, Failure> => {
  const [, root, user, listName, ...rest] = new URL(request.url).pathname.split("/")
  if (request.method !== "GET" || root !== "todo" || rest.length > 0) {
    return Result.fail("not-found")
  }
  return Option.match(Option.all([decodeSegment(user ?? ""), decodeSegment(listName ?? "")]), {
    onNone: () => Result.fail("bad-request"),
    onSome: ([rawUser, rawListName]) => toNames(rawUser, rawListName)
  })
}

// 矢印 2: 利用者とリスト名から、リストを取り出します。
const fetchList =
  (hub: ToDoListHub) =>
  ({ user, listName }: ListRequest): Result.Result<ToDoList, Failure> =>
    Result.fromOption(hub.getList(user, listName), (): Failure => "not-found")

// 矢印 3: HTML と失敗を、それぞれ Response にします。
const htmlResponse = (html: string): Response =>
  new Response(html, { headers: { "content-type": "text/html; charset=utf-8" } })

const failureResponse = (failure: Failure): Response =>
  failure === "bad-request"
    ? new Response("bad request", { status: 400 })
    : new Response("not found", { status: 404 })

export const makeApp =
  (hub: ToDoListHub): App =>
  (request) =>
    Effect.sync(() =>
      extractListRequest(request).pipe(
        Result.flatMap(fetchList(hub)),
        Result.map(renderPage),
        Result.match({ onFailure: failureResponse, onSuccess: htmlResponse })
      )
    )
