package zettai.domain.validation

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right

const val MAX_NAME_LENGTH = 40

/** 検証する入力。画面に誤りを示すときの名前を持つ。 */
enum class InputField(val label: String) {
    User("利用者名"),
    ListName("リスト名"),
    NewName("新しい名前"),
}

sealed interface InputProblem {
    data object Blank : InputProblem

    data class TooLong(val max: Int, val actual: Int) : InputProblem

    data class ForbiddenCharacter(val character: Char) : InputProblem
}

data class FieldProblem(val field: InputField, val problem: InputProblem)

/**
 * 独立した検証の結果を合成する。どれかが失敗しても止まらず、すべての失敗を左から順に集める。
 * 前の結果を使って次を決める `flatMap` と違い、検証は前の結果を使わないので、止まる必要がない。
 */
fun <P, A, B, R> zipAll(
    first: Either<NonEmptyList<P>, A>,
    second: Either<NonEmptyList<P>, B>,
    transform: (A, B) -> R,
): Either<NonEmptyList<P>, R> =
    Either.zipOrAccumulate({ x, y -> x + y }, first, second, transform)

fun <P, A, B, C, R> zipAll(
    first: Either<NonEmptyList<P>, A>,
    second: Either<NonEmptyList<P>, B>,
    third: Either<NonEmptyList<P>, C>,
    transform: (A, B, C) -> R,
): Either<NonEmptyList<P>, R> =
    Either.zipOrAccumulate({ x, y -> x + y }, first, second, third, transform)

private fun notBlank(field: InputField, raw: String): Either<NonEmptyList<FieldProblem>, String> =
    if (raw.isNotBlank()) raw.right() else nonEmptyListOf(FieldProblem(field, InputProblem.Blank)).left()

private fun withinLength(field: InputField, raw: String): Either<NonEmptyList<FieldProblem>, String> =
    if (raw.length <= MAX_NAME_LENGTH) {
        raw.right()
    } else {
        nonEmptyListOf(FieldProblem(field, InputProblem.TooLong(MAX_NAME_LENGTH, raw.length))).left()
    }

/** URL の区切りの `/` と、制御文字は使えない。最初に見つけた 1 文字を示す。 */
private fun noForbiddenCharacter(field: InputField, raw: String): Either<NonEmptyList<FieldProblem>, String> =
    raw.firstOrNull { it == '/' || it.isISOControl() }
        ?.let { nonEmptyListOf(FieldProblem(field, InputProblem.ForbiddenCharacter(it))).left() }
        ?: raw.right()

/** 1 つの名前の、空、長さ、使えない文字を独立に確かめ、誤りをすべて集める。 */
fun validateName(field: InputField, raw: String): Either<NonEmptyList<FieldProblem>, String> =
    zipAll(notBlank(field, raw), withinLength(field, raw), noForbiddenCharacter(field, raw)) { _, _, _ -> raw }

/** 利用者名、リスト名、新しい名前を独立に確かめ、複数の入力にまたがる誤りをすべて集める。 */
fun validateNames(
    rawUser: String,
    rawList: String,
    rawNewName: String,
): Either<NonEmptyList<FieldProblem>, Triple<String, String, String>> =
    zipAll(
        validateName(InputField.User, rawUser),
        validateName(InputField.ListName, rawList),
        validateName(InputField.NewName, rawNewName),
    ) { user, list, newName -> Triple(user, list, newName) }
