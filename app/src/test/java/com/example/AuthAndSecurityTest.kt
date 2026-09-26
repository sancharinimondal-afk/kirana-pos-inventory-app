package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.KeystoreSecurityHelper
import com.example.security.SecureAuthManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthAndSecurityTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        SecureAuthManager.resetForTesting(context)
        KeystoreSecurityHelper.resetFallbackKeyForTesting()
    }

    @After
    fun tearDown() {
        SecureAuthManager.resetForTesting(context)
    }

    @Test
    fun testNoHardcodedDefaultProductionCredentials() {
        // App must not be configured out of the box with default admin/admin or 1234
        assertFalse("Unconfigured store must not be marked configured", SecureAuthManager.isConfigured(context))
        assertFalse("Unconfigured store must block login status", SecureAuthManager.isLoggedIn(context))
        assertEquals("", SecureAuthManager.getUsername(context))

        // Attempts to authenticate before setup must fail
        assertFalse("Default admin login must fail", SecureAuthManager.authenticate(context, "admin", "admin", false))
        assertFalse("Default 1234 PIN must fail", SecureAuthManager.authenticate(context, "owner", "1234", false))
    }

    @Test
    fun testSetupOwnerAndModernPasswordHashingWithKeystoreEncryption() {
        val ownerName = "Ramesh Gupta"
        val username = "ramesh9876"
        val password = "StrongPassword@2026"

        val setupSuccess = SecureAuthManager.setupOwner(
            context = context,
            ownerName = ownerName,
            username = username,
            pinOrPassword = password,
            rememberMe = false
        )
        assertTrue("Initial setup must succeed", setupSuccess)
        assertTrue("Store must be configured after setup", SecureAuthManager.isConfigured(context))
        assertEquals("ramesh9876", SecureAuthManager.getUsername(context))
        assertEquals("Ramesh Gupta", SecureAuthManager.getOwnerName(context))

        // Verify NO plaintext password stored in SharedPreferences
        val rawPrefs = context.getSharedPreferences("kirana_secure_auth_prefs", Context.MODE_PRIVATE)
        val allPrefs = rawPrefs.all
        for ((key, value) in allPrefs) {
            val strValue = value.toString()
            assertFalse(
                "Plaintext password must NEVER exist in storage (found in key $key)",
                strValue.contains(password)
            )
        }

        // Verify hash and salt are encrypted at rest with Keystore
        val encHash = rawPrefs.getString("auth_enc_password_hash", null)
        val encSalt = rawPrefs.getString("auth_enc_password_salt", null)
        assertNotNull("Encrypted hash must exist", encHash)
        assertNotNull("Encrypted salt must exist", encSalt)

        // Decrypt using KeystoreSecurityHelper and verify length matches 256-bit PBKDF2 hash (32 bytes)
        val decryptedHash = KeystoreSecurityHelper.decrypt(encHash!!)
        val decryptedSalt = KeystoreSecurityHelper.decrypt(encSalt!!)
        assertEquals("Hash must be 256 bits (32 bytes)", 32, decryptedHash.size)
        assertEquals("Salt must be 256 bits (32 bytes)", 32, decryptedSalt.size)
    }

    @Test
    fun testAuthenticationSuccessAndFailure() {
        SecureAuthManager.setupOwner(
            context = context,
            ownerName = "Priya Sharma",
            username = "priyastore",
            pinOrPassword = "SafePIN#5678",
            rememberMe = false
        )

        // Wrong password
        val wrongPassAuth = SecureAuthManager.authenticate(context, "priyastore", "WrongPass", false)
        assertFalse("Authentication with wrong password must fail", wrongPassAuth)

        // Wrong username
        val wrongUserAuth = SecureAuthManager.authenticate(context, "unknownUser", "SafePIN#5678", false)
        assertFalse("Authentication with wrong username must fail", wrongUserAuth)

        // Correct credentials
        val correctAuth = SecureAuthManager.authenticate(context, "priyastore", "SafePIN#5678", false)
        assertTrue("Authentication with correct credentials must succeed", correctAuth)
        assertTrue("User must be logged in after authentication", SecureAuthManager.isLoggedIn(context))
    }

    @Test
    fun testChangePasswordFlow() {
        SecureAuthManager.setupOwner(
            context = context,
            ownerName = "Sunil Verma",
            username = "sunilv",
            pinOrPassword = "OldPassword@1",
            rememberMe = true
        )

        // Attempt change with wrong old password
        val wrongOldChange = SecureAuthManager.changePassword(context, "IncorrectOld", "NewPassword@2")
        assertFalse("Change password must fail if old password is wrong", wrongOldChange)

        // Attempt change with correct old password
        val correctChange = SecureAuthManager.changePassword(context, "OldPassword@1", "NewPassword@2")
        assertTrue("Change password must succeed with correct old password", correctChange)

        // Verify old password no longer works
        assertFalse("Old password must no longer authenticate",
            SecureAuthManager.authenticate(context, "sunilv", "OldPassword@1", true))

        // Verify new password works
        assertTrue("New password must authenticate successfully",
            SecureAuthManager.authenticate(context, "sunilv", "NewPassword@2", true))
    }

    @Test
    fun testChangeUsernameFlow() {
        SecureAuthManager.setupOwner(
            context = context,
            ownerName = "Anil Roy",
            username = "anilold",
            pinOrPassword = "MySecurePassword#99",
            rememberMe = true
        )

        // Change username with wrong password
        val wrongPassResult = SecureAuthManager.changeUsername(context, "wrongPin", "anilnew")
        assertFalse("Change username must fail with wrong password", wrongPassResult)
        assertEquals("anilold", SecureAuthManager.getUsername(context))

        // Change username with correct password
        val correctResult = SecureAuthManager.changeUsername(context, "MySecurePassword#99", "anilnew")
        assertTrue("Change username must succeed with correct password", correctResult)
        assertEquals("anilnew", SecureAuthManager.getUsername(context))

        // Verify authentication with new username
        assertTrue("Must authenticate with new username",
            SecureAuthManager.authenticate(context, "anilnew", "MySecurePassword#99", true))
        assertFalse("Old username must no longer authenticate",
            SecureAuthManager.authenticate(context, "anilold", "MySecurePassword#99", true))
    }

    @Test
    fun testLogoutVerification() {
        SecureAuthManager.setupOwner(
            context = context,
            ownerName = "Deepak Sen",
            username = "deepaks",
            pinOrPassword = "SecretPIN@11",
            rememberMe = true
        )
        assertTrue("Must be logged in after setup", SecureAuthManager.isLoggedIn(context))

        // Logout
        SecureAuthManager.logout(context)
        assertFalse("Must be logged out immediately after logout", SecureAuthManager.isLoggedIn(context))
        assertTrue("Store must be locked after logout", SecureAuthManager.isLocked())

        // Re-authenticate restores logged in session
        val reauth = SecureAuthManager.authenticate(context, "deepaks", "SecretPIN@11", true)
        assertTrue(reauth)
        assertTrue("Must be logged in after re-authenticating", SecureAuthManager.isLoggedIn(context))
    }

    @Test
    fun testRememberMeAcrossAppRestartAndForceStop() {
        // CASE 1: Remember Me = FALSE
        SecureAuthManager.setupOwner(
            context = context,
            ownerName = "Kavita Roy",
            username = "kavitar",
            pinOrPassword = "PassPhrase#2026",
            rememberMe = false // DO NOT REMEMBER
        )
        assertTrue("Active in current process", SecureAuthManager.isLoggedIn(context))

        // Simulate App Restart or Force Stop (clearing in-memory process state)
        SecureAuthManager.simulateAppRestartOrForceStop()

        // When Remember Me is FALSE, restart/force-stop must require fresh login
        assertFalse(
            "After app restart / force stop, user must NOT be logged in when Remember Me is false",
            SecureAuthManager.isLoggedIn(context)
        )

        // CASE 2: Remember Me = TRUE
        SecureAuthManager.authenticate(context, "kavitar", "PassPhrase#2026", rememberMe = true)
        assertTrue("Logged in with remember me", SecureAuthManager.isLoggedIn(context))

        // Simulate App Restart or Force Stop
        SecureAuthManager.simulateAppRestartOrForceStop()

        // When Remember Me is TRUE, session persists across restarts
        assertTrue(
            "After app restart / force stop, user remains logged in when Remember Me is true",
            SecureAuthManager.isLoggedIn(context)
        )
    }

    @Test
    fun testAutoLockBehaviorAndUnlock() {
        SecureAuthManager.setupOwner(
            context = context,
            ownerName = "Vikram Patel",
            username = "vikramp",
            pinOrPassword = "VikramPIN@44",
            rememberMe = true
        )
        assertTrue(SecureAuthManager.isLoggedIn(context))

        // Set Auto Lock to Immediate (0 min)
        SecureAuthManager.setAutoLockTimer(context, 0)
        assertEquals(0, SecureAuthManager.getAutoLockTimer(context))

        // Manual lock
        SecureAuthManager.lock(context)
        assertTrue("Store must be marked locked", SecureAuthManager.isLocked())
        assertFalse("isLoggedIn must return false when store is locked", SecureAuthManager.isLoggedIn(context))

        // Attempt unlock with wrong PIN
        val wrongUnlock = SecureAuthManager.unlockWithPassword(context, "WrongPIN")
        assertFalse("Unlock with wrong PIN must fail", wrongUnlock)
        assertTrue("Store must remain locked", SecureAuthManager.isLocked())

        // Unlock with correct PIN
        val correctUnlock = SecureAuthManager.unlockWithPassword(context, "VikramPIN@44")
        assertTrue("Unlock with correct PIN must succeed", correctUnlock)
        assertFalse("Store must no longer be locked", SecureAuthManager.isLocked())
        assertTrue("isLoggedIn must return true after successful unlock", SecureAuthManager.isLoggedIn(context))
    }

    @Test
    fun testKeystoreSecurityHelperStandalone() {
        val testMessage = "Kirana Store Proprietary Secret Payload 2026"
        val encryptedBase64 = KeystoreSecurityHelper.encryptString(testMessage)
        assertNotEquals("Ciphertext must not equal plaintext", testMessage, encryptedBase64)

        val decrypted = KeystoreSecurityHelper.decryptString(encryptedBase64)
        assertEquals("Decrypted message must equal original plaintext", testMessage, decrypted)
    }
}
