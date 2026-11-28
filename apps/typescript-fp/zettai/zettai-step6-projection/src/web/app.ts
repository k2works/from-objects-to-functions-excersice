import { Effect, Option, Result } from "effect"
import type { ZettaiError } from "../domain/errors.ts"
import type { ToDoListHub } from "../domain/hub.ts"
import { parseListName, parseUser } from "../domain/todo-list.ts"
import type { ListName, User } from "../domain/todo-list.ts"
import { renderPage, renderRowsPage } from "./html.ts"

export type App = (request: Request) => Effect.Effect<Response>

// HTTP の要求そのものの失敗です。domain の失敗とは分けます。
export type RequestFailure = "BadRequest" | "UnknownPath"

export type Failure = ZettaiError["_tag"] | RequestFailure

export type PageRequest =
  | { readonly _tag: "ShowList"; readonly user: User; readonly listName: ListName }
  | { readonly _tag: "ShowLists"; readonly user: User }

const decodeSegment = Option.liftThrowable(decodeURIComponent)

type Parsed = Result.Result<PageRequest, RequestFailure>

const unknownPath = (): RequestFailure => "UnknownPath"

// 生の文字列を domain の型にします。名前にできない値の要求は、無いものとして扱います。
const listPage = (rawUser: string, rawListName: string): Parsed =>
  Result.all({ user: parseUser(rawUser), listName: parseListName(rawListName) }).pipe(
    Result.map((names): PageRequest => ({ _tag: "ShowList", ...names })),
    Result.mapError(unknownPath)
  )

const listsPage = (rawUser: string): Parsed =>
  parseUser(rawUser).pipe(
    Result.map((user): PageRequest => ({ _tag: "ShowLists", user })),
    Result.mapError(unknownPath)
  )

// 矢印 1: Request から、どの画面を誰が求めたかを取り出します。
export const extractRequest = (request: Request): Parsed => {
  const [, root, user, listName, ...rest] = new URL(request.url).pathname.split("/")
  if (request.method !== "GET" || root !== "todo" || rest.length > 0) {
    return Result.fail("UnknownPath")
  }
  // /todo/{user} は一覧、/todo/{user}/{listname} は 1 つのリストです。
  const segments = listName === undefined ? [user ?? ""] : [user ?? "", listName]
  return Option.match(Option.all(segments.map(decodeSegment)), {
    onNone: () => Result.fail("BadRequest"),
    onSome: ([rawUser = "", rawListName]) =>
      rawListName === undefined ? listsPage(rawUser) : listPage(rawUser, rawListName)
  })
}

// 矢印 2: 利用者とリスト名から取り出すのは、ハブの getList です。
// 矢印 3: HTML と失敗を、それぞれ Response にします。
const htmlResponse = (html: string): Response =>
  new Response(html, { headers: { "content-type": "text/html; charset=utf-8" } })

// 失敗の種類ごとの状態です。種類を足すと、キーが足りないので型検査が失敗します。
const statusOf: Record<Failure, 400 | 404> = {
  BadRequest: 400,
  UnknownPath: 404,
  ListNotFound: 404,
  ItemNotFound: 404,
  ListAlreadyExists: 400,
  InvalidTransition: 400
}

// 本文は状態ごとの固定の文字列です。失敗の中身は出しません。
const bodies = { 400: "bad request", 404: "not found" } as const

export const failureResponse = (failure: Failure): Response =>
  new Response(bodies[statusOf[failure]], { status: statusOf[failure] })

// catch が受け取るのは、型に現れる失敗だけです。defect は、そのまま最外周に届きます。
const show =
  (hub: ToDoListHub) =>
  (page: PageRequest): Effect.Effect<Response> => {
    switch (page._tag) {
      case "ShowList":
        return hub.getList(page.user, page.listName).pipe(
          Effect.map((list) => htmlResponse(renderPage(list))),
          Effect.catch((error) => Effect.succeed(failureResponse(error._tag)))
        )
      case "ShowLists":
        return hub
          .listRows(page.user)
          .pipe(Effect.map((rows) => htmlResponse(renderRowsPage(page.user, rows))))
    }
  }

export const makeApp =
  (hub: ToDoListHub): App =>
  (request) =>
    Effect.suspend(() =>
      Result.match(extractRequest(request), {
        onFailure: (failure) => Effect.succeed(failureResponse(failure)),
        onSuccess: show(hub)
      })
    )
