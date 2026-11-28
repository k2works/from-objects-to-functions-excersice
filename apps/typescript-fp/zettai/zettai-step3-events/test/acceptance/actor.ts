import { Option } from "effect"
import { expect } from "vitest"
import { listNameOf, userOf } from "../support/model.ts"
import type { ZettaiDriver } from "./driver.ts"

// 利用者の言葉で、できることと、できないことを書きます。
export const listOwner = (zettai: ZettaiDriver, userName: string) => {
  const itemsOf = (listName: string) => zettai.itemsOf(userOf(userName), listNameOf(listName))

  return {
    canSee: async (listName: string, items: ReadonlyArray<string>) => {
      expect(await itemsOf(listName)).toEqual(Option.some(items))
    },
    cannotSee: async (listName: string) => {
      expect(await itemsOf(listName)).toEqual(Option.none())
    }
  }
}
