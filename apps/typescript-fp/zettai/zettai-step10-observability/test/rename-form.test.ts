import { Effect } from "effect"
import { describe, expect, it } from "vitest"
import { makeInMemoryStore } from "../src/application/in-memory-store.ts"
import { makeHub } from "../src/domain/hub.ts"
import { makeApp } from "../src/web/app.ts"
import { createdWith } from "./support/history.ts"

const app = () =>
  makeApp(
    makeHub(
      makeInMemoryStore([
        ...createdWith("uberto", "book", "write"),
        ...createdWith("uberto", "music")
      ])
    )
  )

const call = async (target: ReturnType<typeof app>, path: string, form?: string) => {
  const init =
    form === undefined
      ? {}
      : {
          method: "POST",
          headers: { "content-type": "application/x-www-form-urlencoded" },
          body: form
        }
  const response = await Effect.runPromise(target(new Request(`http://localhost${path}`, init)))
  return {
    status: response.status,
    location: response.headers.get("location"),
    body: await response.text()
  }
}

describe("the rename form", () => {
  it("shows the form of an existing list", async () => {
    const response = await call(app(), "/todo/uberto/book/rename")

    expect(response.status).toBe(200)
    expect(response.body).toContain('<form method="post" action="/todo/uberto/book/rename">')
  })

  it("answers 404 for the form of a list that does not exist", async () => {
    expect((await call(app(), "/todo/uberto/none/rename")).status).toBe(404)
  })

  it("links to the form from the list page", async () => {
    const response = await call(app(), "/todo/uberto/book")

    expect(response.body).toContain('<a href="/todo/uberto/book/rename">')
  })

  it("renames and redirects to the new name", async () => {
    const target = app()

    const response = await call(target, "/todo/uberto/book/rename", "newName=my+reading%2F2")

    expect(response).toMatchObject({ status: 302, location: "/todo/uberto/my%20reading%2F2" })
    expect((await call(target, "/todo/uberto/my%20reading%2F2")).status).toBe(200)
    expect((await call(target, "/todo/uberto/book")).status).toBe(404)
  })

  it("shows every problem of the input on the same form", async () => {
    const response = await call(app(), "/todo/uberto/book/rename", `newName=${"a".repeat(41)}%00`)

    expect(response.status).toBe(400)
    expect(response.body).toContain('data-problem="newName TooLong"')
    expect(response.body).toContain('data-problem="newName ForbiddenCharacter"')
    expect(response.body).toContain("<form")
  })

  it("treats a missing field as a blank name", async () => {
    const response = await call(app(), "/todo/uberto/book/rename", "other=1")

    expect(response.status).toBe(400)
    expect(response.body).toContain('data-problem="newName Blank"')
  })

  it("escapes the rejected input shown on the form", async () => {
    const raw = encodeURIComponent(`"><script>${"a".repeat(40)}`)

    const response = await call(app(), "/todo/uberto/book/rename", `newName=${raw}`)

    expect(response.body).not.toContain("<script>")
  })

  it("answers a business rejection with its status and a fixed body", async () => {
    const response = await call(app(), "/todo/uberto/book/rename", "newName=music")

    expect(response).toMatchObject({ status: 400, body: "bad request" })
  })

  it("refuses a body that is not a form", async () => {
    const target = app()
    const response = await Effect.runPromise(
      target(
        new Request("http://localhost/todo/uberto/book/rename", {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: '{"newName":"x"}'
        })
      )
    )

    expect(response.status).toBe(400)
  })
})
