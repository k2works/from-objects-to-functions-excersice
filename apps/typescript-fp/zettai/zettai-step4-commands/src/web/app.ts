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

// 矢印 2: 取り出した結果を、リストか失敗にします。
const found = (list: Option.Option<ToDoList>): Result.Result<ToDoList, Failure> =>
  Result.fromOption(list, (): Failure => "not-found")

// 矢印 3: HTML と失敗を、それぞれ Response にします。
const htmlResponse = (html: string): Response =>
  new Response(html, { headers: { "content-type": "text/html; charset=utf-8" } })

const failureResponse = (failure: Failure): Response =>
  failure === "bad-request"
    ? new Response("bad request", { status: 400 })
    : new Response("not found", { status: 404 })

const showList =
  (hub: ToDoListHub) =>
  ({ user, listName }: ListRequest): Effect.Effect<Response> =>
    hub
      .getList(user, listName)
      .pipe(
        Effect.map((list) =>
          found(list).pipe(
            Result.map(renderPage),
            Result.match({ onFailure: failureResponse, onSuccess: htmlResponse })
          )
        )
      )

export const makeApp =
  (hub: ToDoListHub): App =>
  (request) =>
    Effect.suspend(() =>
      Result.match(extractListRequest(request), {
        onFailure: (failure) => Effect.succeed(failureResponse(failure)),
        onSuccess: showList(hub)
      })
    )
