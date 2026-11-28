// テンプレートが作った HTML の断片です。# の項目があるので、同じ形のオブジェクトでは代わりになりません。
class Fragment {
  readonly #text: string

  constructor(text: string) {
    this.#text = text
  }

  get text(): string {
    return this.#text
  }
}

export type Html = Fragment

const entities: Readonly<Record<string, string>> = {
  "&": "&amp;",
  "<": "&lt;",
  ">": "&gt;",
  '"': "&quot;",
  "'": "&#39;"
}

const escapeText = (text: string): string =>
  text.replace(/[&<>"']/g, (character) => entities[character] ?? character)

type Value = string | number | Html | ReadonlyArray<Html>

// 文字列は必ずエスケープします。テンプレートが作った断片は、そのまま置きます。
const place = (value: Value): string => {
  if (typeof value === "string") return escapeText(value)
  if (typeof value === "number") return String(value)
  return value instanceof Fragment ? value.text : value.map((each) => each.text).join("")
}

// タグの名前を html にしないでください。Prettier が中身を HTML として整形し、出力に空白が増えます。
export const markup = (strings: TemplateStringsArray, ...values: ReadonlyArray<Value>): Html =>
  new Fragment(
    strings.reduce((text, part, index) => {
      const value = values[index - 1]
      return value === undefined ? text + part : text + place(value) + part
    }, "")
  )

export const render = (fragment: Html): string => fragment.text
