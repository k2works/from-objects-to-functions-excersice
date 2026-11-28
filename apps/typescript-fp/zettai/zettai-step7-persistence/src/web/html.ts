import type { ToDoListRow } from "../domain/projection.ts"
import type { ToDoList, User } from "../domain/todo-list.ts"

const entities: Readonly<Record<string, string>> = {
  "&": "&amp;",
  "<": "&lt;",
  ">": "&gt;",
  '"': "&quot;",
  "'": "&#39;"
}

const escapeHtml = (text: string): string =>
  text.replace(/[&<>"']/g, (character) => entities[character] ?? character)

const page = (heading: string, content: string): string => {
  const head = `<head><meta charset="utf-8"><title>Zettai</title></head>`
  const body = `<body><h1>${escapeHtml(heading)}</h1>${content}</body>`
  return `<!doctype html><html>${head}${body}</html>`
}

export const renderPage = (list: ToDoList): string => {
  const items = list.items.map((item) => `<li>${escapeHtml(item.description)}</li>`).join("")
  return page(list.listName, `<ul>${items}</ul>`)
}

const renderRow = ({ listName, itemCount, doneCount }: ToDoListRow): string =>
  `<tr><td>${escapeHtml(listName)}</td><td>${itemCount}</td><td>${doneCount}</td></tr>`

export const renderRowsPage = (user: User, rows: ReadonlyArray<ToDoListRow>): string => {
  const header = "<tr><th>list</th><th>items</th><th>done</th></tr>"
  return page(user, `<table>${header}${rows.map(renderRow).join("")}</table>`)
}
