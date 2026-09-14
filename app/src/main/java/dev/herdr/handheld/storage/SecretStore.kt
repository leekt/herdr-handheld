package dev.herdr.handheld.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Imported SSH keys and unsent drafts are encrypted blobs, not Keystore SSH signers. */
class SecretStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, "secrets").apply { mkdirs() }
    private val alias = "herdr.handheld.storage.v1"
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun file(name: String) = File(directory, MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).joinToString("") { "%02x".format(it) })
    @Synchronized fun put(name: String, value: ByteArray) {
        require(value.size <= 128 * 1024)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(name.toByteArray()) }
        val encrypted = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(value)
        val target = file(name); val temporary = File(directory, target.name + ".tmp")
        temporary.outputStream().use { it.write(encrypted); it.fd.sync() }
        check(temporary.renameTo(target))
    }
    @Synchronized fun get(name: String): ByteArray? {
        val path = file(name); if (!path.exists()) return null
        val raw = path.readBytes(); require(raw.size in 29..131200 && raw[0].toInt() == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(1,13))); updateAAD(name.toByteArray())
        }
        return cipher.doFinal(raw.copyOfRange(13,raw.size))
    }
    fun exists(name: String) = file(name).exists()
    fun remove(name: String) { file(name).delete() }
}
