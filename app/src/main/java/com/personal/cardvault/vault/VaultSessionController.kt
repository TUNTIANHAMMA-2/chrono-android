package com.personal.cardvault.vault

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.personal.cardvault.data.PaymentCardDao
import com.personal.cardvault.data.PaymentCardEntity
import com.personal.cardvault.data.VaultMetaDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import javax.crypto.Cipher

private const val VAULT_INACTIVITY_TIMEOUT_MILLIS = 60_000L

/** A decrypted card kept exclusively in the in-memory vault session. */
data class UnlockedPaymentCard(
    val id: Int,
    val payload: CardPayload,
)

/**
 * Owns the in-memory vault session. DEK and decrypted card payloads never enter saved state.
 *
 * An authentication prompt is deliberately not treated as leaving the application: while one is
 * pending, ProcessLifecycleOwner's ON_STOP does not lock this session.
 */
class VaultSessionController(
    private val activity: FragmentActivity,
    private val vaultMetaDao: VaultMetaDao,
    private val paymentCardDao: PaymentCardDao,
    private val keyStore: VaultKeyStore = VaultKeyStore(),
    private val executor: Executor,
    private val onUnlocked: (List<UnlockedPaymentCard>) -> Unit,
    private val onLocked: () -> Unit,
    private val onBroken: () -> Unit,
) : DefaultLifecycleObserver {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val lockForInactivity = Runnable { lock() }
    private var dek: ByteArray? = null
    private var cards: List<UnlockedPaymentCard>? = null
    private var authenticationInProgress = false
    private var requestGeneration = 0
    private var pendingExport: ((List<UnlockedPaymentCard>?) -> Unit)? = null

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!authenticationInProgress) lock()
    }

    /** Starts a session after the initial creation flow has authenticated and unwrapped a new DEK. */
    fun startCreatedSession(createdDek: ByteArray) {
        beginSession(createdDek, ++requestGeneration)
    }

    /** Reads and unwraps the persisted DEK after authenticating the exact decryption cipher. */
    fun unlock() {
        if (authenticationInProgress || dek != null) return
        authenticationInProgress = true
        val generation = ++requestGeneration
        activity.lifecycleScope.launch {
            val meta = withContext(Dispatchers.IO) { vaultMetaDao.get() }
            if (meta == null) {
                finishAuthentication()
                finishPendingExport()
                return@launch
            }
            val cipher = try {
                val kek = keyStore.getExistingKek() ?: run {
                    enterBrokenState()
                    return@launch
                }
                prepareKekDecryptionCipher(kek, meta.wrapIv)
            } catch (_: KeyPermanentlyInvalidatedException) {
                enterBrokenState()
                return@launch
            } catch (_: Exception) {
                finishAuthentication()
                finishPendingExport()
                return@launch
            }
            if (generation != requestGeneration) return@launch
            authenticate(cipher, meta.wrappedDek, generation)
        }
    }

    /** Returns the current session cards for navigation while the vault remains unlocked. */
    fun cards(): List<UnlockedPaymentCard> = cards.orEmpty()

    /**
     * Obtains decrypted cards for an export after authenticating if the vault is locked.
     * The result is null when authentication cannot complete; no card plaintext is persisted.
     */
    fun exportCards(onResult: (List<UnlockedPaymentCard>?) -> Unit) {
        cards?.let(onResult)
        if (cards != null) return
        if (authenticationInProgress) return
        pendingExport = onResult
        unlock()
    }

    /**
     * Encrypts and inserts a card, or re-encrypts an existing card with a fresh IV.
     * All persistence is refused unless this controller still owns an unlocked session.
     */
    fun saveCard(id: Int?, payload: CardPayload, onSaved: () -> Unit) {
        val sessionDek = dek ?: return
        activity.lifecycleScope.launch {
            val updatedCards = try {
                withContext(Dispatchers.IO) {
                    val encrypted = encryptCardPayload(sessionDek, payload)
                    val cardId = if (id == null) {
                        paymentCardDao.insert(
                            PaymentCardEntity(iv = encrypted.iv, ciphertext = encrypted.ciphertext),
                        ).toInt()
                    } else {
                        paymentCardDao.update(id, encrypted.iv, encrypted.ciphertext)
                        id
                    }
                    val current = cards.orEmpty().filterNot { it.id == cardId }
                    current + UnlockedPaymentCard(cardId, payload)
                }
            } catch (_: Exception) {
                return@launch
            }
            if (sessionDek !== dek) return@launch
            cards = updatedCards
            resetInactivityTimer()
            onUnlocked(updatedCards)
            onSaved()
        }
    }

    /** Deletes a card only while this controller owns an unlocked session. */
    fun deleteCard(id: Int, onDeleted: () -> Unit) {
        val sessionDek = dek ?: return
        activity.lifecycleScope.launch {
            val updatedCards = try {
                withContext(Dispatchers.IO) {
                    paymentCardDao.deleteById(id)
                    cards.orEmpty().filterNot { it.id == id }
                }
            } catch (_: Exception) {
                return@launch
            }
            if (sessionDek !== dek) return@launch
            cards = updatedCards
            resetInactivityTimer()
            onUnlocked(updatedCards)
            onDeleted()
        }
    }

    /** Restarts the 60-second inactivity timeout after an Activity user interaction. */
    fun resetInactivityTimer() {
        if (dek == null) return
        mainHandler.removeCallbacks(lockForInactivity)
        mainHandler.postDelayed(lockForInactivity, VAULT_INACTIVITY_TIMEOUT_MILLIS)
    }

    /** Clears every in-memory secret and returns the UI to its locked state. */
    fun lock() {
        requestGeneration += 1
        authenticationInProgress = false
        mainHandler.removeCallbacks(lockForInactivity)
        dek?.fill(0)
        dek = null
        cards = null
        pendingExport?.let { callback ->
            pendingExport = null
            callback(null)
        }
        onLocked()
    }

    private fun authenticate(cipher: Cipher, wrappedDek: ByteArray, generation: Int) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁保险箱")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (generation != requestGeneration) return
                val authenticatedCipher = result.cryptoObject?.cipher
                val unlockedDek = try {
                    authenticatedCipher?.doFinal(wrappedDek)
                } catch (_: Exception) {
                    null
                }
                authenticationInProgress = false
                if (unlockedDek == null) {
                    enterBrokenState()
                    return
                }
                beginSession(unlockedDek, generation)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (generation == requestGeneration) {
                    authenticationInProgress = false
                    pendingExport?.let { callback ->
                        pendingExport = null
                        callback(null)
                    }
                }
            }

            override fun onAuthenticationFailed() {
                // A non-match is non-terminal; the system prompt can still authenticate.
            }
        })
        try {
            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) {
            finishAuthentication()
        }
    }

    private fun beginSession(unlockedDek: ByteArray, generation: Int) {
        activity.lifecycleScope.launch {
            val decryptedCards = try {
                withContext(Dispatchers.IO) {
                    paymentCardDao.getAll().map { card ->
                        UnlockedPaymentCard(card.id, decryptCardPayload(unlockedDek, card.iv, card.ciphertext))
                    }
                }
            } catch (_: Exception) {
                unlockedDek.fill(0)
                return@launch
            }
            if (generation != requestGeneration) {
                unlockedDek.fill(0)
                return@launch
            }
            dek = unlockedDek
            cards = decryptedCards
            resetInactivityTimer()
            onUnlocked(decryptedCards)
            pendingExport?.let { callback ->
                pendingExport = null
                callback(decryptedCards)
            }
        }
    }

    private fun finishAuthentication() {
        authenticationInProgress = false
    }

    private fun finishPendingExport() {
        pendingExport?.let { callback ->
            pendingExport = null
            callback(null)
        }
    }

    private fun enterBrokenState() {
        lock()
        onBroken()
    }
}
