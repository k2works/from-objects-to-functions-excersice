package zettai.web

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TemplateTest : StringSpec({
    "{{x}} は HTML エスケープして埋める" {
        renderTemplate("<p>{{x}}</p>", mapOf("x" to "<b>&\"'")) shouldBe
            "<p>&lt;b&gt;&amp;&quot;&#39;</p>".right()
    }

    "{{{x}}} は生のまま埋める" {
        renderTemplate("<ul>{{{items}}}</ul>", mapOf("items" to "<li>a</li>")) shouldBe
            "<ul><li>a</li></ul>".right()
    }

    "同じタグを何度でも埋める" {
        renderTemplate("{{x}}-{{x}}", mapOf("x" to "a")) shouldBe "a-a".right()
    }

    "埋めていないタグがあれば、名前を集めて失敗する" {
        renderTemplate("{{a}} {{b}} {{{c}}} {{a}}", mapOf("b" to "x")) shouldBe
            UnappliedTags(listOf("a", "c")).left()
    }

    "埋めた値の中のタグは、もう一度は解釈しない" {
        renderTemplate("{{x}}/{{y}}", mapOf("x" to "{{y}}", "y" to "z")) shouldBe
            "{{y}}/z".right()
    }

    "生で埋めた値の中のタグも、未適用の誤りにならない" {
        renderTemplate("{{{x}}}", mapOf("x" to "{{y}}")) shouldBe "{{y}}".right()
    }
})
