package zettai.application

import zettai.domain.{Domain, ListName, ToDoList, User}

import cats.Applicative

/** 依存を「型クラスのように」渡す版（タグレス・ファイナル）。効果の種類 F は呼び出し側が決める。 */
trait ToDoLists[F[_]]:
  def get(user: User, name: ListName): F[Option[ToDoList]]

object ToDoLists:

  def inMemory[F[_]: Applicative](lists: Map[User, List[ToDoList]]): ToDoLists[F] =
    (user, name) => Applicative[F].pure(Domain.findToDoList(lists, user, name))
