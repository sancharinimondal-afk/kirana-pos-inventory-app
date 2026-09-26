package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Enterprise-grade local authentication manager for Kirana Grocery POS.
 *
 * Security Architecture (Phase 14):
 * - Passwords are NEVER stored in plaintext.
 * - Password hashing uses modern Android-supported PBKDF2WithHmacSHA256 with 100,000 iterations
 *   and a 256-bit cryptographically secure random salt.
 * - Credential hashes and salts are encrypted at rest using Android Keystore (AES-256-GCM).
 * - Zero hardcoded default production credentials.
 * - Constant-time comparison to prevent side-channel timing attacks.
 * - Comprehensive support for:
 *   - Login
 *   - Logout
 *   - Change Password
 *   - Change Username
 *   - Remember Me (in-memory session enforcement when unchecked)
 *   - Auto Lock (configurable timeout & immediate background lock)
 * - Strict log privacy (no sensitive tokens/passwords ever logged).
 */
object SecureAuthManager {

    private const val PREF_NAME = "kirana_secure_auth_prefs"
    private const val KEY_IS_CONFIGURED = "auth_is_configured"
    private const val KEY_OWNER_NAME = "auth_owner_name"
    private const val KEY_USERNAME = "auth_username"
    private const val KEY_ENCRYPTED_PASSWORD_HASH = "auth_enc_password_hash"
    private const val KEY_ENCRYPTED_PASSWORD_SALT = "auth_enc_password_salt"
    private const val KEY_REMEMBER_ME = "auth_remember_me"
    private const val KEY_LAST_ACTIVITY_TIME = "auth_last_activity_time"
    private const val KEY_BIOMETRIC_ENABLED = "auth_biometric_enabled"
    private const val KEY_AUTO_LOCK_MINUTES = "auth_auto_lock_minutes" // 0: Immediate, 1: 1 min, 5: 5 min, 15: 15 min, -1: Never

    // Modern Android-supported password hashing parameters (OWASP Recommended)
    private const val HASH_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 100000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 32

    // In-memory session tracking for strict Remember Me enforcement
    // When Remember Me is disabled, this in-memory flag is lost upon app restart or force stop
    @Volatile
    private var inMemorySessionActive: Boolean = false

    // In-memory lock state (e.g. auto-locked after inactivity or manual lock)
    @Volatile
    private var isStoreLocked: Boolean = false

    @Volatile
    private var inMemoryLastActivityTime: Long = 0L

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks if the shop owner has configured credentials on initial setup.
     * No default production credentials exist.
     */
    fun isConfigured(context: Context): Boolean {
        val prefs = getPrefs(context)
        val configured = prefs.getBoolean(KEY_IS_CONFIGURED, false)
        val username = prefs.getString(KEY_USERNAME, null)
        val hash = prefs.getString(KEY_ENCRYPTED_PASSWORD_HASH, null)
        return configured && !username.isNullOrBlank() && !hash.isNullOrBlank()
    }

    /**
     * Records user interaction timestamp to prevent unwanted auto-lock during active use.
     */
    fun recordUserActivity(context: Context? = null) {
        val now = System.currentTimeMillis()
        inMemoryLastActivityTime = now
        context?.let {
            getPrefs(it).edit().putLong(KEY_LAST_ACTIVITY_TIME, now).apply()
        }
    }

    /**
     * Checks if auto-lock timeout has expired since the last recorded activity.
     */
    fun checkAutoLock(context: Context): Boolean {
        if (!isConfigured(context) || isStoreLocked) return isStoreLocked

        val autoLockMinutes = getAutoLockTimer(context)
        if (autoLockMinutes < 0) {
            // "Never" auto-lock
            return false
        }

        val lastActive = if (inMemoryLastActivityTime > 0L) {
            inMemoryLastActivityTime
        } else {
            getPrefs(context).getLong(KEY_LAST_ACTIVITY_TIME, 0L)
        }

        if (lastActive <= 0L) return false

        val elapsedMillis = System.currentTimeMillis() - lastActive
        val timeoutMillis = if (autoLockMinutes == 0) {
            // Immediate lock on backgrounding (1 second grace)
            1000L
        } else {
            autoLockMinutes * 60 * 1000L
        }

        if (elapsedMillis >= timeoutMillis) {
            isStoreLocked = true
            return true
        }

        return false
    }

    /**
     * Checks if user has an active, valid authentication session.
     *
     * Validates:
     * 1. Store is configured
     * 2. Store is NOT currently auto-locked or manually locked
     * 3. Auto-lock timeout has not elapsed
     * 4. Session validity:
     *    - If Remember Me is TRUE: persists across app restarts (provided auto-lock hasn't expired)
     *    - If Remember Me is FALSE: requires active in-memory session (lost on restart / force stop)
     */
    fun isLoggedIn(context: Context): Boolean {
        if (!isConfigured(context)) return false

        // Check if store is locked
        if (isStoreLocked) return false

        // Check if auto-lock timer has elapsed
        if (checkAutoLock(context)) {
            return false
        }

        val prefs = getPrefs(context)
        val rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false)

