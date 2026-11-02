package zettai

import cats.effect.IO

/** 読み取りの依存を、関数を持つ値として渡す。 */
final case class Hub(getList: (User, ListName) => IO[Option[ToDoList]])

object Hub:

  def inMemory(lists: Map[User, List[ToDoList]]): Hub =
    Hub((user, name) => IO.pure(Domain.findToDoList(lists, user, name)))
