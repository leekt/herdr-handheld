package dev.herdr.handheld.herdr

/** SSH exec uses a shell: quote every argument, including the configured executable. */
class HerdrCommandBuilder(private val profile: HostProfile) {
    init {
        require(profile.binary.isNotBlank() && profile.binary.length <= 512)
        require(profile.session.isNotBlank() && profile.session.length <= 128)
    }
    fun command(vararg arguments: String): String =
        (listOf(profile.binary, "--session", profile.session) + arguments).joinToString(" ") { quote(it) }
    fun agents() = command("agent", "list")
    fun agent(ref: TargetRef): String { checkScope(ref); return command("agent", "get", ref.paneId) }
    fun observe(ref: TargetRef, cols: Int, rows: Int) = terminal(ref, false, cols, rows)
    fun control(ref: TargetRef, cols: Int, rows: Int) = terminal(ref, true, cols, rows)
    fun recent(ref: TargetRef): String { checkScope(ref); return command("pane", "read", ref.paneId, "--source", "recent-unwrapped", "--lines", "160", "--format", "text") }
    private fun terminal(ref: TargetRef, writable: Boolean, cols: Int, rows: Int): String {
        checkScope(ref)
        require(cols in 2..500 && rows in 2..300)
        return command("terminal", "session", if (writable) "control" else "observe", ref.terminalId,
            "--cols", cols.toString(), "--rows", rows.toString())
    }
    private fun checkScope(ref: TargetRef) { require(ref.profileId == profile.id && ref.session == profile.session) }
    companion object {
        fun quote(value: String): String {
            require(!value.contains('\u0000') && !value.contains('\n') && !value.contains('\r'))
            return "'" + value.replace("'", "'\"'\"'") + "'"
        }
    }
}
