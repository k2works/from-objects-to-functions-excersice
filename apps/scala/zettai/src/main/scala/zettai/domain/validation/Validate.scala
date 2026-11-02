package zettai.domain.validation

import zettai.domain.{ListName, User}
import zettai.domain.commands.ToDoListCommand

import cats.data.ValidatedNec
import cats.syntax.all.*

/** 利用者が入力する欄。 */
enum InputField:
  case UserName, ListNameField, NewName

/** 入力の誤り。誤りのもとになった値は持たない（メッセージや記録に入力をそのまま出さないため）。 */
enum FieldProblem:
  case Blank(field: InputField)
  case TooLong(field: InputField, limit: Int)
  case ForbiddenCharacter(field: InputField)

object Validate:

  val maxLength: Int = 40

  private def fail[A](problem: FieldProblem): ValidatedNec[FieldProblem, A] = problem.invalidNec

  def notBlank(field: InputField, raw: String): ValidatedNec[FieldProblem, Unit] =
    if raw.trim.isEmpty then fail(FieldProblem.Blank(field)) else ().validNec

  def withinLength(field: InputField, raw: String): ValidatedNec[FieldProblem, Unit] =
    if raw.length > maxLength then fail(FieldProblem.TooLong(field, maxLength)) else ().validNec

  /** 「/」と制御文字を許さない。 */
  def noForbiddenCharacter(field: InputField, raw: String): ValidatedNec[FieldProblem, Unit] =
    if raw.exists(c => c == '/' || c.isControl) then fail(FieldProblem.ForbiddenCharacter(field)) else ().validNec

  /** 1 つの入力の 3 つの規則は互いに独立なので、すべて確かめて、誤りを規則の順に集める。 */
  def validateName(field: InputField, raw: String): ValidatedNec[FieldProblem, String] =
    (notBlank(field, raw), withinLength(field, raw), noForbiddenCharacter(field, raw)).mapN((_, _, _) => raw)

  /** 3 つの入力も互いに独立なので、誤りを入力の順にすべて集める。 */
  def validateRename(rawUser: String, rawList: String, rawNewName: String): ValidatedNec[FieldProblem, ToDoListCommand] =
    (
      validateName(InputField.UserName, rawUser),
      validateName(InputField.ListNameField, rawList),
      validateName(InputField.NewName, rawNewName)
    ).mapN((user, list, newName) => ToDoListCommand.RenameToDoList(User(user), ListName(list), ListName(newName)))

  private def label(field: InputField): String =
    field match
      case InputField.UserName      => "利用者名"
      case InputField.ListNameField => "リスト名"
      case InputField.NewName       => "新しい名前"

  /** 利用者に見せる説明。誤りのもとになった入力の値は、含めない。 */
  def describe(problem: FieldProblem): String =
    problem match
      case FieldProblem.Blank(field)              => s"${label(field)}は空にできません"
      case FieldProblem.TooLong(field, limit)     => s"${label(field)}は $limit 文字以内にしてください"
      case FieldProblem.ForbiddenCharacter(field) => s"${label(field)}に使えない文字が含まれています"

  /** すべての誤りを、1 つの説明にまとめる。 */
  def explain(problems: List[FieldProblem]): String =
    "入力に誤りがあります。" + problems.map(p => s"\n- ${describe(p)}").mkString
