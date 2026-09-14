package dev.herdr.handheld.terminal

/** Keep visible text stable while browsing; hold at most one newer snapshot. */
data class ReadingBuffer(
    val output: String?=null,
    val updatedAt: Long=0,
    val following: Boolean=true,
    val pending: String?=null,
    val pendingAt: Long=0,
) {
    fun receive(text: String,at: Long): ReadingBuffer = when {
        following || output==null -> copy(output=text,updatedAt=at,pending=null,pendingAt=0)
        text==output -> copy(updatedAt=at,pending=null,pendingAt=0)
        else -> copy(pending=text,pendingAt=at)
    }
    fun follow(value: Boolean): ReadingBuffer =
        if(value && pending!=null) copy(output=pending,updatedAt=pendingAt,following=true,pending=null,pendingAt=0)
        else copy(following=value)
}
