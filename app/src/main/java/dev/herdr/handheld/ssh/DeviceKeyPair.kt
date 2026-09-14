package dev.herdr.handheld.ssh

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.StringWriter
import java.security.SecureRandom
import java.util.Base64

/** Library-generated OpenSSH keys. Only the public line is displayed or copied. */
class DeviceKeyPair(val privateKey: ByteArray, val publicKey: String) {
    companion object {
        fun generate(): DeviceKeyPair {
            val key=Ed25519PrivateKeyParameters(SecureRandom())
            val blob=OpenSSHPrivateKeyUtil.encodePrivateKey(key)
            val writer=StringWriter()
            try {
                PemWriter(writer).use { it.writeObject(PemObject("OPENSSH PRIVATE KEY",blob)) }
                val publicBlob=OpenSSHPublicKeyUtil.encodePublicKey(key.generatePublicKey())
                return DeviceKeyPair(writer.toString().toByteArray(),
                    "ssh-ed25519 ${Base64.getEncoder().encodeToString(publicBlob)} pdx-device")
            } finally { blob.fill(0) }
        }
    }
}
