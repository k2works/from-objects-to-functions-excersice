package zettai.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class ToDoListTest : StringSpec({
    "項目は既定状態と任意の期日を immutable な値として持つ" {
        val item = ToDoItem("write chapter", dueDate = LocalDate.parse("2026-11-01"))

        item.status shouldBe ToDoStatus.Todo
        item.dueDate shouldBe LocalDate.parse("2026-11-01")
    }

    "状態には有限個の値だけを指定できる" {
        ToDoStatus.entries shouldBe listOf(
            ToDoStatus.Todo,
            ToDoStatus.InProgress,
            ToDoStatus.Done,
            ToDoStatus.Blocked,
        )
    }
})
