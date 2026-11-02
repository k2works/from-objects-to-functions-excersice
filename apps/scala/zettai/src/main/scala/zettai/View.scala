package zettai

import cats.Functor
import cats.data.Kleisli
import cats.effect.IO

/** 同じ「リストを HTML にする」処理を、依存の渡し方を変えて 3 通りに書く。 */
object View:

  /** 1. 関数を持つ値を、引数で渡す。 */
  def byHub(hub: Hub, user: User, name: ListName): IO[Option[String]] =
    hub.getList(user, name).map(_.map(Html.render))

  /** 2. 依存を Kleisli（読み取り専用の環境）に隠す。 */
  def byReader(user: User, name: ListName): Kleisli[IO, Hub, Option[String]] =
    Kleisli(hub => byHub(hub, user, name))

  /** 3. 依存を型クラスの形で渡し、効果の種類 F も呼び出し側に委ねる。 */
  def byTagless[F[_]: Functor](lists: ToDoLists[F], user: User, name: ListName): F[Option[String]] =
    Functor[F].map(lists.get(user, name))(_.map(Html.render))
