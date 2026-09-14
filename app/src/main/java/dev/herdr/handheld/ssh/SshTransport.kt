package dev.herdr.handheld.ssh

import dev.herdr.handheld.herdr.HostProfile
import java.io.InputStream
import java.io.OutputStream

data class ExecResult(val stdout: String, val stderr: String, val exitCode: Int)
data class HostKeyChallenge(val host: String, val port: Int, val fingerprint: String, val changed: Boolean)
class SshFailure(val stage: String, val recovery: String, val challenge: HostKeyChallenge? = null) : Exception("$stage: $recovery")

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
