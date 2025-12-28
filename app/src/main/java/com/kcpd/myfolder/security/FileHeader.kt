package com.kcpd.myfolder.security

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Represents the binary header of an encrypted file.
 *
 * Format:
 * MAGIC (4 bytes): [0x4B, 0x43, 0x50, 0x44] ("KCPD")
 * VERSION (1 byte): 0x01
 * IV (12 bytes): Unique Random IV for this file. Required to decrypt the FEK.
 * ENC_FEK (48 bytes): The FEK encrypted by MasterKey + IV. (32 bytes key + 16 bytes tag).
 * META_LEN (4 bytes): Integer length of the metadata JSON.
 * META (Var bytes): Encrypted Metadata JSON (Filename, MIME, Timestamp).
 */
data class FileHeader(
    val version: Byte,
    val iv: ByteArray,
    val encryptedFek: ByteArray,
    val metaLen: Int,
    val meta: ByteArray
) {
    companion object {
        val MAGIC = byteArrayOf(0x4B, 0x43, 0x50, 0x44) // "KCPD"
        const val VERSION_1: Byte = 0x01
        const val IV_SIZE = 12
        const val FEK_SIZE = 32
        const val GCM_TAG_SIZE = 16
        const val ENC_FEK_SIZE = FEK_SIZE + GCM_TAG_SIZE // 48 bytes

        /**
         * Reads the header from an input stream.
         * The stream is positioned after the header (at the start of the body) after this call.
         */
        fun readHeader(inputStream: InputStream): FileHeader {
            val dataInputStream = DataInputStream(inputStream)

            // 1. Verify MAGIC
            val magic = ByteArray(4)
            dataInputStream.readFully(magic)
            if (!magic.contentEquals(MAGIC)) {
                throw IllegalArgumentException("Invalid file format: MAGIC header mismatch")
            }

            // 2. Read VERSION
            val version = dataInputStream.readByte()
            if (version != VERSION_1) {
                throw IllegalArgumentException("Unsupported version: $version")
            }

            // 3. Read IV
            val iv = ByteArray(IV_SIZE)
            dataInputStream.readFully(iv)

            // 4. Read ENC_FEK
            val encryptedFek = ByteArray(ENC_FEK_SIZE)
            dataInputStream.readFully(encryptedFek)

            // 5. Read META_LEN
            val metaLen = dataInputStream.readInt()

            // 6. Read META
            val meta = ByteArray(metaLen)
            dataInputStream.readFully(meta)

            return FileHeader(version, iv, encryptedFek, metaLen, meta)
        }
    }

    /**
     * Writes the header to an output stream.
     */
    fun writeHeader(outputStream: OutputStream) {
        val dataOutputStream = DataOutputStream(outputStream)

        // 1. Write MAGIC
        dataOutputStream.write(MAGIC)

        // 2. Write VERSION
        dataOutputStream.writeByte(version.toInt())

        // 3. Write IV
        if (iv.size != IV_SIZE) {
            throw IllegalArgumentException("Invalid IV size: ${iv.size}, expected $IV_SIZE")
        }
        dataOutputStream.write(iv)

        // 4. Write ENC_FEK
        if (encryptedFek.size != ENC_FEK_SIZE) {
            throw IllegalArgumentException("Invalid ENC_FEK size: ${encryptedFek.size}, expected $ENC_FEK_SIZE")
        }
        dataOutputStream.write(encryptedFek)

        // 5. Write META_LEN
        dataOutputStream.writeInt(meta.size)

        // 6. Write META
        dataOutputStream.write(meta)
        
        dataOutputStream.flush()
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileHeader

        if (version != other.version) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!encryptedFek.contentEquals(other.encryptedFek)) return false
        if (metaLen != other.metaLen) return false
        if (!meta.contentEquals(other.meta)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + encryptedFek.contentHashCode()
        result = 31 * result + metaLen
        result = 31 * result + meta.contentHashCode()
        return result
    }
}
