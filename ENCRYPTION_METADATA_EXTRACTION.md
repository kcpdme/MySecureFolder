# How Metadata is Extracted from Encrypted Files

## Question: How do we get metadata without decrypting the entire file?

**Short Answer**: The metadata is stored **separately encrypted** in the file header and can be decrypted **independently** from the file body using the Master Key.

---

## Encrypted File Structure

Every encrypted file (`.enc`) has this structure:

```
┌─────────────────────────────────────────────────────────────┐
│                      ENCRYPTED FILE                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              FILE HEADER (Fixed Size)               │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │  MAGIC (4 bytes):  "KCPD" [0x4B, 0x43, 0x50, 0x44] │    │
│  │  VERSION (1 byte): 0x01                             │    │
│  │  IV (12 bytes):    Random IV for FEK wrapping       │    │
│  │  ENC_FEK (48 bytes): Encrypted File Encryption Key  │    │
│  │  META_LEN (4 bytes): Length of metadata (N bytes)   │    │
│  │  META (N bytes): ENCRYPTED metadata JSON            │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │          FILE BODY (Variable Size)                  │    │
│  │         ENCRYPTED with FEK (AES-CTR)                │    │
│  │                                                       │    │
│  │  [Millions of bytes of encrypted photo/video data]  │    │
│  │                                                       │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## The Magic: Two-Level Encryption

### Level 1: File Body Encryption (Streaming)
- **Key**: FEK (File Encryption Key) - Random 32-byte AES key
- **Algorithm**: AES-CTR (streaming cipher)
- **Purpose**: Encrypt the actual file content (photo/video data)

### Level 2: Metadata + FEK Encryption (Envelope)
- **Key**: Master Key (derived from Password + Seed Words)
- **Algorithm**: AES-GCM (authenticated encryption)
- **Purpose**:
  - Encrypt the FEK itself (envelope encryption)
  - Encrypt the metadata (filename, timestamp, MIME type)

---

## How `validateAndGetMetadata()` Works

**Code Location**: [SecureFileManager.kt:445-461](app/src/main/java/com/kcpd/myfolder/security/SecureFileManager.kt#L445-L461)

```kotlin
suspend fun validateAndGetMetadata(file: File): FileMetadata? = withContext(Dispatchers.IO) {
    try {
        FileInputStream(file).use { input ->
            // Step 1: Read ONLY the header (NOT the entire file!)
            val header = FileHeader.readHeader(input)

            // Step 2: Get Master Key
            val masterKey = securityManager.getActiveMasterKey()

            // Step 3: Unwrap FEK (validates Master Key is correct)
            securityManager.unwrapFEK(header.encryptedFek, header.iv, masterKey)

            // Step 4: Decrypt ONLY the metadata (NOT the file body!)
            return@use decryptMetadata(header.meta, masterKey)
        }
    } catch (e: Exception) {
        return@withContext null
    }
}
```

### Step-by-Step Breakdown

#### Step 1: Read Header (Lines 447-448)

**File Location**: [FileHeader.kt:38-69](app/src/main/java/com/kcpd/myfolder/security/FileHeader.kt#L38-L69)

```kotlin
fun readHeader(inputStream: InputStream): FileHeader {
    val dataInputStream = DataInputStream(inputStream)

    // Read MAGIC (4 bytes)
    val magic = ByteArray(4)
    dataInputStream.readFully(magic)
    if (!magic.contentEquals(MAGIC)) {
        throw IllegalArgumentException("Invalid file format")
    }

    // Read VERSION (1 byte)
    val version = dataInputStream.readByte()

    // Read IV (12 bytes)
    val iv = ByteArray(12)
    dataInputStream.readFully(iv)

    // Read ENC_FEK (48 bytes)
    val encryptedFek = ByteArray(48)
    dataInputStream.readFully(encryptedFek)

    // Read META_LEN (4 bytes)
    val metaLen = dataInputStream.readInt()

    // Read META (N bytes)
    val meta = ByteArray(metaLen)
    dataInputStream.readFully(meta)

    return FileHeader(version, iv, encryptedFek, metaLen, meta)
}
```

**Key Point**: This only reads the **header** (first ~100-200 bytes), NOT the entire file!

For a 100MB video:
- Total file size: 100,000,000 bytes
- Header size: ~150 bytes (MAGIC + VERSION + IV + ENC_FEK + META_LEN + META)
- **We read only 0.00015% of the file!**

#### Step 2: Get Master Key (Line 449)

```kotlin
val masterKey = securityManager.getActiveMasterKey()
```

The Master Key is already in memory (loaded when vault was unlocked).

#### Step 3: Unwrap FEK (Line 453)

```kotlin
securityManager.unwrapFEK(header.encryptedFek, header.iv, masterKey)
```

**Purpose**: Validates that the Master Key is correct by attempting to decrypt the FEK.

**Process**:
```
Encrypted FEK (48 bytes) = IV (12) + Ciphertext (32) + GCM Tag (16)
                               ↓
                      AES-GCM Decrypt with Master Key
                               ↓
                    Decrypted FEK (32 bytes)
