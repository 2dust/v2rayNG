package com.v2ray.ang.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranslatorsParserTest {

    @Test
    fun parsePreservesNestedGenericContributors() {
        val source = """
            [{"language":"en","contributors":[
                {"name":"alice","displayName":"Alice","url":"https://example.com/alice"},
                {"name":"bob"}]},
             {"language":"ru","contributors":[{"name":"carol"}]}]
        """.trimIndent()

        val credits = TranslatorsParser.parse(source)

        assertEquals(
            listOf(
                TranslatorsCredit(
                    "en",
                    listOf(Contributor("alice", "Alice", "https://example.com/alice"), Contributor("bob"))
                ),
                TranslatorsCredit("ru", listOf(Contributor("carol")))
            ),
            credits
        )
    }
}
