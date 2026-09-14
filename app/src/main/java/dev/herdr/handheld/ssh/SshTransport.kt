package dev.herdr.handheld.ssh

import dev.herdr.handheld.herdr.HostProfile
import java.io.InputStream
import java.io.OutputStream

data class ExecResult(val stdout: String, val stderr: String, val exitCode: Int)
data class HostKeyChallenge(val host: String, val port: Int, val fingerprint: String, val changed: Boolean)
enum class SshStage(val label: String) { CONNECTION("SSH connection"), AUTHENTICATION("Authentication"), HOST_KEY("Host verification"), COMMAND("Remote command"), OUTPUT("Remote output") }
class SshFailure(val stage: SshStage, val recovery: String, val challenge: HostKeyChallenge? = null) : Exception("${stage.label}: $recovery")

interface SshChannel {
    val stdout: InputStream
    val stderr: InputStream
    val stdin: OutputStream
    suspend fun close()
}
interface SshTransport {
    suspend fun connect(profile: HostProfile)
    suspend fun exec(command: String): ExecResult
    suspend fun open(command: String): SshChannel
    suspend fun disconnect()
}
