package zettai.domain.validation

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import zettai.domain.validation.InputField.ListName
import zettai.domain.validation.InputField.NewName
import zettai.domain.validation.InputField.User
import zettai.domain.validation.InputProblem.Blank
import zettai.domain.validation.InputProblem.ForbiddenCharacter
import zettai.domain.validation.InputProblem.TooLong

class ValidationTest : StringSpec({
    "正しい名前は通る" {
        validateName(NewName, "novel") shouldBe "novel".right()
    }

    "空の名前は Blank だけを返す" {
        validateName(NewName, "") shouldBe nonEmptyListOf(FieldProblem(NewName, Blank)).left()
    }

    "長すぎる名前は TooLong を返す" {
        validateName(NewName, "x".repeat(41)) shouldBe
            nonEmptyListOf(FieldProblem(NewName, TooLong(MAX_NAME_LENGTH, 41))).left()
    }

    "使えない文字を含む名前は ForbiddenCharacter を返す" {
        validateName(NewName, "a/b") shouldBe nonEmptyListOf(FieldProblem(NewName, ForbiddenCharacter('/'))).left()
    }

    "1 つの入力の複数の規則の誤りを、規則の順にすべて集める" {
        validateName(NewName, "/".repeat(41)) shouldBe nonEmptyListOf(
            FieldProblem(NewName, TooLong(MAX_NAME_LENGTH, 41)),
            FieldProblem(NewName, ForbiddenCharacter('/')),
        ).left()

        validateName(NewName, " ".repeat(41)) shouldBe nonEmptyListOf(
            FieldProblem(NewName, Blank),
            FieldProblem(NewName, TooLong(MAX_NAME_LENGTH, 41)),
        ).left()
    }

    "複数の入力にまたがる誤りを、入力の順にすべて集める" {
        validateNames("", "a/b", " ") shouldBe nonEmptyListOf(
            FieldProblem(User, Blank),
            FieldProblem(ListName, ForbiddenCharacter('/')),
            FieldProblem(NewName, Blank),
        ).left()
    }

    "すべて正しければ、3 つの値を返す" {
        validateNames("uberto", "book", "novel") shouldBe
            Triple("uberto", "book", "novel").right()
    }

    // 対比：flatMap は前の結果を使って次を決める型なので、最初の失敗で止まる。検証は前の結果を使わない
    "同じ規則を flatMap で繋ぐと、最初の失敗で止まり、2 つ目の誤りが見えない" {
        val raw = " ".repeat(41)

        fun notBlank(value: String): Either<List<InputProblem>, String> =
            if (value.isNotBlank()) value.right() else listOf<InputProblem>(Blank).left()

        fun withinLength(value: String): Either<List<InputProblem>, String> =
            if (value.length <= MAX_NAME_LENGTH) {
                value.right()
            } else {
                listOf<InputProblem>(TooLong(MAX_NAME_LENGTH, value.length)).left()
            }

        val chained = notBlank(raw).flatMap(::withinLength)

        chained shouldBe listOf<InputProblem>(Blank).left()
        validateName(NewName, raw).leftOrNull()?.size shouldBe 2
    }
})
