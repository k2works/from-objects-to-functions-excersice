package zettai.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import zettai.domain.ListName
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.errors.InvalidTransition
import zettai.domain.errors.ItemNotFound
import zettai.domain.errors.PersistenceFailure

class DescribeErrorTest : StringSpec({
    val alice = User("alice")
    val book = ListName("book")

    "項目が見つからない理由を、利用者向けの日本語にする" {
        describeError(ItemNotFound(alice, book, "write")) shouldBe "その項目は見つかりません"
    }

    "許されない状態変更の理由を、利用者向けの日本語にする" {
        describeError(InvalidTransition("write", ToDoStatus.Done, ToDoStatus.Todo)) shouldBe
            "その状態には変更できません"
    }

    "永続化の失敗の理由に、原因を含めない" {
        val message = describeError(PersistenceFailure("jdbc:postgresql://db.internal password=secret"))

        message shouldNotContain "secret"
        message shouldNotContain "db.internal"
        message shouldBe "保存に失敗しました。しばらくしてから、もう一度お試しください"
    }
})
