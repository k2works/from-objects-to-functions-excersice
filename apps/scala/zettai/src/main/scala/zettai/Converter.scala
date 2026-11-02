package zettai

import cats.Invariant

/** 木との双方向の束。型 A は、encode の入力にも、decode の出力にも現れる。 */
final case class Converter[A](encode: A => JsonTree, decode: JsonTree => Either[String, A])

object Converter:

  val string: Converter[String] =
    Converter(
      JsonTree.JString(_),
      {
        case JsonTree.JString(s) => Right(s)
        case _                   => Left("not a string")
      }
    )

  /** 反変の位置と共変の位置の両方に現れるので、使えるのは dimap ではなく、行きと戻りの両方向の写像を渡す imap。 */
  given Invariant[Converter] with
    def imap[A, B](fa: Converter[A])(f: A => B)(g: B => A): Converter[B] =
      Converter(b => fa.encode(g(b)), json => fa.decode(json).map(f))
