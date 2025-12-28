# Critical Bug Fix: File Decryption After Password Change

## Issue Summary
After changing the password, files could not be decrypted. The image decoder reported "Format is not supported" because the decrypted data was garbage.

## Root Cause Analysis

### The Bug
The file encryption system uses a **single IV** in the file header for two purposes:
1. **FEK Wrapping**: Decrypting the File Encryption Key (FEK) with the Master Key (AES-GCM mode)
2. **Body Encryption**: Decrypting the file body with the FEK (AES-CTR mode, using IV padded to 16 bytes)

During password change, the `reWrapFileAtomic` function:
- Unwrapped the FEK with the OLD Master Key using the original IV ✅
- Generated a **NEW random IV** when re-wrapping the FEK with the NEW Master Key ❌
- Stored the **NEW IV** in the file header ❌
- BUT the file body remained encrypted with the **ORIGINAL IV** (padded) ❌

Result: When decrypting after password change:
- The header had the **NEW IV**
- Decryption used `bodyIv = newIv.copyOf(16)` 
- But the body was encrypted with `bodyIv = originalIv.copyOf(16)`
- **IV mismatch → Garbage decrypted data** → Image decoder failure

### Why This Happened
The original design reused the same IV for both FEK wrapping and body encryption to save space. This is secure because:
- The FEK wrapping uses AES-GCM (authenticated)
- The body uses AES-CTR with a unique FEK per file
- IV reuse between different keys/modes is safe

But during re-wrapping, we forgot that changing the IV would break body decryption!

## The Fix

### Code Changes

**1. Modified `SecurityManager.wrapFEK()` to accept optional IV:**
```kotlin
fun wrapFEK(fek: SecretKey, masterKey: SecretKey, iv: ByteArray? = null): Pair<ByteArray, ByteArray>
```
- If `iv` is provided, uses it instead of generating a new random IV
- This allows re-wrapping to preserve the original IV

**2. Updated `SecureFileManager.reWrapFileAtomic()`:**
```kotlin
// OLD CODE (BUGGY):
val (newIv, newEncFek) = securityManager.wrapFEK(fek, newKey)
val newHeader = FileHeader(iv = newIv, ...)  // NEW IV breaks body decryption!

// NEW CODE (FIXED):
val (newIv, newEncFek) = securityManager.wrapFEK(fek, newKey, header.iv)  // REUSE original IV
val newHeader = FileHeader(iv = newIv, ...)  // newIv == header.iv now
```

**3. Added comprehensive logging:**
- IV values (hex format) at each step
- FEK unwrap/wrap success confirmations
- Metadata decryption confirmations
- Body decryption cipher initialization logs

## Verification Steps

### Before Testing
1. **Backup your existing encrypted files** (if important data)
2. Note: Existing files that were re-wrapped with the BUG will need to be re-wrapped again after this fix

### Testing Procedure
1. **Import fresh files** into the vault
2. **Change password** via Settings
3. **Verify files are viewable** immediately after change
4. **Restart the app**
5. **Unlock with NEW password**
6. **Verify files are still viewable** after restart

### What to Check in Logs
After password change, look for:
```
SecureFileManager: 🔄 Re-wrapping [filename].enc
SecureFileManager: 📖 Original header IV: [hex values]
SecureFileManager: ✅ FEK unwrapped successfully with old key
SecureFileManager: ✅ FEK wrapped with new key, IV preserved: true
SecureFileManager: 📝 New header created with IV: [same hex values]
SecureFileManager: Successfully re-wrapped: [filename].enc
```

When viewing files:
```
SecureFileManager: 🔓 Starting streaming decryption for: [filename].enc
SecureFileManager: 📖 Header IV: [hex values]
SecureFileManager: ✅ FEK unwrapped successfully
SecureFileManager: 📖 Body IV (padded): [hex values with trailing zeros]
SecureFileManager: ✅ Cipher initialized for body decryption
```

### For Existing Broken Files
If you have files that were already re-wrapped with the buggy code:
1. They are **NOT recoverable** with the new password (body encrypted with wrong IV reference)
2. You need to:
   - **Temporarily revert** to the OLD password (before this fix was applied)
   - Re-import the files fresh
   - Change password again with the fixed code

## Technical Details

### File Format Spec
```
[4B MAGIC] [1B VERSION] [12B IV] [48B ENC_FEK] [4B META_LEN] [VAR META] [VAR BODY]
```

The **12-byte IV** serves dual purpose:
- **AES-GCM FEK Decryption**: `unwrapFEK(encFek, iv, masterKey)` → FEK
- **AES-CTR Body Decryption**: `bodyIv = iv.copyOf(16)` (padded with 4 zero bytes)

### Why This Design Works
- **IV uniqueness**: Each file has a unique random 12-byte IV
- **Mode separation**: GCM (authenticated) for FEK, CTR (streaming) for body
- **Key separation**: Different keys (MasterKey vs FEK) make IV reuse safe
- **No collision risk**: IV + Key combination is unique per operation

### Re-wrapping Process (FIXED)
1. Read header with **original IV**
2. Unwrap FEK using **original IV + OLD MasterKey**
3. Re-wrap FEK using **original IV + NEW MasterKey** (key change here!)
4. Write header with **original IV** (unchanged)
5. Copy body as-is (still encrypted with FEK + original body IV)

Result: FEK is now protected by NEW MasterKey, but body IV remains consistent!

## Files Modified
1. `SecurityManager.kt` - Added optional `iv` parameter to `wrapFEK()`
2. `SecureFileManager.kt` - Fixed `reWrapFileAtomic()` to preserve original IV
3. Added comprehensive logging for debugging

## Prevention
- Added explicit comments about IV preservation in re-wrapping code
- Logging now shows IV values at each step for verification
- Unit tests should be added to verify IV consistency during re-wrap

## References
- Related fix: [FIX_ENCRYPTED_FILE_IMPORT.md](FIX_ENCRYPTED_FILE_IMPORT.md)
- Password rotation journal: [PasswordRotationJournal.kt](app/src/main/java/com/kcpd/myfolder/security/PasswordRotationJournal.kt)
- Atomic re-wrap: [VaultManager.kt#changePasswordSafely](app/src/main/java/com/kcpd/myfolder/vault/VaultManager.kt)
