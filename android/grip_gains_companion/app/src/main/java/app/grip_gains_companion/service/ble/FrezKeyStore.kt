package app.grip_gains_companion.service.ble

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-local encrypted credential; neither the key nor its ciphertext is backed up. */
class FrezKeyStore(context: Context) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"frez-key"))
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey("frez-developer-key",null) as? SecretKey)?.let {return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("frez-developer-key",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): String = if(!file.baseFile.exists()) "" else runCatching {
        val bytes=file.readFully()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
    }.getOrDefault("")
    fun save(value: String) {
        val normalized=value.trim()
        require(normalized.isEmpty() || (normalized.length<=4096 && normalized.all {it.code in 33..126}))
        if(normalized.isEmpty()) {file.delete();return}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val bytes=cipher.iv+cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val stream=file.startWrite()
        try {stream.write(bytes);file.finishWrite(stream)} catch(e: Exception) {file.failWrite(stream);throw e}
    }
}
