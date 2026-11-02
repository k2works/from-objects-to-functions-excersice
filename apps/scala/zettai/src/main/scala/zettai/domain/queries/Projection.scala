package zettai.domain.queries

import zettai.domain.{Domain, ListName, ToDoItem, ToDoStatus, User}
import zettai.domain.events.ToDoListEvent

/** 画面に出したい形。業務の判断に使う状態とは別に持つ。 */
final case class ToDoListRow(name: ListName, total: Int, done: Int)

object Projection:

  /** リスト名から項目の列への対応。射影の途中経過。 */
  private type Lists = Map[ListName, List[ToDoItem]]

  private def step(lists: Lists, event: ToDoListEvent): Lists =
    event match
      case ToDoListEvent.ListCreated(_, name) => lists.updated(name, Nil)
      case ToDoListEvent.ItemAdded(_, name, item) =>
        lists.get(name).fold(lists)(items => lists.updated(name, items :+ item))
      case ToDoListEvent.ListRenamed(_, name, _) => lists - name
      case ToDoListEvent.ItemStatusChanged(_, name, description, status) =>
        lists.get(name).fold(lists)(items => lists.updated(name, Domain.changeStatus(items, description, status)))

  private def owner(event: ToDoListEvent): User =
    event match
      case ToDoListEvent.ListCreated(user, _)             => user
      case ToDoListEvent.ItemAdded(user, _, _)            => user
      case ToDoListEvent.ItemStatusChanged(user, _, _, _) => user
      case ToDoListEvent.ListRenamed(user, _, _)          => user

  private def toRow(entry: (ListName, List[ToDoItem])): ToDoListRow =
    val (name, items) = entry
    ToDoListRow(name, items.size, items.count(_.status == ToDoStatus.Done))

  /** 状態を作るときと同じ畳み込みで、イベントを行へ写す。畳み込む先が違うだけ。 */
  def projectFrom(user: User, events: List[ToDoListEvent]): List[ToDoListRow] =
    events
      .filter(owner(_) == user)
      .foldLeft(Map.empty: Lists)(step)
      .toList
      .map(toRow)
