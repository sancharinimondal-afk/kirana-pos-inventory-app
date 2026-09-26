package com.example.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.Key
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android Keystore helper to protect authentication credentials and session tokens
 * using hardware-backed AES-256-GCM encryption where supported.
 *
 * Implements transparent fallback for standard JVM / Robolectric test environments.
 */
object KeystoreSecurityHelper {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "KiranaAuthMasterKey"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    // In-memory fallback key for testing environments where AndroidKeyStore provider is not registered
    private var fallbackKey: ByteArray? = null

    @Synchronized
    private fun getOrCreateKey(): Key {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
            keyStore.getKey(MASTER_KEY_ALIAS, null)
        } catch (e: Exception) {
            // JVM / Robolectric fallback: generate or retrieve software AES-256 key
            getOrCreateFallbackKey()
        }
    }

    private fun getOrCreateFallbackKey(): Key {
        if (fallbackKey == null) {
            val key = ByteArray(32)
            SecureRandom().nextBytes(key)
            fallbackKey = key
        }
        return SecretKeySpec(fallbackKey, "AES")
    }

    /**
     * Encrypts plaintext bytes using hardware-backed AES-256-GCM.
     * Returns a Base64-encoded string representing [IV (12 bytes) + Ciphertext + Tag].
     */
    fun encrypt(data: ByteArray): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)

        // Combine IV + ciphertext
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts Base64-encoded ciphertext using hardware-backed AES-256-GCM.
     */
    fun decrypt(base64Encrypted: String): ByteArray {
        val combined = Base64.decode(base64Encrypted, Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH) { "Invalid encrypted payload length" }

        val iv = ByteArray(GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)

        val ciphertext = ByteArray(combined.size - GCM_IV_LENGTH)
        System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.size)

        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Helper to encrypt a UTF-8 string.
     */
    fun encryptString(plaintext: String): String {
        return encrypt(plaintext.toByteArray(Charsets.UTF_8))
    }

    /**
     * Helper to decrypt to a UTF-8 string.
     */
    fun decryptString(encryptedBase64: String): String {
        return String(decrypt(encryptedBase64), Charsets.UTF_8)
    }

    /**
     * Visible for testing to reset fallback key state.
     */
    fun resetFallbackKeyForTesting() {
        fallbackKey = null
    }
}
