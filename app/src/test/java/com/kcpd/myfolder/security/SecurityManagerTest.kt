package com.kcpd.myfolder.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for SecurityManager.
 *
 * Tests AES-256-GCM encryption/decryption and key management.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28]) // Android 9.0 - minimum for biometrics
class SecurityManagerTest {

    private lateinit var securityManager: SecurityManager
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        securityManager = SecurityManager(context)
    }

    @Test
    fun `encrypt and decrypt data successfully`() {
        // Given
        val originalData = "This is sensitive vault data!".toByteArray()

        // When
        val encrypted = securityManager.encrypt(originalData)
        val decrypted = securityManager.decrypt(encrypted)

        // Then
        assertArrayEquals("Decrypted data should match original", originalData, decrypted)
    }

    @Test
    fun `encrypted data should be different from original`() {
        // Given
        val originalData = "Test data".toByteArray()

        // When
        val encrypted = securityManager.encrypt(originalData)

        // Then
        assertFalse("Encrypted data should differ from original",
            encrypted.contentEquals(originalData))
    }

    @Test
    fun `encryption produces different ciphertext each time (unique IVs)`() {
        // Given
        val originalData = "Test data".toByteArray()

        // When
        val encrypted1 = securityManager.encrypt(originalData)
        val encrypted2 = securityManager.encrypt(originalData)

        // Then
        assertFalse("Each encryption should produce different ciphertext due to unique IVs",
            encrypted1.contentEquals(encrypted2))

        // But both should decrypt to the same plaintext
        val decrypted1 = securityManager.decrypt(encrypted1)
        val decrypted2 = securityManager.decrypt(encrypted2)
        assertArrayEquals(decrypted1, decrypted2)
    }

    @Test
    fun `encrypted data includes 12-byte IV`() {
        // Given
        val originalData = "Test".toByteArray()

        // When
        val encrypted = securityManager.encrypt(originalData)

        // Then
        // Encrypted format: IV (12 bytes) + ciphertext + auth tag (16 bytes)
        // So encrypted should be at least 12 + data.size + 16 bytes
        assertTrue("Encrypted data should include IV",
            encrypted.size >= 12 + originalData.size)
    }

    @Test
    fun `encrypt and decrypt empty data`() {
        // Given
        val emptyData = ByteArray(0)

        // When
        val encrypted = securityManager.encrypt(emptyData)
        val decrypted = securityManager.decrypt(encrypted)

        // Then
        assertArrayEquals("Empty data should encrypt and decrypt correctly",
            emptyData, decrypted)
    }

    @Test
    fun `encrypt and decrypt large data`() {
        // Given - 10MB of data
        val largeData = ByteArray(10 * 1024 * 1024) { it.toByte() }

        // When
        val encrypted = securityManager.encrypt(largeData)
        val decrypted = securityManager.decrypt(encrypted)

        // Then
        assertArrayEquals("Large data should encrypt and decrypt correctly",
            largeData, decrypted)
    }

    @Test
    fun `encryptString and decryptString work correctly`() {
        // Given
        val originalString = "Sensitive password: MySecretPass123!"

        // When
        val encrypted = securityManager.encryptString(originalString)
        val decrypted = securityManager.decryptString(encrypted)

        // Then
        assertEquals("String encryption should preserve data", originalString, decrypted)
    }

    @Test
    fun `database key is generated and persisted`() {
        // When
        val key1 = securityManager.getDatabaseKey()
        val key2 = securityManager.getDatabaseKey()

        // Then
        assertEquals("Database key should be 32 bytes (256 bits)", 32, key1.size)
        assertArrayEquals("Same key should be returned on subsequent calls", key1, key2)
    }

    @Test
    fun `isInitialized returns true after key generation`() {
        // When
        securityManager.getDatabaseKey()

        // Then
        assertTrue("Should be initialized after generating database key",
            securityManager.isInitialized())
    }

    @Test(expected = Exception::class)
    fun `decrypting corrupted data throws exception`() {
        // Given
        val corruptedData = ByteArray(50) { 0xFF.toByte() }

        // When - This should throw due to authentication tag failure
        securityManager.decrypt(corruptedData)

        // Then - exception expected
    }

    @Test
    fun `wipeKeys clears all security data`() {
        // Given
        securityManager.getDatabaseKey()
        assertTrue("Should be initialized", securityManager.isInitialized())

        // When
        securityManager.wipeKeys()

        // Then
        assertFalse("Should not be initialized after wiping keys",
            securityManager.isInitialized())
    }
}
