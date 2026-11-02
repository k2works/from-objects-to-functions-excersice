package zettai

final case class User(name: String)

final case class ListName(name: String)

enum ToDoStatus:
  case Todo, InProgress, Done, Blocked

final case class ToDoItem(description: String, status: ToDoStatus)

object ToDoItem:
  def todo(description: String): ToDoItem = ToDoItem(description, ToDoStatus.Todo)

final case class ToDoList(name: ListName, items: List[ToDoItem])

object Domain:

  /** 許される状態遷移。Done からは戻せない。Todo から Blocked へは直接移せない。 */
  def canTransition(current: ToDoStatus, next: ToDoStatus): Boolean =
    (current, next) match
      case (ToDoStatus.Todo, ToDoStatus.InProgress)    => true
      case (ToDoStatus.Todo, ToDoStatus.Done)          => true
      case (ToDoStatus.InProgress, ToDoStatus.Done)    => true
      case (ToDoStatus.InProgress, ToDoStatus.Blocked) => true
      case (ToDoStatus.Blocked, ToDoStatus.InProgress) => true
      case _                                           => false

  /** 説明が最初に一致した項目だけの状態を変える。状態への適用と射影の両方がこの規則を共有する。 */
  def changeStatus(items: List[ToDoItem], description: String, status: ToDoStatus): List[ToDoItem] =
    items.indexWhere(_.description == description) match
      case -1    => items
      case index => items.updated(index, items(index).copy(status = status))

  def findToDoList(lists: Map[User, List[ToDoList]], user: User, name: ListName): Option[ToDoList] =
    lists.get(user).flatMap(_.find(_.name == name))
