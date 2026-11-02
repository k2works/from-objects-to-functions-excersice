package zettai

import cats.effect.IO

/** 読み取り専用の入口。コマンドを実行する操作を、型として持たない。 */
final case class QueryHub(listRows: User => IO[List[ToDoListRow]])

object QueryHub:

  def queryHub(store: EventStore): QueryHub =
    QueryHub(user => store.load.map(events => Projection.projectFrom(user, events)))
