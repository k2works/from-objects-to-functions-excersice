import type { ToDoList } from "../domain/todo-list.ts"

const entities: Readonly<Record<string, string>> = {
  "&": "&amp;",
  "<": "&lt;",
  ">": "&gt;",
  '"': "&quot;",
  "'": "&#39;"
}

const escapeHtml = (text: string): string =>
  text.replace(/[&<>"']/g, (character) => entities[character] ?? character)

export const renderPage = (list: ToDoList): string => {
  const items = list.items.map((item) => `<li>${escapeHtml(item.description)}</li>`).join("")
  const head = `<head><meta charset="utf-8"><title>Zettai</title></head>`
  const body = `<body><h1>${escapeHtml(list.listName.name)}</h1><ul>${items}</ul></body>`
  return `<!doctype html><html>${head}${body}</html>`
}
