package com.personal.cardvault.vault

import android.app.KeyguardManager
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.personal.cardvault.data.VaultMetaEntity
import com.personal.cardvault.data.VaultMetaDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import javax.crypto.Cipher

/**
 * Owns one first-vault creation request for an Activity.
 *
 * The successfully wrapped DEK is handed to the in-memory session; it is never persisted in
 * plaintext.
 */
class VaultCreationController(
    private val activity: FragmentActivity,
    private val vaultMetaDao: VaultMetaDao,
    private val keyStore: VaultKeyStore = VaultKeyStore(),
    private val executor: Executor,
    private val onCreated: (ByteArray) -> Unit,
    private val onNoSecureScreenLock: () -> Unit,
    private val onFailure: () -> Unit = {},
) {
    private var busy = false
    private var authenticationSucceeded = false

    fun create() {
        if (busy) return
        val keyguardManager = activity.getSystemService(KeyguardManager::class.java)
        if (keyguardManager?.isDeviceSecure != true) {
            onNoSecureScreenLock()
            return
        }
        busy = true
        authenticationSucceeded = false
        activity.lifecycleScope.launch {
            val meta = withContext(Dispatchers.IO) { vaultMetaDao.get() }
            if (meta != null) {
                finishFailure()
                return@launch
            }
            val kek = try {
                // A first creation may create an absent alias, but never replaces a present one.
                keyStore.getExistingKek() ?: keyStore.getOrCreateKek()
            } catch (_: Exception) {
                finishFailure()
                return@launch
            }
            val cipher = try {
                prepareKekEncryptionCipher(kek)
            } catch (_: KeyPermanentlyInvalidatedException) {
                try {
                    prepareKekEncryptionCipher(keyStore.recreateKek())
                } catch (_: Exception) {
                    finishFailure()
                    return@launch
                }
            } catch (_: Exception) {
                finishFailure()
                return@launch
            }
            authenticate(cipher)
        }
    }

    private fun authenticate(cipher: Cipher) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("创建保险箱")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (!busy) return
                val authenticatedCipher = result.cryptoObject?.cipher
                if (authenticatedCipher == null) {
                    finishFailure()
                    return
                }
                authenticationSucceeded = true
                val dek = generateDek()
                val wrappedDek = try {
                    authenticatedCipher.doFinal(dek)
                } catch (_: Exception) {
                    dek.fill(0)
                    finishFailure()
                    return
                }
                val wrapIv = authenticatedCipher.iv
                activity.lifecycleScope.launch {
                    val inserted = try {
                        withContext(Dispatchers.IO) {
                            vaultMetaDao.insertIfAbsent(
                                VaultMetaEntity(wrappedDek = wrappedDek, wrapIv = wrapIv),
                            )
                        }
                    } catch (_: Exception) {
                        -1L
                    }
                    if (inserted != -1L && busy) {
                        busy = false
                        onCreated(dek)
                    } else {
                        dek.fill(0)
                        finishFailure()
                    }
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!authenticationSucceeded) finishFailure()
            }

            override fun onAuthenticationFailed() {
                // A non-match is non-terminal; this prompt may still authenticate successfully.
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
        authenticationSucceeded = false
        onFailure()
    }
}
