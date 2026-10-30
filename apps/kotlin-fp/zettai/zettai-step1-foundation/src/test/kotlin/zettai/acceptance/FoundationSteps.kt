package zettai.acceptance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.kotest.matchers.shouldBe
import org.http4k.core.Method.GET
import org.http4k.core.Request
import zettai.scoreHandler
import zettai.wordScore

class FoundationSteps {
    private lateinit var path: String
    private lateinit var word: String
    private var score: Int = -1

    @Given("実行経路が {string} である")
    fun executionPath(path: String) {
        this.path = path
    }

    @When("単語 {string} の得点を求める")
    fun scoreWord(word: String) {
        this.word = word
        score = when (path) {
            "domain" -> wordScore(word)
            "HTTP" -> scoreHandler()(Request(GET, "/score/$word")).bodyString().toInt()
            else -> error("未対応の実行経路です: $path")
        }
    }

    @Then("得点は {int} である")
    fun scoreIs(expected: Int) {
        score shouldBe expected
    }
}
