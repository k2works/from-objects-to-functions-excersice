import type { ToDoListRow } from "../domain/projection.ts"
import type { InputProblem, ListName, NameProblem, ToDoList, User } from "../domain/todo-list.ts"
import { markup, render } from "./template.ts"
import type { Html } from "./template.ts"

export const listPath = (user: User, listName: ListName): string =>
  `/todo/${encodeURIComponent(user)}/${encodeURIComponent(listName)}`

const page = (heading: string, content: Html): string => {
  const head = markup`<head><meta charset="utf-8"><title>Zettai</title></head>`
  const body = markup`<body><h1>${heading}</h1>${content}</body>`
  return `<!doctype html>${render(markup`<html>${head}${body}</html>`)}`
}

export const renderPage = (user: User, list: ToDoList): string => {
  const items = list.items.map((item) => markup`<li>${item.description}</li>`)
  const rename = `${listPath(user, list.listName)}/rename`
  return page(list.listName, markup`<ul>${items}</ul><p><a href="${rename}">rename</a></p>`)
}

const renderRow = ({ listName, itemCount, doneCount }: ToDoListRow): Html =>
  markup`<tr><td>${listName}</td><td>${itemCount}</td><td>${doneCount}</td></tr>`

export const renderRowsPage = (user: User, rows: ReadonlyArray<ToDoListRow>): string => {
  const header = markup`<tr><th>list</th><th>items</th><th>done</th></tr>`
  return page(user, markup`<table>${header}${rows.map(renderRow)}</table>`)
}

// 問題の種類を足すと、文言が足りないので型検査が失敗します。
const messages: Record<NameProblem, string> = {
  Blank: "名前を入力してください",
  TooLong: "名前は 40 文字以内にしてください",
  ForbiddenCharacter: "名前に使えない文字があります"
}

const renderProblem = ({ field, problem }: InputProblem): Html =>
  markup`<li data-problem="${field} ${problem}">${messages[problem]}</li>`

export const renderRenameForm = (
  user: User,
  listName: ListName,
  input: string,
  problems: ReadonlyArray<InputProblem>
): string => {
  const action = `${listPath(user, listName)}/rename`
  const reasons = markup`<ul>${problems.map(renderProblem)}</ul>`
  const field = markup`<input name="newName" value="${input}">`
  const form = markup`<form method="post" action="${action}">${field}<button>rename</button></form>`
  return page(`rename ${listName}`, markup`${reasons}${form}`)
}
