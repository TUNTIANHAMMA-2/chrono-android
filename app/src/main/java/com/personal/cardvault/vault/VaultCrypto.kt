package com.personal.cardvault.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

const val VAULT_KEK_ALIAS = "vault_kek"

private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val AES_KEY_LENGTH_BITS = 256
private const val DEK_LENGTH_BYTES = 32
private const val CARD_IV_LENGTH_BYTES = 12

/** Ciphertext and IV for one encrypted [CardPayload]. */
data class EncryptedCardPayload(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

/** The actual Android Keystore protection level of the vault KEK. */
enum class VaultKekSecurityLevel {
    STRONGBOX,
    TEE,
    SOFTWARE,
    UNKNOWN,
}

/**
 * Android Keystore access for the vault KEK.
 *
 * Call [recreateKek] only after the caller has established that an existing key is unavailable;
 * it deliberately does not delete a key as a fallback for arbitrary crypto failures.
 */
class VaultKeyStore(
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) },
) {
    /**
     * Returns the existing KEK, or null only when its alias is absent.
     *
     * Unlike [getOrCreateKek], this method never deletes or recreates an alias. A present but
     * unreadable/non-secret entry is an error for callers that must preserve existing vault data.
     */
    fun getExistingKek(): SecretKey? {
        if (!keyStore.containsAlias(VAULT_KEK_ALIAS)) return null
        return keyStore.getKey(VAULT_KEK_ALIAS, null) as? SecretKey
            ?: throw UnrecoverableKeyException("Existing vault KEK is unavailable")
    }

    /**
     * Returns a usable existing KEK or creates/recreates one when the alias is absent or unusable.
     *
     * An [UnrecoverableKeyException], a null key, or a non-secret key explicitly means this alias
     * cannot provide the KEK, so it is replaced. Other Keystore failures propagate unchanged: in
     * particular, callers must not treat authentication or cipher/AEAD failures as recreation.
     */
    fun getOrCreateKek(): SecretKey {
        if (!keyStore.containsAlias(VAULT_KEK_ALIAS)) return generateKek()

        val existing = try {
            keyStore.getKey(VAULT_KEK_ALIAS, null)
        } catch (_: UnrecoverableKeyException) {
            null
        }
        return (existing as? SecretKey) ?: recreateKek()
    }

    /**
     * Replaces a KEK only after it has been explicitly identified as unavailable.
     * It is not a fallback for authentication, AEAD, or other encryption failures.
     */
    fun recreateKek(): SecretKey {
        deleteKek()
        return generateKek()
    }

    /** Deletes only the vault KEK alias; callers decide whether vault database records are retained. */
    fun deleteKek() {
        if (keyStore.containsAlias(VAULT_KEK_ALIAS)) keyStore.deleteEntry(VAULT_KEK_ALIAS)
    }

    /**
     * Reads the protection level reported by Android Keystore for the existing KEK.
     *
     * Returns null only if the KEK alias does not exist. Values Android does not recognize are
     * represented as [VaultKekSecurityLevel.UNKNOWN].
     */
    fun getKekSecurityLevel(): VaultKekSecurityLevel? {
        val kek = getExistingKek() ?: return null
        val keyInfo = SecretKeyFactory.getInstance(kek.algorithm, ANDROID_KEY_STORE)
            .getKeySpec(kek, KeyInfo::class.java) as KeyInfo
        return when (keyInfo.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> VaultKekSecurityLevel.STRONGBOX
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> VaultKekSecurityLevel.TEE
            KeyProperties.SECURITY_LEVEL_SOFTWARE -> VaultKekSecurityLevel.SOFTWARE
            else -> VaultKekSecurityLevel.UNKNOWN
        }
    }

    private fun generateKek(): SecretKey = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        ANDROID_KEY_STORE,
    ).apply {
        init(
            KeyGenParameterSpec.Builder(
                VAULT_KEK_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(AES_KEY_LENGTH_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
                .build(),
        )
    }.generateKey()

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    }
}

/**
 * Prepares a KEK operation for BiometricPrompt's CryptoObject.
 * The caller must authenticate this exact cipher with a CryptoObject for every KEK use.
 */
fun prepareKekEncryptionCipher(kek: SecretKey): Cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
    init(Cipher.ENCRYPT_MODE, kek)
}

/**
 * Prepares a KEK operation for BiometricPrompt's CryptoObject.
 * The caller must authenticate this exact cipher with a CryptoObject for every KEK use.
 */
fun prepareKekDecryptionCipher(kek: SecretKey, wrapIv: ByteArray): Cipher =
    Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
        init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, wrapIv))
    }

/** Generates a random AES-256 DEK for use only in memory. */
fun generateDek(): ByteArray = ByteArray(DEK_LENGTH_BYTES).also(SecureRandom()::nextBytes)

/** Encrypts a card with a fresh random 12-byte AES-GCM IV. */
fun encryptCardPayload(dek: ByteArray, payload: CardPayload): EncryptedCardPayload {
    val iv = ByteArray(CARD_IV_LENGTH_BYTES).also(SecureRandom()::nextBytes)
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
        init(Cipher.ENCRYPT_MODE, dek.asAesKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
    }
    return EncryptedCardPayload(iv, cipher.doFinal(payload.toJson().encodeToByteArray()))
}

/** Decrypts a card using its persisted AES-GCM IV. */
fun decryptCardPayload(dek: ByteArray, iv: ByteArray, ciphertext: ByteArray): CardPayload {
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
        init(Cipher.DECRYPT_MODE, dek.asAesKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
    }
    return cardPayloadFromJson(cipher.doFinal(ciphertext).decodeToString())
}

private fun ByteArray.asAesKey(): SecretKey = SecretKeySpec(this, KeyProperties.KEY_ALGORITHM_AES)
