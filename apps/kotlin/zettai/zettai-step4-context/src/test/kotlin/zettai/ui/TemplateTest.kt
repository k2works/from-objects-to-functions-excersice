package zettai.ui

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import zettai.fp.Failure
import zettai.fp.Success

class TemplateTest {
    @Test
    fun `文字列のタグを差し込む`() {
        val result = Template("<h1>{{title}}</h1>").render(mapOf("title" to StringTag("Zettai")))

        expectThat(result).isEqualTo(Success("<h1>Zettai</h1>"))
    }

    @Test
    fun `リストのタグを繰り返す`() {
        val result = Template("{{#items}}<li>{{name}}</li>{{/items}}").render(
            mapOf(
                "items" to ListTag(
                    listOf(
                        mapOf("name" to StringTag("write chapter")),
                        mapOf("name" to StringTag("publish book"))
                    )
                )
            )
        )

        expectThat(result).isEqualTo(Success("<li>write chapter</li><li>publish book</li>"))
    }

    @Test
    fun `真偽のタグで出し分ける`() {
        val template = Template("{{#hasError}}<p>{{message}}</p>{{/hasError}}")

        expectThat(template.render(mapOf("hasError" to BooleanTag(false))))
            .isEqualTo(Success(""))
        expectThat(
            template.render(mapOf("hasError" to BooleanTag(true), "message" to StringTag("だめ")))
        ).isEqualTo(Success("<p>だめ</p>"))
    }

    @Test
    fun `未適用のタグが残っていたら失敗する`() {
        val result = Template("<h1>{{title}}</h1><p>{{subtitle}}</p>")
            .render(mapOf("title" to StringTag("Zettai")))

        expectThat(result).isA<Failure<*>>()
    }

    @Test
    fun `未適用のタグの名前を教える`() {
        val result = Template("{{a}}{{b}}{{c}}").render(mapOf("a" to StringTag("あ")))

        val message = (result as Failure).error.message

        expectThat(message).contains("b")
        expectThat(message).contains("c")
    }

    @Test
    fun `リストの中の未適用タグも検出する`() {
        val result = Template("{{#items}}<li>{{name}} {{status}}</li>{{/items}}").render(
            mapOf("items" to ListTag(listOf(mapOf("name" to StringTag("write chapter")))))
        )

        expectThat(result).isA<Failure<*>>()
    }
}
