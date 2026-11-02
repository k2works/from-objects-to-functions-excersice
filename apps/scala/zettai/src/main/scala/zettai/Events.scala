package zettai

/** すでに起きた出来事。命令ではないので過去形で名付ける。 */
enum ToDoListEvent:
  case ListCreated(user: User, name: ListName)
  case ItemAdded(user: User, name: ListName, item: ToDoItem)
  case ItemStatusChanged(user: User, name: ListName, description: String, status: ToDoStatus)

/** イベントから導出される現在の状態。 */
final case class ToDoListState(lists: Map[(User, ListName), ToDoList])

object Events:

  val empty: ToDoListState = ToDoListState(Map.empty)

  def listFor(state: ToDoListState, user: User, name: ListName): Option[ToDoList] =
    state.lists.get((user, name))

  private def put(state: ToDoListState, user: User, name: ListName, list: ToDoList): ToDoListState =
    ToDoListState(state.lists.updated((user, name), list))

  /** 1 つのイベントを適用し、新しい状態を返す。元の状態は変わらない。 */
  def apply(state: ToDoListState, event: ToDoListEvent): ToDoListState =
    event match
      case ToDoListEvent.ListCreated(user, name) =>
        put(state, user, name, ToDoList(name, Nil))
      case ToDoListEvent.ItemAdded(user, name, item) =>
        listFor(state, user, name) match
          case Some(ToDoList(_, items)) => put(state, user, name, ToDoList(name, items :+ item))
          case None                     => state
      case ToDoListEvent.ItemStatusChanged(user, name, description, status) =>
        listFor(state, user, name) match
          case Some(ToDoList(_, items)) =>
            put(state, user, name, ToDoList(name, Domain.changeStatus(items, description, status)))
          case None => state
