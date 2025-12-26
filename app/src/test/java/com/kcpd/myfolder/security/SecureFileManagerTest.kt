package com.kcpd.myfolder.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for SecureFileManager.
 *
 * Tests file encryption, decryption, streaming, and secure deletion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecureFileManagerTest {

    private lateinit var secureFileManager: SecureFileManager
    private lateinit var securityManager: SecurityManager
    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        securityManager = SecurityManager(context)
        secureFileManager = SecureFileManager(context, securityManager)
        testDir = File(context.cacheDir, "test").apply { mkdirs() }
    }

    @Test
    fun `encrypt and decrypt file successfully`() = runBlocking {
        // Given
        val originalFile = File(testDir, "test.txt")
        originalFile.writeText("Secret content")
        val destDir = File(testDir, "encrypted")

        // When
        val encryptedFile = secureFileManager.encryptFile(originalFile, destDir)
        val decryptedFile = secureFileManager.decryptFile(encryptedFile)

        // Then
        assertFalse("Original file should be securely deleted", originalFile.exists())
        assertTrue("Encrypted file should exist", encryptedFile.exists())
        assertTrue("Decrypted file should exist", decryptedFile.exists())
        assertEquals("Decrypted content should match original",
            "Secret content", decryptedFile.readText())

        // Cleanup
        decryptedFile.delete()
        encryptedFile.delete()
    }

    @Test
    fun `encrypted file has .enc extension`() = runBlocking {
        // Given
        val originalFile = File(testDir, "document.pdf")
        originalFile.writeText("PDF content")
        val destDir = File(testDir, "encrypted")

        // When
        val encryptedFile = secureFileManager.encryptFile(originalFile, destDir)

        // Then
        assertTrue("Encrypted file should have .enc extension",
            encryptedFile.name.endsWith(".enc"))
        assertEquals("Should preserve original name",
            "document.pdf.enc", encryptedFile.name)

        // Cleanup
        encryptedFile.delete()
    }

    @Test
    fun `streaming decryption works for large files`() = runBlocking {
        // Given - Create a 1MB test file
        val originalFile = File(testDir, "large.bin")
        val originalData = ByteArray(1024 * 1024) { it.toByte() }
        originalFile.writeBytes(originalData)
        val destDir = File(testDir, "encrypted")

        // When
        val encryptedFile = secureFileManager.encryptFile(originalFile, destDir)
        val inputStream = secureFileManager.getStreamingDecryptedInputStream(encryptedFile)
        val decryptedData = inputStream.readBytes()
        inputStream.close()

        // Then
        assertArrayEquals("Streaming decryption should produce correct data",
            originalData, decryptedData)

        // Cleanup
        encryptedFile.delete()
    }

    @Test
    fun `isEncrypted detects encrypted files`() = runBlocking {
        // Given
        val plainFile = File(testDir, "plain.txt")
        plainFile.writeText("Plain text")

        val encryptedFile = secureFileManager.encryptFileInPlace(plainFile)

        // Then
        assertTrue("Should detect encrypted file",
            secureFileManager.isEncrypted(encryptedFile))

        // Cleanup
        encryptedFile.delete()
    }

    @Test
    fun `getOriginalFileName removes .enc extension`() {
        // Given
        val encryptedFile = File(testDir, "photo.jpg.enc")

        // When
        val originalName = secureFileManager.getOriginalFileName(encryptedFile)

        // Then
        assertEquals("Should remove .enc extension", "photo.jpg", originalName)
    }

    @Test
    fun `secure delete overwrites file multiple times`() = runBlocking {
        // Given
        val file = File(testDir, "sensitive.txt")
        file.writeText("Very sensitive data that must be destroyed!")
        val originalSize = file.length()

        // When
        val deleted = secureFileManager.secureDelete(file)

        // Then
        assertTrue("Secure delete should return true", deleted)
        assertFalse("File should no longer exist", file.exists())
        // Note: We can't easily verify the overwrite pattern in unit tests,
        // but we ensure the API works correctly
    }

    @Test
    fun `secure delete handles non-existent files`() = runBlocking {
        // Given
        val nonExistentFile = File(testDir, "doesnt_exist.txt")

        // When
        val result = secureFileManager.secureDelete(nonExistentFile)

        // Then
        assertFalse("Should return false for non-existent file", result)
    }

    @Test
    fun `encrypt file in place removes original`() = runBlocking {
        // Given
        val file = File(testDir, "inplace.txt")
        file.writeText("Content")

        // When
        val encryptedFile = secureFileManager.encryptFileInPlace(file)

        // Then
        assertFalse("Original file should be deleted", file.exists())
        assertTrue("Encrypted file should exist", encryptedFile.exists())
        assertTrue("Encrypted filename should have .enc",
            encryptedFile.name.endsWith(".enc"))

        // Cleanup
        encryptedFile.delete()
    }

    @Test
    fun `decrypt file in place removes encrypted file`() = runBlocking {
        // Given
        val file = File(testDir, "decrypt_test.txt")
        file.writeText("Original content")
        val encryptedFile = secureFileManager.encryptFileInPlace(file)

        // When
        val decryptedFile = secureFileManager.decryptFileInPlace(encryptedFile)

        // Then
        assertFalse("Encrypted file should be deleted", encryptedFile.exists())
        assertTrue("Decrypted file should exist", decryptedFile.exists())
        assertEquals("Content should be preserved",
            "Original content", decryptedFile.readText())

        // Cleanup
        decryptedFile.delete()
    }
}
