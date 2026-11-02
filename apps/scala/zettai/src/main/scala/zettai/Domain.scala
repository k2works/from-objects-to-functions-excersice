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

  def findToDoList(lists: Map[User, List[ToDoList]], user: User, name: ListName): Option[ToDoList] =
    lists.get(user).flatMap(_.find(_.name == name))
