package dev.herdr.handheld.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.herdr.handheld.herdr.ContractException
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStyle
import org.jline.utils.Colors

/** Converts Herdr's complete styled snapshots, not live terminal escape streams.
 * JLine owns ANSI parsing. Compose receives text and spans, never executable content.
 */
object TerminalText {
    val foreground=Color(0xFFE4E9F0)
    private val background=Color.Black
    // Same base palette as the bundled live xterm. True-color values pass through unchanged.
    private val palette=intArrayOf(0x000000,0xFF9498,0x88D4AE,0xF2CC80,0x91BFFF,0xC9A9EF,0x89D8DF,0xE4E9F0,
        0x8E9BAB,0xFF0000,0x00FF00,0xFFFF00,0x0000FF,0xFF00FF,0x00FFFF,0xFFFFFF)
    private val default=AttributedStyle.DEFAULT
    private val fgIndex=default.foreground(0).style
    private val fgRgb=default.foregroundRgb(0).style
    private val bgIndex=default.background(0).style
    private val bgRgb=default.backgroundRgb(0).style
    // Derive masks through the pinned library's public style API, without reflection.
    private val fgMask=default.foregroundRgb(0xFFFFFF).style xor fgRgb
    private val bgMask=default.backgroundRgb(0xFFFFFF).style xor bgRgb

    fun parse(ansi: String): AnnotatedString {
        if(ansi.length>1_048_576)throw ContractException("Styled output exceeded limit")
        val parsed=try { AttributedString.fromAnsi(ansi) }
            catch(_: IllegalArgumentException) { throw ContractException("Invalid styled output") }
        val builder=AnnotatedString.Builder(parsed.toString())
        var start=0;var runs=0
        while(start<parsed.length) {
            if(++runs>16384)throw ContractException("Styled output has too many spans")
            val end=parsed.runLimit(start)
            val bits=parsed.styleAt(start).style
            fun has(flag: AttributedStyle)=bits and flag.style!=0L
            fun color(rgb: Long,index: Long,mask: Long,fallback: Color): Color {
                val value=((bits and mask) ushr java.lang.Long.numberOfTrailingZeros(mask)).toInt()
                return when {
                    bits and rgb!=0L -> Color(0xFF000000.toInt() or value)
                    bits and index!=0L -> Color(0xFF000000.toInt() or if(value<16)palette[value]else Colors.rgbColor(value.coerceIn(0,255)))
                    else -> fallback
                }
            }
            var fg=color(fgRgb,fgIndex,fgMask,foreground)
            var bg=color(bgRgb,bgIndex,bgMask,background)
            if(has(default.inverse())) { val old=fg;fg=bg;bg=old }
            if(has(default.faint()))fg=lerp(bg,fg,.65f)
            if(has(default.conceal()) || has(default.hidden()))fg=bg
            val decorations=buildList {
                if(has(default.underline()))add(TextDecoration.Underline)
                if(has(default.crossedOut()))add(TextDecoration.LineThrough)
            }
            builder.addStyle(SpanStyle(color=fg,background=bg,
                fontWeight=if(has(default.bold()))FontWeight.Bold else FontWeight.Normal,
                fontStyle=if(has(default.italic()))FontStyle.Italic else FontStyle.Normal,
                textDecoration=TextDecoration.combine(decorations)),start,end)
            start=end
        }
        return builder.toAnnotatedString()
    }
}
