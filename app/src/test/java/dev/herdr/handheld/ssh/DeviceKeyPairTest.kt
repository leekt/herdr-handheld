package dev.herdr.handheld.ssh

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class DeviceKeyPairTest {
    @Test fun generatedOpenSshKeyLoadsInSshjWithTheDisplayedPublicKey() {
        val pair=DeviceKeyPair.generate()
        try {
            SSHClient().use { ssh ->
                val loaded=ssh.loadKeys(pair.privateKey.toString(Charsets.UTF_8),null,null)
                assertNotNull(loaded.private)
                val wire=Buffer.PlainBuffer().putPublicKey(loaded.public).compactData
                assertEquals(pair.publicKey.split(' ')[1],Base64.getEncoder().encodeToString(wire))
            }
        } finally { pair.privateKey.fill(0) }
    }
}
