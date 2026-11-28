export interface ListPage {
  readonly name: string
  readonly items: ReadonlyArray<string>
}

const entities: Readonly<Record<string, string>> = {
  "&amp;": "&",
  "&lt;": "<",
  "&gt;": ">",
  "&quot;": '"',
  "&#39;": "'"
}

const NOT_ESCAPED = /[<>"']|&(?!(?:amp|lt|gt|quot|#39);)/

// エスケープされていない値を見つけたら、読み戻しを失敗にします。
const readText = (raw: string): string => {
  if (NOT_ESCAPED.test(raw)) throw new Error(`not escaped: ${raw}`)
  return raw.replace(/&(?:amp|lt|gt|quot|#39);/g, (entity) => entities[entity] ?? entity)
}

export const readListPage = (html: string): ListPage => {
  const heading = /<h1>(.*?)<\/h1>/.exec(html)?.[1]
  if (heading === undefined) throw new Error(`not a list page: ${html}`)
  return {
    name: readText(heading),
    items: [...html.matchAll(/<li>(.*?)<\/li>/g)].map((match) => readText(match[1] ?? ""))
  }
}
