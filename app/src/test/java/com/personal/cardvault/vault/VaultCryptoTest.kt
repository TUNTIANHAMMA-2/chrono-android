package com.personal.cardvault.vault

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Expectations: dev-plan.md sections 5.3, 8.1, 8.2 and U26. All card data is fictitious. */
class VaultCryptoTest {
    private val payload = CardPayload(
        name = "测试卡",
        issuer = "虚构发卡行",
        number = "00001234",
        expMonth = "01",
        expYear = "2030",
        cvv = "007",
        notes = "仅用于测试\n含引号\"和反斜杠\\",
    )

    @Test
    fun generatedDekHas32Bytes() {
        assertEquals(32, generateDek().size)
    }

    @Test
    fun sameDekRoundTripPreservesEveryFieldIncludingLeadingZeros() {
        val dek = generateDek()
        val encrypted = encryptCardPayload(dek, payload)

        assertEquals(12, encrypted.iv.size)
        assertEquals(payload, decryptCardPayload(dek, encrypted.iv, encrypted.ciphertext))
    }

    @Test
    fun allEmptyFieldsRoundTrip() {
        val dek = generateDek()
        val empty = CardPayload(
            name = "", issuer = "", number = "", expMonth = "", expYear = "", cvv = "", notes = "",
        )
        val encrypted = encryptCardPayload(dek, empty)

        assertEquals(empty, decryptCardPayload(dek, encrypted.iv, encrypted.ciphertext))
    }

    @Test
    fun encryptingSamePlaintextTwiceUsesDifferentIvsAndCiphertexts() {
        val dek = generateDek()
        val first = encryptCardPayload(dek, payload)
        val second = encryptCardPayload(dek, payload)

        assertEquals(12, first.iv.size)
        assertEquals(12, second.iv.size)
        assertFalse("Each encryption must use a fresh IV", first.iv.contentEquals(second.iv))
        assertFalse("Repeated plaintext must produce different ciphertexts",
            first.ciphertext.contentEquals(second.ciphertext))
        assertEquals(payload, decryptCardPayload(dek, first.iv, first.ciphertext))
        assertEquals(payload, decryptCardPayload(dek, second.iv, second.ciphertext))
    }

    @Test
    fun ciphertextIsAesGcmJsonWith16ByteTagAndNoAad() {
        val dek = generateDek()
        val encrypted = encryptCardPayload(dek, payload)
        // Use JCA directly so matching mistakes in the production pair cannot pass a round trip.
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(128, encrypted.iv))
        }
        val plaintext = cipher.doFinal(encrypted.ciphertext)

        assertEquals(plaintext.size + 16, encrypted.ciphertext.size)
        val fields = Json.parseToJsonElement(plaintext.decodeToString()).jsonObject
            .mapValues { (_, value) -> value.jsonPrimitive.content }
        assertEquals(mapOf(
            "name" to "测试卡",
            "issuer" to "虚构发卡行",
            "number" to "00001234",
            "expMonth" to "01",
            "expYear" to "2030",
            "cvv" to "007",
            "notes" to "仅用于测试\n含引号\"和反斜杠\\",
        ), fields)
    }
}
