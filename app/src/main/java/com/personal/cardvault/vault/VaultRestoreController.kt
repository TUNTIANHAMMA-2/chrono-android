package com.personal.cardvault.vault

import android.app.KeyguardManager
import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.UnrecoverableKeyException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.personal.cardvault.backup.BackupCard
import com.personal.cardvault.backup.toCardPayload
import com.personal.cardvault.data.PaymentCardEntity
import com.personal.cardvault.data.VaultMetaEntity
import java.util.concurrent.Executor

/**
 * Prepares replacement vault records for a backup restore without changing the database.
 *
 * The caller commits the returned records atomically with the restored expiry items. The fresh
 * DEK is wiped as soon as the encrypted records have been prepared.
 */
class VaultRestoreController(
    private val activity: FragmentActivity,
    private val executor: Executor,
    private val keyStore: VaultKeyStore = VaultKeyStore(),
    private val onPrepared: (List<PaymentCardEntity>, VaultMetaEntity) -> Unit,
    private val onNoSecureScreenLock: () -> Unit,
    private val onCancelledOrFailed: () -> Unit,
) {
    private var busy = false

    fun prepare(cards: List<BackupCard>) {
        if (busy) return
        if (activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure != true) {
            onNoSecureScreenLock()
            return
        }
        busy = true
        val kek = try {
            keyStore.getExistingKek() ?: keyStore.getOrCreateKek()
        } catch (_: UnrecoverableKeyException) {
            // An unreadable KEK cannot decrypt the replaced vault, so create a new one.
            try {
                keyStore.recreateKek()
            } catch (_: Exception) {
                finishFailure()
                return
            }
        } catch (_: Exception) {
            finishFailure()
            return
        }
        val cipher = try {
            prepareKekEncryptionCipher(kek)
        } catch (_: KeyPermanentlyInvalidatedException) {
            try {
                prepareKekEncryptionCipher(keyStore.recreateKek())
            } catch (_: Exception) {
                finishFailure()
                return
            }
        } catch (_: Exception) {
            finishFailure()
            return
        }
        authenticate(cipher, cards)
    }

    private fun authenticate(cipher: javax.crypto.Cipher, cards: List<BackupCard>) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("恢复保险箱")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (!busy) return
                val authenticatedCipher = result.cryptoObject?.cipher ?: run {
                    finishFailure()
                    return
                }
                val dek = generateDek()
                try {
                    val encryptedCards = cards.map { card ->
                        val encrypted = encryptCardPayload(dek, card.toCardPayload())
                        PaymentCardEntity(iv = encrypted.iv, ciphertext = encrypted.ciphertext)
                    }
                    val meta = VaultMetaEntity(
                        wrappedDek = authenticatedCipher.doFinal(dek),
                        wrapIv = authenticatedCipher.iv,
                    )
                    busy = false
                    onPrepared(encryptedCards, meta)
                } catch (_: Exception) {
                    finishFailure()
                } finally {
                    dek.fill(0)
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                finishFailure()
            }
        })
        try {
            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) {
            finishFailure()
        }
    }

    private fun finishFailure() {
        if (!busy) return
        busy = false
        onCancelledOrFailed()
    }
}
