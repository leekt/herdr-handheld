package dev.herdr.handheld.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.em
import org.commonmark.node.*
import org.commonmark.parser.Parser

/** Native text only: no HTML renderer, remote images, script, or automatic navigation. */
internal object MessageMarkdown {
    fun parse(text: String): AnnotatedString {
        val out=AnnotatedString.Builder()
        val code=SpanStyle(fontFamily=FontFamily.Monospace,background=Color(0xFF1A2029),color=Color(0xFFB6D4FF),fontSize=.92.em)
        val visitor=object: AbstractVisitor() {
            fun styled(node: Node,style: SpanStyle) { out.pushStyle(style);visitChildren(node);out.pop() }
            override fun visit(node: Text) { out.append(node.literal) }
            override fun visit(node: SoftLineBreak) { out.append(" ") }
            override fun visit(node: HardLineBreak) { out.append("\n") }
            override fun visit(node: Paragraph) { visitChildren(node);out.append(if(node.parent is ListItem)"\n"else "\n\n") }
            override fun visit(node: Heading) { styled(node,SpanStyle(fontWeight=FontWeight.Bold,fontSize=1.12.em));out.append("\n\n") }
            override fun visit(node: StrongEmphasis) { styled(node,SpanStyle(fontWeight=FontWeight.Bold)) }
            override fun visit(node: Emphasis) { styled(node,SpanStyle(fontStyle=FontStyle.Italic)) }
            override fun visit(node: Code) { out.pushStyle(code);out.append(node.literal);out.pop() }
            override fun visit(node: FencedCodeBlock) { out.pushStyle(code);out.append(node.literal.trimEnd());out.pop();out.append("\n\n") }
            override fun visit(node: IndentedCodeBlock) { out.pushStyle(code);out.append(node.literal.trimEnd());out.pop();out.append("\n\n") }
            override fun visit(node: ListItem) {
                val parent=node.parent
                if(parent is OrderedList) {
                    var index=parent.startNumber;var previous=node.previous
                    while(previous!=null) { index++;previous=previous.previous }
                    out.append("$index. ")
                } else out.append("• ")
                visitChildren(node)
            }
            override fun visit(node: BlockQuote) { styled(node,SpanStyle(color=Color(0xFFADB7C7),fontStyle=FontStyle.Italic)) }
            override fun visit(node: Link) {
                // Labels remain readable on a square screen. URLs are data, never instructions.
                out.pushStringAnnotation("url",node.destination);visitChildren(node);out.pop()
            }
            override fun visit(node: Image) { out.append("[Image: ");visitChildren(node);out.append("]") }
            override fun visit(node: HtmlInline) { out.append(node.literal) }
            override fun visit(node: HtmlBlock) { out.append(node.literal);out.append("\n") }
            override fun visit(node: ThematicBreak) { out.append("────────\n\n") }
        }
        Parser.builder().build().parse(text).accept(visitor)
        val value=out.toAnnotatedString()
        return value.subSequence(0,value.text.trimEnd().length)
    }
}
