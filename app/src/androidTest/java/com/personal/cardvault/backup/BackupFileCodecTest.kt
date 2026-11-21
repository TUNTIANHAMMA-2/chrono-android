package com.personal.cardvault.backup

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * U21–U24: dev-plan.md 9.1/9.4. Uses the real Android Argon2Kt library, never a fake KDF.
 * Run on Android at a checkpoint (D-1); host preflight coverage is in BackupFilePreflightTest.
 * All content and passwords below are fictitious test data.
 */
class BackupFileCodecTest {
    private val password = "测试口令🔑".toCharArray()
    private val emptyBackup = BackupContent("2026-11-18T09:00:00Z", emptyList(), emptyList())

    @Test
    fun u21_encodedFileHas33ByteHeaderAnd16ByteTag() = runBlocking<Unit> {
        val plaintext = Json.encodeToString(emptyBackup).toByteArray(Charsets.UTF_8)
        val file = BackupFileCodec.encrypt(plaintext, password)

        assertArrayEquals("CVBK".toByteArray(Charsets.US_ASCII), file.copyOfRange(0, 4))
        assertEquals(1, file[4].toInt())
        assertEquals(33 + plaintext.size + 16, file.size)
        val header = BackupFileCodec.preflight(file)
        assertEquals(16, header.salt.size)
        assertEquals(12, header.nonce.size)
        assertArrayEquals(file.copyOfRange(5, 21), header.salt)
        assertArrayEquals(file.copyOfRange(21, 33), header.nonce)
    }

    @Test
    fun u21_encryptingTwiceGeneratesFreshSaltAndNonce() = runBlocking<Unit> {
        val first = BackupFileCodec.encrypt(byteArrayOf(1, 2, 3), password)
        val second = BackupFileCodec.encrypt(byteArrayOf(1, 2, 3), password)

        assertFalse("salt must be random", first.copyOfRange(5, 21).contentEquals(second.copyOfRange(5, 21)))
        assertFalse("nonce must be random", first.copyOfRange(21, 33).contentEquals(second.copyOfRange(21, 33)))
    }

    @Test
    fun u22_jsonRoundTripPreservesUnicodeLeadingZerosAndEmptyFields() = runBlocking<Unit> {
        val content = BackupContent(
            exportedAt = "2026-11-18T09:00:00Z",
            expiryItems = listOf(BackupExpiryItem(
                name = "测试订阅", category = "", startsOn = null, validThrough = "2027-01-31",
                notes = "换行\n引号\"反斜杠\\", isPaused = false, cycle = "MONTHLY", cycleAnchorDay = 31,
            )),
            cards = listOf(BackupCard(
                name = "测试卡", issuer = "", number = "00001234", expMonth = "01",
                expYear = "2030", cvv = "007", notes = "仅用于测试🔑",
            )),
        )
        val plaintext = Json.encodeToString(content).toByteArray(Charsets.UTF_8)
        val file = BackupFileCodec.encrypt(plaintext, password)
        val restored = BackupFileCodec.decrypt(file, password)

        assertArrayEquals(plaintext, restored)
        assertEquals(content, Json.decodeFromString<BackupContent>(restored.toString(Charsets.UTF_8)))
    }

    @Test
    fun u22_emptyPlaintextAndEveryByteValueRoundTrip() = runBlocking<Unit> {
        for (plaintext in listOf(byteArrayOf(), ByteArray(256) { it.toByte() })) {
            val file = BackupFileCodec.encrypt(plaintext, password)
            assertEquals(33 + plaintext.size + 16, file.size)
            assertArrayEquals(plaintext, BackupFileCodec.decrypt(file, password))
        }
    }

    @Test
    fun u22_decryptsIndependentV1FixtureUsingSpecifiedArgon2Parameters() = runBlocking<Unit> {
        // Generated independently with system libargon2 argon2id_hash_raw(3, 65536, 1),
        // 32 output bytes, v1.3, UTF-8 password, and Python cryptography AESGCM(header AAD).
        // Salt is 00..0f, nonce is 10..1b. No production codec was used to make this fixture.
        val file = (
            "4356424b01000102030405060708090a0b0c0d0e0f101112131415161718191a1b" +
                "f4d5bebcf0df60c0a6281231263ad7be17e6d00d9f70569bd21b9f706e80b89f" +
                "ddb23ee5f836c5fc52eeea1daa29556bb186c0ead8eb262679483a3cce82e6c81c" +
                "022091b8fb51ffaa379ebbb09713c8cd"
            ).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val expected = """{"exportedAt":"2026-11-18T09:00:00Z","expiryItems":[],"cards":[]}"""

        assertArrayEquals(expected.toByteArray(Charsets.UTF_8), BackupFileCodec.decrypt(file, password))
    }

    @Test
    fun u21_encryptionUsesSpecifiedKdfAes256GcmAndWholeHeaderAsAad() = runBlocking<Unit> {
        val plaintext = Json.encodeToString(emptyBackup).toByteArray(Charsets.UTF_8)
        val file = BackupFileCodec.encrypt(plaintext, password)
        // Independent JCA decryption prevents matching encrypt/decrypt bugs from passing.
        withContext(Dispatchers.Default) {
            val key = Argon2Kt().hash(
                Argon2Mode.ARGON2_ID, "测试口令🔑".toByteArray(Charsets.UTF_8),
                file.copyOfRange(5, 21), 3, 65536, 1, 32, Argon2Version.V13,
            ).rawHashAsByteArray()
            try {
                fun cipher() = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                        GCMParameterSpec(128, file.copyOfRange(21, 33)))
                }
                val ciphertext = file.copyOfRange(33, file.size)
                val authenticated = cipher().apply { updateAAD(file.copyOfRange(0, 33)) }
                assertArrayEquals(plaintext, authenticated.doFinal(ciphertext))
                assertThrows(AEADBadTagException::class.java) { cipher().doFinal(ciphertext) }
            } finally {
                key.fill(0)
            }
        }
    }

    @Test
    fun u23_changingEachHeaderCiphertextOrTagByteRejectsTheFile() = runBlocking<Unit> {
        val plaintext = Json.encodeToString(emptyBackup).toByteArray(Charsets.UTF_8)
        val file = BackupFileCodec.encrypt(plaintext, password)
        for (offset in file.indices) {
            val changed = file.copyOf()
            changed[offset] = (changed[offset].toInt() xor 1).toByte()
            // Magic/version are rejected by preflight; salt/nonce/ciphertext/tag by GCM.
            val expectedException = if (offset < 5) BackupFormatException::class.java
                else AEADBadTagException::class.java
            assertThrows("tampered byte $offset", expectedException) {
                runBlocking { BackupFileCodec.decrypt(changed, password) }
            }
        }
    }

    @Test
    fun u22_wrongPasswordFailsAuthentication() = runBlocking<Unit> {
        val file = BackupFileCodec.encrypt(Json.encodeToString(emptyBackup).toByteArray(Charsets.UTF_8), password)
        assertThrows(AEADBadTagException::class.java) {
            runBlocking { BackupFileCodec.decrypt(file, "另一个测试口令".toCharArray()) }
        }
    }
}
