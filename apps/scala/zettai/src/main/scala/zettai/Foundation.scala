package zettai

import cats.effect.IO

object Foundation:

  def wordScore(word: String): Int = word.length

  def nonBlankName(name: String): Either[String, String] =
    if name.trim.isEmpty then Left("名前は空にできません")
    else Right(name)

  def loadGreeting(load: IO[String]): IO[String] = load
