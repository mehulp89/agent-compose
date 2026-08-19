package io.github.mehulp89.agentcompose.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun `parses headings bullets paragraphs and code`() {
        val blocks = parseMarkdownBlocks(
            """
            # Title

            Intro **text**.
            - First

            ```kotlin
            val answer = 42
            ```
            """.trimIndent(),
        )

        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertTrue(blocks[1] is MarkdownBlock.Paragraph)
        assertTrue(blocks[2] is MarkdownBlock.Bullet)
        assertEquals("kotlin", (blocks[3] as MarkdownBlock.Code).language)
    }

    @Test
    fun `keeps an unfinished streaming code fence visible`() {
        val blocks = parseMarkdownBlocks("```kotlin\nval value =")

        assertEquals(1, blocks.size)
        assertEquals("val value =", (blocks.single() as MarkdownBlock.Code).code)
    }
}
