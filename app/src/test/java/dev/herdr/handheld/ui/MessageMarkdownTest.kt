package dev.herdr.handheld.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.*
import org.junit.Test

class MessageMarkdownTest {
    @Test fun assistantMessagesHaveNativeHeadingsListsAndCodeWithoutMarkdownDelimiters() {
        val value=MessageMarkdown.parse("# Result\n\n**Ready** for 한글.\n\n1. First\n2. Second\n\n```sh\necho ok\n```\n")
        assertEquals("Result\n\nReady for 한글.\n\n1. First\n2. Second\necho ok",value.text)
        assertTrue(value.spanStyles.any { it.item.fontWeight==FontWeight.Bold })
        assertTrue(value.spanStyles.any { it.item.fontFamily==FontFamily.Monospace })
    }
    @Test fun htmlAndLinksCannotExecuteOrLoadImages() {
        val value=MessageMarkdown.parse("[docs](https://example.invalid/path) ![diagram](https://example.invalid/image)\n\n<script>alert(1)</script>")
        assertTrue(value.text.startsWith("docs [Image: diagram]"))
        assertTrue(value.text.contains("<script>alert(1)</script>"))
        assertEquals("https://example.invalid/path",value.getStringAnnotations("url",0,value.length).single().item)
    }
}
