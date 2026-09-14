package dev.herdr.handheld.terminal

import dev.herdr.handheld.herdr.TerminalFrame

interface TerminalRenderer {
    suspend fun reset(generation: Long, fontSize: Int)
    suspend fun render(frame: TerminalFrame, generation: Long)
    fun scroll(lines: Int, generation: Long)
    fun font(size: Int, generation: Long)
}
