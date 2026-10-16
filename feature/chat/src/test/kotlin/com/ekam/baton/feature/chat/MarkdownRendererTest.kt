package com.ekam.baton.feature.chat

import org.junit.Assert.*
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun testSanitizeChatTextStripsDangerousOverridesAndControlChars() {
        // RTL override \u202E and null char \u0000
        val malicious = "Hello\u202EWorld\u0000Test\u0007Bell"
        val sanitized = sanitizeChatText(malicious)
        assertEquals("HelloWorldTestBell", sanitized)
    }

    @Test
    fun testParseMarkdownBlocksStandard() {
        val input = "Here is some code:\n```kotlin\nval x = 42\n```\nAnd more text."
        val blocks = parseMarkdownBlocks(input)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownPart.Text)
        assertTrue(blocks[1] is MarkdownPart.CodeBlock)
        assertTrue(blocks[2] is MarkdownPart.Text)

        val code = blocks[1] as MarkdownPart.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val x = 42", code.content)
    }

    @Test
    fun testMassiveTextDoesNotExhaustMemoryOrHang() {
        // Construct a 70,000 character string with unclosed code blocks and formatting
        val sb = StringBuilder()
        for (i in 0 until 1000) {
            sb.append("This is line $i with **bold** and *italic* and `code` test.\n")
        }
        val largeText = sb.toString()
        assertTrue(largeText.length > 50_000)

        val startTime = System.currentTimeMillis()
        val blocks = parseMarkdownBlocks(largeText)
        val duration = System.currentTimeMillis() - startTime

        // Should complete in under 500ms
        assertTrue("Parsing took too long: ${duration}ms", duration < 500L)
        assertTrue(blocks.isNotEmpty())
    }

    @Test
    fun testTextBombWithDeepUnicodeRecursion() {
        val bomb = "\u202E".repeat(500) + "Boom" + "\u202D".repeat(500)
        val sanitized = sanitizeChatText(bomb)
        assertEquals("Boom", sanitized)
    }
}
