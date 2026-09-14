package dev.herdr.handheld.ssh

import dev.herdr.handheld.herdr.HostProfile
import dev.herdr.handheld.storage.SecretStore
import kotlinx.coroutines.*
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.InputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider
import net.schmizz.sshj.common.SecurityUtils
import java.util.Base64
import java.util.concurrent.TimeUnit

class SshjTransport(private val secrets: SecretStore, private val testFailure: ((Throwable)->Unit)? = null) : SshTransport {
    @Volatile private var client: SSHClient? = null
    override suspend fun connect(profile: HostProfile) = withContext(Dispatchers.IO) {
        disconnect()
        require(profile.host.isNotBlank() && profile.port in 1..65535 && profile.username.isNotBlank())
        SshCrypto.prepare()
        val ssh = SSHClient()
        client = ssh
        ssh.connectTimeout = 8000
        ssh.timeout = 12000
        var challenge: HostKeyChallenge? = null
        val trustName = "hostkey:${profile.host.lowercase()}:${profile.port}"
        val trusted = secrets.get(trustName)?.toString(Charsets.UTF_8)
        ssh.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(host: String, port: Int, key: PublicKey): Boolean {
                val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
                val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))
                if (trusted == fingerprint) return true
                challenge = HostKeyChallenge(profile.host, profile.port, fingerprint, trusted != null)
                return false
            }
            override fun findExistingAlgorithms(host: String, port: Int): List<String> = emptyList()
        })
        try {
            ssh.connect(profile.host,profile.port)
            val rawKey = secrets.get("ssh:${profile.id}") ?: throw SshFailure("Authentication", "Import an app-specific SSH private key in Settings.")
            val passphrase = secrets.get("passphrase:${profile.id}")?.toString(Charsets.UTF_8)?.toCharArray()
            try {
                val provider = ssh.loadKeys(rawKey.toString(Charsets.UTF_8), null, passphrase?.let { PasswordUtils.createOneOff(it) })
                ssh.authPublickey(profile.username,provider)
            } finally { rawKey.fill(0); passphrase?.fill('\u0000') }
            ssh.connection.keepAlive.keepAliveInterval = 5
        } catch (failure: Exception) {
            testFailure?.invoke(failure)
            runCatching { ssh.disconnect() }; client = null
            if (failure is CancellationException) throw failure
            if (challenge != null) throw SshFailure("Host verification", if (challenge!!.changed) "Host key changed. Verify the server before replacing trust." else "Compare this fingerprint with the server before trusting it.", challenge)
            if (failure is SshFailure) throw failure
            val auth = failure.javaClass.name.contains("UserAuth") || failure.javaClass.name.contains("Key")
            throw SshFailure(if (auth) "Authentication" else "SSH connection", (if (auth) "Check username, key format, and passphrase." else "Check address, SSH service, and network; then reconnect.") + " [${failure.javaClass.simpleName}]")
        }
    }

    override suspend fun exec(command: String): ExecResult = withContext(Dispatchers.IO) {
        val ssh = client ?: throw SshFailure("SSH connection", "Reconnect to the host.")
        ssh.startSession().use { session ->
            val process = session.exec(command)
            coroutineScope {
                val out = async(Dispatchers.IO) { readBounded(process.inputStream,4 * 1024 * 1024) }
                val err = async(Dispatchers.IO) { readBounded(process.errorStream,32 * 1024) }
                try {
                    withTimeout(12000) {
                        process.join(10,TimeUnit.SECONDS)
                        if (process.isOpen) throw SshFailure("Herdr command", "Command timed out. Check the Herdr installation and session.")
                        ExecResult(out.await(),err.await(),process.exitStatus ?: -1)
                    }
                } finally { process.close(); session.close() }
            }
        }
    }
    override suspend fun open(command: String): SshChannel = withContext(Dispatchers.IO) {
        val session = (client ?: throw SshFailure("SSH connection", "Reconnect to the host.")).startSession()
        try { LiveChannel(session,session.exec(command)) } catch (e: Exception) { session.close(); throw e }
    }
    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        val previous = client; client = null
        runCatching { previous?.disconnect() }; runCatching { previous?.close() }; Unit
    }
    private class LiveChannel(private val session: Session, private val command: Session.Command) : SshChannel {
        override val stdout get() = command.inputStream
        override val stderr get() = command.errorStream
        override val stdin get() = command.outputStream
        override suspend fun close() = withContext(Dispatchers.IO) { runCatching { command.close() }; runCatching { session.close() }; Unit }
    }
    private fun readBounded(stream: InputStream, limit: Int): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer); if (count < 0) break
            if (out.size() + count > limit) throw SshFailure("Herdr output", "Response exceeded the supported size.")
            out.write(buffer,0,count)
        }
        return out.toString("UTF-8")
    }
}

private object SshCrypto {
    private var prepared=false
    @Synchronized fun prepare() {
        if(prepared)return
        // Android ships a reduced provider named BC. SSHJ selects providers by name,
        // so register the unmodified bundled provider in THIS APP PROCESS only.
        // AndroidKeyStore and the platform's other providers retain their positions.
        if(Security.getProvider("BC") !is BouncyCastleProvider) {
            Security.removeProvider("BC")
            Security.addProvider(BouncyCastleProvider())
        }
        SecurityUtils.setSecurityProvider("BC")
        prepared=true
    }
}
