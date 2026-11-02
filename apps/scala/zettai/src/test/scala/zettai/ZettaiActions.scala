package zettai

import cats.effect.IO

/** シナリオが使う操作。どの経路で実行されるかをシナリオは知らない。 */
final case class ZettaiActions(
    name: String,
    getToDoList: (User, ListName) => IO[Option[List[ToDoItem]]]
)
