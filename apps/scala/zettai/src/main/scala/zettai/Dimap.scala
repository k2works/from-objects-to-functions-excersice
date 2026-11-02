package zettai

object Dimap:

  /** 入力を読み替え（逆向きに写し）、出力を書き換える（順向きに写す）。入力の側は反変、出力の側は共変になる。 */
  def dimap[A, A2, B, B2](pre: A2 => A, post: B => B2, f: A => B): A2 => B2 =
    x => post(f(pre(x)))
