import { Option } from "effect"
import { expect } from "vitest"
import { addToDoItem, changeItemStatus, createToDoList } from "../../src/domain/commands.ts"
import { makeToDoItem } from "../../src/domain/todo-list.ts"
import { listNameOf, userOf } from "../support/model.ts"
import type { ZettaiDriver } from "./driver.ts"

// 利用者の言葉で、できることと、できないことを書きます。
export const listOwner = (zettai: ZettaiDriver, userName: string) => {
  const itemsOf = (listName: string) => zettai.itemsOf(userOf(userName), listNameOf(listName))

  return {
    creates: (listName: string) =>
      zettai.send(createToDoList(userOf(userName), listNameOf(listName))),
    adds: (listName: string, description: string) =>
      zettai.send(addToDoItem(userOf(userName), listNameOf(listName), makeToDoItem(description))),
    finishes: (listName: string, description: string) =>
      zettai.send(changeItemStatus(userOf(userName), listNameOf(listName), description, "Done")),
    renames: async (listName: string, input: string) => {
      expect(await zettai.rename(userOf(userName), listNameOf(listName), input)).toEqual([])
    },
    cannotRename: async (listName: string, input: string, problems: ReadonlyArray<string>) => {
      expect(await zettai.rename(userOf(userName), listNameOf(listName), input)).toEqual(problems)
    },
    seesLists: async (expected: ReadonlyArray<readonly [string, number, number]>) => {
      expect(await zettai.listsOf(userOf(userName))).toEqual(
        expected.map(([name, items, done]) => ({ name, items, done }))
      )
    },
    canSee: async (listName: string, items: ReadonlyArray<string>) => {
      expect(await itemsOf(listName)).toEqual(Option.some(items))
    },
    cannotSee: async (listName: string) => {
      expect(await itemsOf(listName)).toEqual(Option.none())
    }
  }
}
