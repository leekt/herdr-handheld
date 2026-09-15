package dev.herdr.handheld.terminal

import androidx.compose.ui.text.AnnotatedString

/** Keep visible text stable while browsing; hold at most one newer snapshot. */
data class ReadingBuffer(
    val output: String?=null,
    val updatedAt: Long=0,
    val following: Boolean=true,
    val pending: String?=null,
    val pendingAt: Long=0,
    val formatted: AnnotatedString?=null,
    val pendingFormatted: AnnotatedString?=null,
) {
    fun receive(text: String,at: Long)=receive(TerminalText.parse(text),at)
    fun receive(text: AnnotatedString,at: Long): ReadingBuffer = when {
        following || output==null -> copy(output=text.text,formatted=text,updatedAt=at,pending=null,pendingFormatted=null,pendingAt=0)
        text==formatted -> copy(updatedAt=at,pending=null,pendingFormatted=null,pendingAt=0)
        else -> copy(pending=text.text,pendingFormatted=text,pendingAt=at)
    }
    fun follow(value: Boolean): ReadingBuffer =
        if(value && pending!=null) copy(output=pending,formatted=pendingFormatted,updatedAt=pendingAt,following=true,pending=null,pendingFormatted=null,pendingAt=0)
        else copy(following=value)
}
