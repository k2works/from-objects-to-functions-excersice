package zettai

/** これから行いたい意図。イベントと違い、拒否されうる。 */
enum ToDoListCommand:
  case CreateToDoList(user: User, name: ListName)
  case AddToDoItem(user: User, name: ListName, item: ToDoItem)
  case ChangeItemStatus(user: User, name: ListName, description: String, status: ToDoStatus)

object Commands:

  private def requireList(state: ToDoListState, user: User, name: ListName): Either[ZettaiError, ToDoList] =
    Events.listFor(state, user, name).toRight(ZettaiError.ListNotFound(user, name))

  private def requireItem(user: User, name: ListName, description: String, list: ToDoList): Either[ZettaiError, ToDoItem] =
    list.items.find(_.description == description).toRight(ZettaiError.ItemNotFound(user, name, description))

  private def checkTransition(description: String, item: ToDoItem, next: ToDoStatus): Either[ZettaiError, String] =
    if Domain.canTransition(item.status, next) then Right(description)
    else Left(ZettaiError.InvalidTransition(description, item.status, next))

  /** コマンドと現在の状態から、新しいイベントを決める純粋関数。 */
  def handle(command: ToDoListCommand, state: ToDoListState): Either[ZettaiError, List[ToDoListEvent]] =
    command match
      case ToDoListCommand.CreateToDoList(user, name) =>
        Events.listFor(state, user, name) match
          case Some(_) => Left(ZettaiError.ListAlreadyExists(user, name))
          case None    => Right(List(ToDoListEvent.ListCreated(user, name)))
      case ToDoListCommand.AddToDoItem(user, name, item) =>
        for _ <- requireList(state, user, name)
        yield List(ToDoListEvent.ItemAdded(user, name, item))
      case ToDoListCommand.ChangeItemStatus(user, name, description, next) =>
        for
          list    <- requireList(state, user, name)
          item    <- requireItem(user, name, description, list)
          allowed <- checkTransition(description, item, next)
        yield List(ToDoListEvent.ItemStatusChanged(user, name, allowed, next))
