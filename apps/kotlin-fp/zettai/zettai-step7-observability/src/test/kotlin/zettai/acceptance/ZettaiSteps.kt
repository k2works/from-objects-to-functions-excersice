package zettai.acceptance

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.User

class ZettaiSteps {
    private val actionPaths = listOf(domainActions(), httpActions(), postgresActions())
    private var user: User? = null
    private var actual: Map<String, List<ToDoItem>?> = emptyMap()
    private var renamed: Map<String, RenameOutcome> = emptyMap()

    @Given("ユーザー {string} がリスト {string} に次の項目を持っている")
    fun listExists(userName: String, listName: String, items: DataTable) {
        val owner = User(userName)
        user = owner
        val descriptions = items.asLists().flatten().filterNotNull()
        val list = ToDoList(ListName(listName), descriptions.map { ToDoItem(it) })
        runBlocking { actionPaths.forEach { it.setUp(owner, list) } }
    }

    @Given("ユーザー {string} はリストを持っていない")
    fun userHasNoLists(userName: String) {
        user = User(userName)
    }

    @When("ユーザーがリスト {string} を見る")
    fun seeList(listName: String) {
        actual = runBlocking {
            actionPaths.associate { actions ->
                actions.name to actions.getToDoList(requireNotNull(user), ListName(listName))
            }
        }
    }

    @Then("項目は次のとおりである")
    fun itemsAre(items: DataTable) {
        val descriptions = items.asLists().flatten().filterNotNull()
        val expected = descriptions.map { ToDoItem(it) }
        actual.forEach { (path, result) ->
            withClue("$path 経路") { result shouldBe expected }
        }
    }

    @Then("リストは見つからない")
    fun listIsNotFound() {
        actual.forEach { (path, result) ->
            withClue("$path 経路") { result shouldBe null }
        }
    }

    @When("ユーザーがリスト {string} の名前を {string} に変える")
    fun renameList(listName: String, newName: String) {
        renamed = runBlocking {
            actionPaths.associate { it.name to it.rename(requireNotNull(user), ListName(listName), newName) }
        }
    }

    @When("ユーザーがリスト {string} の名前を {string} を {int} 回くり返した名前に変える")
    fun renameListToRepeated(listName: String, unit: String, times: Int) {
        renameList(listName, unit.repeat(times))
    }

    @Then("名前が変わった")
    fun renameSucceeded() {
        renamed.forEach { (path, outcome) ->
            withClue("$path 経路") { outcome shouldBe RenameOutcome.Renamed }
        }
    }

    @Then("名前は変わらず、理由は次のとおりである")
    fun renameRejected(reasons: DataTable) {
        val expected = RenameOutcome.Rejected(reasons.asLists().flatten().filterNotNull())
        renamed.forEach { (path, outcome) ->
            withClue("$path 経路") { outcome shouldBe expected }
        }
    }
}
