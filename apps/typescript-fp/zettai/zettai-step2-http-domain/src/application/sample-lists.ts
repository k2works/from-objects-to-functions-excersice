import type { ToDoListStore } from "../domain/find-todo-list.ts"

export const sampleLists: ToDoListStore = new Map([
  [
    "uberto",
    [
      {
        listName: { name: "book" },
        items: [
          { description: "write chapter" },
          { description: "insert code" },
          { description: "publish book" }
        ]
      }
    ]
  ]
])
