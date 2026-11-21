package com.personal.cardvault.backup

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The checked v1 header of a CardVault backup file. */
data class BackupFileHeader(
    val salt: ByteArray,
    val nonce: ByteArray,
)

/** The input is not a complete, supported CardVault backup file. */
class BackupFormatException(message: String) : IllegalArgumentException(message)

/**
 * Encodes and decodes the v1 backup envelope specified in document section 9.1.
 *
 * Argon2id is deliberately derived on [Dispatchers.Default]: its synchronous native call is
 * expensive and must not run on the UI thread. The supplied password is never retained.
 */
object BackupFileCodec {
    private val magic = byteArrayOf('C'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val version: Byte = 0x01
    private const val saltSize = 16
    private const val nonceSize = 12
    private const val headerSize = 33
    private const val gcmTagSize = 16
    private const val keySize = 32
    private const val argonIterations = 3
    private const val argonMemoryKiB = 65_536
    private const val argonParallelism = 1
    private const val aesGcm = "AES/GCM/NoPadding"

    /** Creates a v1 file: 33-byte header followed by AES-256-GCM ciphertext and tag. */
    suspend fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray = withContext(Dispatchers.Default) {
        val salt = ByteArray(saltSize).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(nonceSize).also(SecureRandom()::nextBytes)
        val header = makeHeader(salt, nonce)
        val key = deriveKey(password, salt)
        try {
            val ciphertext = Cipher.getInstance(aesGcm).run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(gcmTagSize * 8, nonce))
                updateAAD(header)
                doFinal(plaintext)
            }
            header + ciphertext
        } finally {
            key.fill(0)
        }
    }

    /** Validates the non-secret structural fields required before asking for a password. */
    fun preflight(file: ByteArray): BackupFileHeader {
        if (file.size < headerSize + gcmTagSize) {
            throw BackupFormatException("Not a backup file")
        }
        if (!file.copyOfRange(0, magic.size).contentEquals(magic) || file[4] != version) {
            throw BackupFormatException("Not a supported backup file")
        }
        return BackupFileHeader(
            salt = file.copyOfRange(5, 5 + saltSize),
            nonce = file.copyOfRange(5 + saltSize, headerSize),
        )
    }

    /** Decrypts a structurally valid v1 file. Authentication failures are left to the caller. */
    suspend fun decrypt(file: ByteArray, password: CharArray): ByteArray = withContext(Dispatchers.Default) {
        val header = preflight(file)
        val key = deriveKey(password, header.salt)
        try {
            Cipher.getInstance(aesGcm).run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(gcmTagSize * 8, header.nonce))
                updateAAD(file, 0, headerSize)
                doFinal(file, headerSize, file.size - headerSize)
            }
        } finally {
            key.fill(0)
        }
    }

    private fun makeHeader(salt: ByteArray, nonce: ByteArray): ByteArray =
        magic + byteArrayOf(version) + salt + nonce

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val encodedPassword = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password))
        val passwordBytes = ByteArray(encodedPassword.remaining()).also(encodedPassword::get)
        return try {
            Argon2Kt().hash(
                Argon2Mode.ARGON2_ID,
                passwordBytes,
                salt,
                argonIterations,
                argonMemoryKiB,
                argonParallelism,
                keySize,
                Argon2Version.V13,
            ).rawHashAsByteArray()
        } finally {
            passwordBytes.fill(0)
        }
    }
}
