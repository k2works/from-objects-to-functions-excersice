package zettai.domain.commands

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll
import zettai.arbCommand
import zettai.arbEvents
import zettai.domain.events.replayFrom
import zettai.interleave

/** command の判断は、その集約のイベントだけで決まる。他の集約のイベントが混ざっても変わらない。 */
class CommandIsolationTest : StringSpec({
    "他の集約のイベントを途中に混ぜても、command の判断は変わらない" {
        checkAll(arbEvents, arbEvents, arbCommand) { events, extra, command ->
            val own = events.filter { it.user == command.user && it.listName == command.listName }
            val foreign = extra.filter { it.user != command.user || it.listName != command.listName }

            handle(command, interleave(own, foreign).replayFrom()) shouldBe handle(command, own.replayFrom())
        }
    }
})