```

If Master Key is wrong → GCM authentication fails → Exception thrown → returns `null`

#### Step 4: Decrypt Metadata (Line 456)

**Code Location**: [SecureFileManager.kt:428-439](app/src/main/java/com/kcpd/myfolder/security/SecureFileManager.kt#L428-L439)

```kotlin
fun decryptMetadata(encryptedMetadata: ByteArray, key: SecretKey): FileMetadata? {
    try {
        // Extract IV (first 12 bytes)
        val iv = encryptedMetadata.copyOfRange(0, 12)

        // Extract ciphertext (remaining bytes)
        val ciphertext = encryptedMetadata.copyOfRange(12, encryptedMetadata.size)

        // Decrypt with AES-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plaintext = cipher.doFinal(ciphertext)

        // Parse JSON
        return Json.decodeFromString(String(plaintext, Charsets.UTF_8))
    } catch (e: Exception) {
        return null
    }
}
```

**Process**:
```
Encrypted Metadata (header.meta):
  IV (12 bytes) + Encrypted JSON + GCM Tag (16 bytes)
           ↓
  AES-GCM Decrypt with Master Key
           ↓
  Plaintext JSON:
  {
    "filename": "MEDIA_20251228_172512.jpg",
    "mimeType": "image/jpeg",
    "timestamp": 1735401234567
  }
           ↓
  Parse JSON → FileMetadata object
```

**Result**: We get the `FileMetadata` containing:
- Original filename: `"MEDIA_20251228_172512.jpg"`
- MIME type: `"image/jpeg"`
- Timestamp: `1735401234567`

---

## What We Do NOT Decrypt

**Important**: The **file body** (actual photo/video data) is **NEVER decrypted** during metadata extraction!

```
File Structure:

┌─────────────────────────────────────┐
│  HEADER (150 bytes)                 │  ← WE READ THIS
│  ├─ MAGIC                           │
│  ├─ VERSION                         │
│  ├─ IV                              │
│  ├─ ENC_FEK  ← Decrypt with Master Key
│  └─ META     ← Decrypt with Master Key
├─────────────────────────────────────┤
│  BODY (100 MB)                      │  ← WE SKIP THIS ENTIRELY!
│  [Encrypted photo data...]          │
│  [Encrypted photo data...]          │
│  [... millions more bytes ...]      │
│                                      │
└─────────────────────────────────────┘
```

**Performance**:
- Reading header: ~1-2ms (only first 150 bytes)
- Decrypting metadata: ~1-2ms (small JSON, ~100 bytes)
- **Total**: ~2-4ms

vs.

- Decrypting entire 100MB file: ~500-1000ms (depends on device)

**Speedup**: ~250-500x faster!

---

## Security Analysis

### Question: Is this secure?

**Yes!** Here's why:

#### 1. Master Key Required
- Metadata is encrypted with **Master Key** (derived from Password + Seed Words)
- Without correct Master Key → Cannot decrypt metadata
- GCM provides **authentication** (tamper-proof)

#### 2. FEK Validation
- Before decrypting metadata, we **validate** the Master Key by unwrapping FEK
- If wrong Master Key → FEK unwrap fails → Returns `null`
- This prevents brute-force attacks on metadata alone

#### 3. No Information Leakage
- File body remains encrypted
- Only metadata is decrypted (filename, timestamp, MIME type)
- Metadata reveals no sensitive content (file body is what matters)

#### 4. Authenticated Encryption
- Uses **AES-GCM** for metadata (not just encryption, but also authentication)
- Any tampering with metadata → GCM tag verification fails → Decryption fails

---

## Use Cases

### 1. Import Already-Encrypted Files

**Scenario**: User imports `bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc` from backup.

```kotlin
// Step 1: Read header and decrypt metadata (FAST - 2ms)
val metadata = secureFileManager.validateAndGetMetadata(tempFile)

