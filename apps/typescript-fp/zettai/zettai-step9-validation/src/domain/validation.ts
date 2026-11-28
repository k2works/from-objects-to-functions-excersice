import { Result } from "effect"

// 検証の結果です。失敗は、見つけた問題を順に並べた配列にします。
export type Validation<A, E> = Result.Result<A, ReadonlyArray<E>>

const problemsOf = <A, E>(validation: Validation<A, E>): ReadonlyArray<E> =>
  Result.isFailure(validation) ? validation.failure : []

// 2 つの検証を、どちらも実行して合わせます。片方でも失敗なら、両方の問題を左から順に並べます。
export const zipWith = <A, B, C, E>(
  left: Validation<A, E>,
  right: Validation<B, E>,
  combine: (a: A, b: B) => C
): Validation<C, E> =>
  Result.isSuccess(left) && Result.isSuccess(right)
    ? Result.succeed(combine(left.success, right.success))
    : Result.fail([...problemsOf(left), ...problemsOf(right)])

// 条件を満たさなければ、問題を 1 つ返します。
export const check = <E>(satisfied: boolean, problem: E): Validation<null, E> =>
  satisfied ? Result.succeed(null) : Result.fail([problem])

// どの入力の問題かを付けます。
export const mapProblems = <A, E, F>(
  validation: Validation<A, E>,
  f: (problem: E) => F
): Validation<A, F> => Result.mapError(validation, (problems) => problems.map(f))
