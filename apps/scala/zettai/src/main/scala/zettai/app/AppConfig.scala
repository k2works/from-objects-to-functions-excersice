package zettai.app

import com.comcast.ip4s.*

/** 起動の設定。環境から読んだ値は、起動の前に検査して、失敗は値にする。 */
object AppConfig:

  /** 環境変数 ZETTAI_PORT の値からポートを決める。無ければ 8080 番。 */
  def portFrom(value: Option[String]): Either[String, Port] =
    value match
      case None => Right(port"8080")
      case Some(text) =>
        if text.nonEmpty && text.forall(_.isDigit) then
          Port.fromString(text).toRight(s"ZETTAI_PORT は 0 から 65535 の数字にしてください")
        else Left("ZETTAI_PORT は 0 から 65535 の数字にしてください")
