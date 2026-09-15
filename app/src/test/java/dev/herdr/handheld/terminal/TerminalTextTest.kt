package dev.herdr.handheld.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.herdr.handheld.herdr.ContractException
import org.junit.Assert.*
import org.junit.Test

class TerminalTextTest {
    @Test fun ansiPaletteTrueColorBackgroundAndResetSurviveNativeConversion() {
        val text=TerminalText.parse("\u001b[31mred\u001b[38;2;137;180;250;48;2;33;58;43m색상\u001b[0m plain")
        assertEquals("red색상 plain",text.text)
        assertEquals(Color(0xFFFF9498),text.spanStyles[0].item.color)
        assertEquals(Color(0xFF89B4FA),text.spanStyles[1].item.color)
        assertEquals(Color(0xFF213A2B),text.spanStyles[1].item.background)
        assertEquals(TerminalText.foreground,text.spanStyles.last().item.color)
    }
    @Test fun extendedPaletteAndTextStylesArePreserved() {
        val text=TerminalText.parse("\u001b[1;4;38;5;196m한글 👋 é\u001b[0m\n")
        assertEquals("한글 👋 é\n",text.text)
        val style=text.spanStyles.first().item
        assertEquals(Color.Red,style.color);assertEquals(FontWeight.Bold,style.fontWeight)
        assertEquals(TextDecoration.Underline,style.textDecoration)
    }
    @Test fun clipboardLinksAndControlStringsHaveNoNativeActionsOrVisiblePayload() {
        val text=TerminalText.parse("\u001b]52;c;private-clipboard\u0007before \u001b]8;;https://example.invalid\u001b\\label\u001b]8;;\u001b\\ after")
        assertEquals("before label after",text.text)
        assertTrue(text.getStringAnnotations(0,text.length).isEmpty())
    }
    @Test fun invalidAndOversizedSnapshotsAreRejectedWithoutDroppingBytes() {
        assertThrows(ContractException::class.java) { TerminalText.parse("x".repeat(1_048_577)) }
        assertThrows(ContractException::class.java) { TerminalText.parse("\u001b[99999999999999999999m") }
    }
    @Test fun browsingFreezesColorsAsWellAsText() {
        val paused=ReadingBuffer().receive("\u001b[31munchanged",1).follow(false).receive("\u001b[32munchanged",2)
        assertEquals(Color(0xFFFF9498),paused.formatted!!.spanStyles.first().item.color)
        assertEquals("unchanged",paused.pending)
        assertEquals(Color(0xFF88D4AE),paused.follow(true).formatted!!.spanStyles.first().item.color)
    }
}
