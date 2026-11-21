package com.personal.cardvault.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** U21/U24: expectations from dev-plan.md 9.1 and 9.4; no native Argon2 call is needed. */
class BackupFilePreflightTest {
    // Structural fixture only: the 16 trailing bytes stand in for a GCM tag.
    private fun envelope(size: Int = 49): ByteArray = ByteArray(size).also {
        "CVBK".toByteArray(Charsets.US_ASCII).copyInto(it, 0)
        it[4] = 1
        ByteArray(16) { index -> index.toByte() }.copyInto(it, 5)
        ByteArray(12) { index -> (index + 16).toByte() }.copyInto(it, 21)
    }

    @Test
    fun u21_saltAndNonceHaveSpecifiedLengthsAndOffsets() {
        val header = BackupFileCodec.preflight(envelope())

        assertEquals(16, header.salt.size)
        assertEquals(12, header.nonce.size)
        assertArrayEquals(ByteArray(16) { it.toByte() }, header.salt)
        assertArrayEquals(ByteArray(12) { (it + 16).toByte() }, header.nonce)
    }

    @Test
    fun u24_minimum49BytesAndLongerFilesPassStructuralPreflight() {
        for (size in listOf(49, 50, 128)) {
            val header = BackupFileCodec.preflight(envelope(size))
            assertArrayEquals(ByteArray(16) { it.toByte() }, header.salt)
            assertArrayEquals(ByteArray(12) { (it + 16).toByte() }, header.nonce)
        }
    }

    @Test
    fun u24_everyLengthBelow49IsRejectedEvenWithValidHeaderPrefix() {
        val validPrefix = envelope()
        for (size in 0 until 49) {
            assertThrows("length $size", BackupFormatException::class.java) {
                BackupFileCodec.preflight(validPrefix.copyOf(size))
            }
        }
    }

    @Test
    fun u24_wrongMagicIsRejectedAtEachOfItsFourOffsets() {
        for (offset in 0 until 4) {
            val file = envelope()
            file[offset] = (file[offset].toInt() xor 1).toByte()
            assertThrows("magic byte $offset", BackupFormatException::class.java) {
                BackupFileCodec.preflight(file)
            }
        }
    }

    @Test
    fun u24_everyVersionExcept01IsRejected() {
        for (version in 0..255) {
            if (version == 1) continue
            val file = envelope()
            file[4] = version.toByte()
            assertThrows("version $version", BackupFormatException::class.java) {
                BackupFileCodec.preflight(file)
            }
        }
    }

    @Test
    fun u24_decryptAlsoRejectsMalformedFilesBeforeKeyDerivation() {
        val shortFile = envelope().copyOf(48)
        val badMagic = envelope().also { it[0] = 0 }
        val badVersion = envelope().also { it[4] = 2 }
        for (file in listOf(shortFile, badMagic, badVersion)) {
            assertThrows(BackupFormatException::class.java) {
                runBlocking { BackupFileCodec.decrypt(file, "测试口令".toCharArray()) }
            }
        }
    }
}