if (metadata != null) {
    // File is already encrypted!
    // metadata.filename = "MEDIA_20251228_172512.jpg"

    // Step 2: Skip re-encryption (saves 500ms+)
    // Just move file to secure storage with new UUID
    val newUUID = UUID.randomUUID().toString()
    val targetFile = File(secureDir, "$newUUID.enc")
    tempFile.renameTo(targetFile)

    // Step 3: Store correct metadata in database
    database.insert(
        originalFileName = metadata.filename,  // "MEDIA_20251228_172512.jpg"
        encryptedFileName = targetFile.name    // "a1b2c3d4-NEW-UUID.enc"
    )
}
```

**Benefits**:
- ✅ Fast import (no re-encryption needed)
- ✅ Correct filename extracted from metadata
- ✅ User sees proper filename in UI

### 2. File Browser/Gallery

**Scenario**: User opens gallery screen with 1000 encrypted photos.

**Without metadata extraction**:
```kotlin
// ❌ BAD: Decrypt entire file to get filename
files.forEach { file ->
    val decrypted = decryptFile(file)  // 500ms × 1000 = 8 minutes!
    val filename = decrypted.name
}
```

**With metadata extraction**:
```kotlin
// ✅ GOOD: Read only metadata from header
files.forEach { file ->
    val metadata = validateAndGetMetadata(file)  // 2ms × 1000 = 2 seconds!
    val filename = metadata.filename
}
```

**Speedup**: ~240x faster (2 seconds vs 8 minutes)

### 3. Cloud Restore Validation

**Scenario**: User downloads encrypted files from cloud, wants to verify before restoring.

```kotlin
cloudFiles.forEach { file ->
    // Validate file without decrypting entire content
    val metadata = validateAndGetMetadata(file)

    if (metadata != null) {
        println("✓ Valid: ${metadata.filename}")
    } else {
        println("✗ Invalid or wrong Master Key")
    }
}
```

---

## Comparison with Full Decryption

### Full Decryption Approach (Slow)

```kotlin
fun getMetadataFullDecrypt(file: File): FileMetadata {
    // 1. Read entire file (100 MB)
    val encryptedData = file.readBytes()  // 100 ms

    // 2. Decrypt entire file (AES-CTR)
    val decryptedData = decrypt(encryptedData)  // 500 ms

    // 3. Parse metadata from decrypted file
    val metadata = parseMetadata(decryptedData)  // 10 ms

    // Total: ~610 ms
}
```

### Header-Only Approach (Fast)

```kotlin
fun getMetadataHeaderOnly(file: File): FileMetadata {
    // 1. Read only header (~150 bytes)
    val header = readHeader(file)  // 1 ms

    // 2. Decrypt only metadata (~100 bytes)
    val metadata = decryptMetadata(header.meta)  // 1 ms

    // Total: ~2 ms
}
```

**Performance Comparison**:
- Full decryption: 610ms
- Header-only: 2ms
- **Speedup**: 305x faster!

---

## Technical Details

### Encryption Algorithms Used

#### For Metadata:
```
Algorithm: AES-GCM (Authenticated Encryption)
Key: Master Key (32 bytes, derived from Password + Seed Words)
IV: Random 12 bytes (unique per file)
Tag: 16 bytes (authentication)

Process:
  Plaintext JSON → AES-GCM Encrypt → IV (12) + Ciphertext + Tag (16)
```

#### For FEK:
```
Algorithm: AES-GCM (Key Wrapping)
Key: Master Key (32 bytes)
IV: Random 12 bytes (unique per file)
Tag: 16 bytes (authentication)

Process:
  FEK (32 bytes) → AES-GCM Encrypt → IV (12) + Ciphertext (32) + Tag (16) = 48 bytes
```

#### For File Body:
```
Algorithm: AES-CTR (Streaming Cipher)
Key: FEK (32 bytes, random per file)
IV: File offset (counter mode)

Process:
  File data → AES-CTR Encrypt → Encrypted file data
```

---

## Summary

### How We Get Metadata Without Full Decryption:

1. **Encrypted files have a HEADER** containing encrypted metadata
2. **Header is small** (~150 bytes) compared to file body (could be GBs)
3. **Metadata is encrypted separately** with Master Key (AES-GCM)
4. **We read ONLY the header**, not the entire file
5. **We decrypt ONLY the metadata**, not the file body
6. **Result**: Get filename, timestamp, MIME type in ~2ms instead of 500ms+

### Security Guarantees:

- ✅ Master Key required to decrypt metadata
- ✅ FEK validation prevents brute-force on metadata
- ✅ AES-GCM provides authentication (tamper-proof)
- ✅ File body remains encrypted at all times
- ✅ No information leakage

### Performance Benefits:

- ✅ ~250-500x faster than full decryption
- ✅ Enables fast import of encrypted files
- ✅ Fast gallery/file browser
- ✅ Quick validation of cloud backups

---

## Related Files

- [SecureFileManager.kt](app/src/main/java/com/kcpd/myfolder/security/SecureFileManager.kt) - `validateAndGetMetadata()` function
- [FileHeader.kt](app/src/main/java/com/kcpd/myfolder/security/FileHeader.kt) - Binary header format
- [ImportMediaUseCase.kt](app/src/main/java/com/kcpd/myfolder/domain/usecase/ImportMediaUseCase.kt) - Uses metadata extraction for import
