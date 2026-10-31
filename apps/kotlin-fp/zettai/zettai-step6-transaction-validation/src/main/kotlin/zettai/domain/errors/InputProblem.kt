package zettai.domain.errors

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
