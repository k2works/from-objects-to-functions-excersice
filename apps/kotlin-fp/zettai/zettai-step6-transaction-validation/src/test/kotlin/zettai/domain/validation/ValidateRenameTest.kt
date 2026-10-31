package zettai.domain.validation

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import zettai.domain.ListName
import zettai.domain.User
import zettai.domain.commands.RenameToDoList
import zettai.domain.errors.FieldProblem
import zettai.domain.errors.InputField.NewName
import zettai.domain.errors.InputProblem.Blank
import zettai.domain.errors.InputProblem.ForbiddenCharacter
import zettai.domain.errors.InputProblem.TooLong
import zettai.domain.errors.InvalidInput

class ValidateRenameTest : StringSpec({
    "正しい入力は RenameToDoList になる" {
        validateRename("alice", "book", "novel") shouldBe
            RenameToDoList(User("alice"), ListName("book"), ListName("novel")).right()
    }

    "新しい名前が空なら、その誤りだけを InvalidInput に入れる" {
        validateRename("alice", "book", "") shouldBe
            InvalidInput(nonEmptyListOf(FieldProblem(NewName, Blank))).left()
    }

    "新しい名前の複数の誤りを、止まらずにすべて返す" {
        validateRename("alice", "book", "/".repeat(41)) shouldBe InvalidInput(
            nonEmptyListOf(
                FieldProblem(NewName, TooLong(MAX_NAME_LENGTH, 41)),
                FieldProblem(NewName, ForbiddenCharacter('/')),
            ),
        ).left()
    }

    "InvalidInput の message に入力の値を含めない" {
        InvalidInput(nonEmptyListOf(FieldProblem(NewName, Blank))).message shouldBe "Invalid input"
    }
})
