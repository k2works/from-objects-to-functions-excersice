package zettai.domain.errors

import zettai.domain.{ListName, ToDoStatus, User}
import zettai.domain.validation.FieldProblem

/** 業務上ありうる失敗の集合。文字列ではなく、判断に使った値を持つ。 */
enum ZettaiError:
  case ListNotFound(user: User, name: ListName)
  case ListAlreadyExists(user: User, name: ListName)
  case ItemNotFound(user: User, name: ListName, description: String)
  case InvalidTransition(description: String, from: ToDoStatus, to: ToDoStatus)
  case VersionConflict(expected: Int, actual: Int)
  case InvalidInput(problems: List[FieldProblem])