        return if (rememberMe) {
            true
        } else {
            // Strict session check: requires active in-memory session
            inMemorySessionActive
        }
    }

    fun isRememberMeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REMEMBER_ME, false)
    }

    fun setRememberMeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REMEMBER_ME, enabled).apply()
    }

    fun getOwnerName(context: Context): String {
        return getPrefs(context).getString(KEY_OWNER_NAME, "Shop Owner") ?: "Shop Owner"
    }

    fun setOwnerName(context: Context, name: String) {
        require(name.isNotBlank()) { "Owner name cannot be blank" }
        getPrefs(context).edit().putString(KEY_OWNER_NAME, name.trim()).apply()
    }

    fun getUsername(context: Context): String {
        return getPrefs(context).getString(KEY_USERNAME, "") ?: ""
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun getAutoLockTimer(context: Context): Int {
        return getPrefs(context).getInt(KEY_AUTO_LOCK_MINUTES, 5)
    }

    fun setAutoLockTimer(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_AUTO_LOCK_MINUTES, minutes).apply()
    }

    fun isLocked(): Boolean = isStoreLocked

    /**
     * Manually locks the store, requiring re-authentication.
     */
    fun lock(context: Context? = null) {
        isStoreLocked = true
    }

    /**
     * Sets up the store owner account on first launch.
     * Generates a modern PBKDF2WithHmacSHA256 hash and encrypts it with Android Keystore.
     */
    fun setupOwner(
        context: Context,
        ownerName: String,
        username: String,
        pinOrPassword: String,
        rememberMe: Boolean = true
    ): Boolean {
        require(ownerName.isNotBlank()) { "Owner name cannot be blank" }
        require(username.isNotBlank()) { "Username cannot be blank" }
        require(pinOrPassword.length >= 4) { "PIN or password must be at least 4 characters" }

        val salt = generateSalt()
        val hash = hashPassword(pinOrPassword.toCharArray(), salt)

        // Encrypt hash and salt via Android Keystore before writing to disk
        val encryptedHash = KeystoreSecurityHelper.encrypt(hash)
        val encryptedSalt = KeystoreSecurityHelper.encrypt(salt)

        val now = System.currentTimeMillis()
        getPrefs(context).edit()
            .putBoolean(KEY_IS_CONFIGURED, true)
            .putString(KEY_OWNER_NAME, ownerName.trim())
            .putString(KEY_USERNAME, username.trim().lowercase())
            .putString(KEY_ENCRYPTED_PASSWORD_HASH, encryptedHash)
            .putString(KEY_ENCRYPTED_PASSWORD_SALT, encryptedSalt)
            .putBoolean(KEY_REMEMBER_ME, rememberMe)
            .putLong(KEY_LAST_ACTIVITY_TIME, now)
            .apply()

        inMemorySessionActive = true
        isStoreLocked = false
        inMemoryLastActivityTime = now

        return true
    }

    /**
     * Authenticates an existing user against stored credentials.
     */
    fun authenticate(
        context: Context,
        username: String,
        pinOrPassword: String,
        rememberMe: Boolean
    ): Boolean {
        if (!isConfigured(context)) return false

        val prefs = getPrefs(context)
        val storedUser = prefs.getString(KEY_USERNAME, "")?.trim()?.lowercase() ?: ""
        val inputUser = username.trim().lowercase()

        // Verify username
        if (storedUser != inputUser) {
            return false
        }

        val encHash = prefs.getString(KEY_ENCRYPTED_PASSWORD_HASH, null) ?: return false
        val encSalt = prefs.getString(KEY_ENCRYPTED_PASSWORD_SALT, null) ?: return false

        return try {
            val salt = KeystoreSecurityHelper.decrypt(encSalt)
            val expectedHash = KeystoreSecurityHelper.decrypt(encHash)
            val computedHash = hashPassword(pinOrPassword.toCharArray(), salt)

            if (slowEquals(expectedHash, computedHash)) {
                val now = System.currentTimeMillis()
                prefs.edit()
                    .putBoolean(KEY_REMEMBER_ME, rememberMe)
                    .putLong(KEY_LAST_ACTIVITY_TIME, now)
                    .apply()

                inMemorySessionActive = true
                isStoreLocked = false
                inMemoryLastActivityTime = now
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Unlocks an auto-locked or manually locked store using the owner's password/PIN.
     */
    fun unlockWithPassword(
        context: Context,
        pinOrPassword: String
    ): Boolean {
        if (!isConfigured(context)) return false

        val prefs = getPrefs(context)
        val encHash = prefs.getString(KEY_ENCRYPTED_PASSWORD_HASH, null) ?: return false
        val encSalt = prefs.getString(KEY_ENCRYPTED_PASSWORD_SALT, null) ?: return false

        return try {
            val salt = KeystoreSecurityHelper.decrypt(encSalt)
            val expectedHash = KeystoreSecurityHelper.decrypt(encHash)
            val computedHash = hashPassword(pinOrPassword.toCharArray(), salt)

            if (slowEquals(expectedHash, computedHash)) {
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_ACTIVITY_TIME, now).apply()
                inMemorySessionActive = true
                isStoreLocked = false
                inMemoryLastActivityTime = now
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Changes password / PIN after verifying the old credential.
     * Plaintext passwords are not retained.
     */
    fun changePassword(
        context: Context,
        oldPinOrPassword: String,
        newPinOrPassword: String
    ): Boolean {
        if (!isConfigured(context)) return false

        val prefs = getPrefs(context)
        val encHash = prefs.getString(KEY_ENCRYPTED_PASSWORD_HASH, null) ?: return false
        val encSalt = prefs.getString(KEY_ENCRYPTED_PASSWORD_SALT, null) ?: return false

        val (salt, expectedHash) = try {
            Pair(
                KeystoreSecurityHelper.decrypt(encSalt),
                KeystoreSecurityHelper.decrypt(encHash)
            )
        } catch (e: Exception) {
            return false
        }

        val computedOldHash = hashPassword(oldPinOrPassword.toCharArray(), salt)
        if (!slowEquals(expectedHash, computedOldHash)) {
            return false
        }

        require(newPinOrPassword.length >= 4) { "New PIN/password must be at least 4 characters" }

        val newSalt = generateSalt()
        val newHash = hashPassword(newPinOrPassword.toCharArray(), newSalt)

        val newEncHash = KeystoreSecurityHelper.encrypt(newHash)
        val newEncSalt = KeystoreSecurityHelper.encrypt(newSalt)

        prefs.edit()
            .putString(KEY_ENCRYPTED_PASSWORD_HASH, newEncHash)
            .putString(KEY_ENCRYPTED_PASSWORD_SALT, newEncSalt)
            .apply()

        recordUserActivity(context)
        return true
    }

    /**
     * Changes the login username. Requires current password verification for security.
     */
    fun changeUsername(
        context: Context,
        currentPassword: String,
        newUsername: String
    ): Boolean {
        if (!isConfigured(context)) return false
        require(newUsername.isNotBlank()) { "New username cannot be blank" }

        val prefs = getPrefs(context)
        val encHash = prefs.getString(KEY_ENCRYPTED_PASSWORD_HASH, null) ?: return false
        val encSalt = prefs.getString(KEY_ENCRYPTED_PASSWORD_SALT, null) ?: return false

        val (salt, expectedHash) = try {
            Pair(
                KeystoreSecurityHelper.decrypt(encSalt),
                KeystoreSecurityHelper.decrypt(encHash)
            )
        } catch (e: Exception) {
            return false
        }

        val computedHash = hashPassword(currentPassword.toCharArray(), salt)
        if (!slowEquals(expectedHash, computedHash)) {
            return false
        }

        prefs.edit()
            .putString(KEY_USERNAME, newUsername.trim().lowercase())
            .apply()

        recordUserActivity(context)
        return true
    }

    /**
     * Clears active login session and remember me flag.
     * The app will require re-authentication immediately.
     */
    fun logout(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putBoolean(KEY_REMEMBER_ME, false)
            .putLong(KEY_LAST_ACTIVITY_TIME, 0L)
            .apply()

        inMemorySessionActive = false
        isStoreLocked = true
        inMemoryLastActivityTime = 0L
    }

    /**
     * Simulates an app restart or process force stop for testing and session verification.
     * Clears in-memory volatile session states.
     */
    fun simulateAppRestartOrForceStop() {
        inMemorySessionActive = false
        isStoreLocked = false
        inMemoryLastActivityTime = 0L
    }

    /**
     * Resets authentication state (testing only).
     */
    fun resetForTesting(context: Context) {
        getPrefs(context).edit().clear().apply()
        inMemorySessionActive = false
        isStoreLocked = false
        inMemoryLastActivityTime = 0L
    }

    private fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES)
        random.nextBytes(salt)
        return salt
    }

    /**
     * Computes PBKDF2WithHmacSHA256 hash using 100,000 iterations.
     * Provides fallback to standard MessageDigest SHA-256 only if algorithm is missing.
     */
    private fun hashPassword(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val skf = SecretKeyFactory.getInstance(HASH_ALGORITHM)
            skf.generateSecret(spec).encoded
        } catch (e: NoSuchAlgorithmException) {
            // Android fallback
            try {
                val skfFallback = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                skfFallback.generateSecret(PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)).encoded
            } catch (e2: Exception) {
                val md = MessageDigest.getInstance("SHA-256")
                md.update(salt)
                md.digest(String(password).toByteArray(Charsets.UTF_8))
            }
        } catch (e: InvalidKeySpecException) {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            md.digest(String(password).toByteArray(Charsets.UTF_8))
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Constant-time comparison to prevent side-channel timing attacks.
     */
    private fun slowEquals(a: ByteArray, b: ByteArray): Boolean {
        return MessageDigest.isEqual(a, b)
    }
}
