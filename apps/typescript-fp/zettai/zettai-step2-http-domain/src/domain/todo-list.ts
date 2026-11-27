export interface User {
  readonly name: string
}

export interface ListName {
  readonly name: string
}

export interface ToDoItem {
  readonly description: string
}

export interface ToDoList {
  readonly listName: ListName
  readonly items: ReadonlyArray<ToDoItem>
}
