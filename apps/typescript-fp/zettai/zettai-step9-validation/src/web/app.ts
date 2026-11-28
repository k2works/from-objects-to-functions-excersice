import { Effect, Option, Result } from "effect"
import type { ZettaiError } from "../domain/errors.ts"
import type { ToDoListHub } from "../domain/hub.ts"
import { renameToDoList } from "../domain/commands.ts"
import { parseListName, parseUser, validateRename } from "../domain/todo-list.ts"
import type { ListName, User } from "../domain/todo-list.ts"
import { listPath, renderPage, renderRenameForm, renderRowsPage } from "./html.ts"

export type App = (request: Request) => Effect.Effect<Response>

// HTTP の要求そのものの失敗です。domain の失敗とは分けます。
export type RequestFailure = "BadRequest" | "UnknownPath"

export type Failure = ZettaiError["_tag"] | RequestFailure

export type PageRequest =
  | { readonly _tag: "ShowLists"; readonly user: User }
  | { readonly _tag: "ShowList"; readonly user: User; readonly listName: ListName }
  | { readonly _tag: "ShowRenameForm"; readonly user: User; readonly listName: ListName }
  | {
      readonly _tag: "Rename"
      readonly user: User
      readonly listName: ListName
      readonly request: Request
    }

const decodeSegment = Option.liftThrowable(decodeURIComponent)

type Parsed = Result.Result<PageRequest, RequestFailure>

const unknownPath = (): RequestFailure => "UnknownPath"

// 生の文字列を domain の型にします。名前にできない値の要求は、無いものとして扱います。
const namesOf = (rawUser: string, rawListName: string) =>
  Result.all({ user: parseUser(rawUser), listName: parseListName(rawListName) }).pipe(
    Result.mapError(unknownPath)
  )

const listsPage = (rawUser: string): Parsed =>
  parseUser(rawUser).pipe(
    Result.map((user): PageRequest => ({ _tag: "ShowLists", user })),
    Result.mapError(unknownPath)
  )

// パスの段の数と末尾から、画面を決めます。POST を受け付けるのは、名前の変更だけです。
const route = (request: Request, segments: ReadonlyArray<string>): Parsed => {
  const [rawUser = "", rawListName, action] = segments
  const isGet = request.method === "GET"
  if (rawListName === undefined) return isGet ? listsPage(rawUser) : Result.fail("UnknownPath")
  return namesOf(rawUser, rawListName).pipe(
    Result.flatMap((names): Parsed => {
      if (action === undefined && isGet) return Result.succeed({ _tag: "ShowList", ...names })
      if (action !== "rename") return Result.fail("UnknownPath")
      if (isGet) return Result.succeed({ _tag: "ShowRenameForm", ...names })
      return request.method === "POST"
        ? Result.succeed({ _tag: "Rename", ...names, request })
        : Result.fail("UnknownPath")
    })
  )
}

// 矢印 1: Request から、どの画面を誰が求めたかを取り出します。
export const extractRequest = (request: Request): Parsed => {
  const [, root, ...segments] = new URL(request.url).pathname.split("/")
  if (root !== "todo" || segments.length < 1 || segments.length > 3) {
    return Result.fail("UnknownPath")
  }
  return Option.match(Option.all(segments.map(decodeSegment)), {
    onNone: () => Result.fail("BadRequest"),
    onSome: (decoded) => route(request, decoded)
  })
}

// 矢印 2: 利用者とリスト名から取り出すのは、ハブの getList です。
// 矢印 3: HTML と失敗を、それぞれ Response にします。
const htmlResponse = (body: string, status = 200): Response =>
  new Response(body, { status, headers: { "content-type": "text/html; charset=utf-8" } })

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

const respondWithFailure = (error: ZettaiError) => Effect.succeed(failureResponse(error._tag))

const FORM = "application/x-www-form-urlencoded"

type RenameRequest = Extract<PageRequest, { readonly _tag: "Rename" }>

// 入力の誤りは、まとめて同じ画面に返します。業務の規則の失敗は、理由に応じた状態にします。
const rename = (hub: ToDoListHub, { user, listName, request }: RenameRequest) =>
  Effect.gen(function* () {
    if (!request.headers.get("content-type")?.startsWith(FORM)) {
      return failureResponse("BadRequest")
    }
    const form = new URLSearchParams(yield* Effect.promise(() => request.text()))
    const input = form.get("newName") ?? ""
    const validated = validateRename(user, input)
    if (Result.isFailure(validated)) {
      yield* hub.getList(user, listName)
      return htmlResponse(renderRenameForm(user, listName, input, validated.failure), 400)
    }
    const { newName } = validated.success
    yield* hub.execute(renameToDoList(user, listName, newName))
    return new Response(null, { status: 302, headers: { location: listPath(user, newName) } })
  }).pipe(Effect.catch(respondWithFailure))

// catch が受け取るのは、型に現れる失敗だけです。defect は、そのまま最外周に届きます。
const show =
  (hub: ToDoListHub) =>
  (page: PageRequest): Effect.Effect<Response> => {
    switch (page._tag) {
      case "ShowLists":
        return hub
          .listRows(page.user)
          .pipe(Effect.map((rows) => htmlResponse(renderRowsPage(page.user, rows))))
      case "ShowList":
        return hub.getList(page.user, page.listName).pipe(
          Effect.map((list) => htmlResponse(renderPage(page.user, list))),
          Effect.catch(respondWithFailure)
        )
      case "ShowRenameForm":
        return hub.getList(page.user, page.listName).pipe(
          Effect.map((list) =>
            htmlResponse(renderRenameForm(page.user, list.listName, list.listName, []))
          ),
          Effect.catch(respondWithFailure)
        )
      case "Rename":
        return rename(hub, page)
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
