import { Result } from "effect"

// JSON との変換を、両方向まとめて持ちます。書く値の型 In と、読む値の型 Out は、別にできます。
export interface JsonCodec<In, Out> {
  readonly encode: (value: In) => unknown
  readonly decode: (json: unknown) => Result.Result<Out, unknown>
}

// 書く前に pre を、読んだ後に post を適用した変換を作ります。pre は、向きが逆です。
export const dimap = <In, Out, In2, Out2>(
  codec: JsonCodec<In, Out>,
  pre: (value: In2) => In,
  post: (value: Out) => Out2
): JsonCodec<In2, Out2> => ({
  encode: (value) => codec.encode(pre(value)),
  decode: (json) => Result.map(codec.decode(json), post)
})
