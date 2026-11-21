package com.personal.cardvault.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The plaintext encrypted into a payment card's ciphertext column. */
@Serializable
data class CardPayload(
    val name: String = "",
    val issuer: String = "",
    val number: String = "",
    val expMonth: String = "",
    val expYear: String = "",
    val cvv: String = "",
    val notes: String = "",
    val artFileName: String = "",
    val network: String = "",
    val cardType: String = "",
)

private val cardPayloadJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

fun CardPayload.toJson(): String = cardPayloadJson.encodeToString(this)

fun cardPayloadFromJson(json: String): CardPayload = cardPayloadJson.decodeFromString(json)

/** Formats the card expiry for copying, using the required MM/YY representation. */
fun formatExpiry(expMonth: String, expYear: String): String =
    "${expMonth.padStart(2, '0')}/${expYear.takeLast(2)}"
