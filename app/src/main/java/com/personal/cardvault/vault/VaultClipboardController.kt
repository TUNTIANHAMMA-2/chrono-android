package com.personal.cardvault.vault

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

private const val VAULT_CLIP_LABEL = "cardvault"

/**
 * Copies vault values as sensitive clips and clears only this app's clip after a foreground return.
 * Clipboard contents are never read: only the clip description's label is inspected.
 */
class VaultClipboardController(context: Context) : DefaultLifecycleObserver {
    private val clipboard = context.applicationContext.getSystemService(ClipboardManager::class.java)
    private var clearOwnClipOnNextWindowFocus = true

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        clearOwnClipOnNextWindowFocus = true
    }

    fun copy(text: String) {
        val clip = ClipData.newPlainText(VAULT_CLIP_LABEL, text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
    }

    /** Call from the Activity's focus callback after the app has returned from the background. */
    fun clearOwnClipAfterForegroundReturn() {
        if (!clearOwnClipOnNextWindowFocus) return
        clearOwnClipOnNextWindowFocus = false
        if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.label == VAULT_CLIP_LABEL) {
            clipboard.clearPrimaryClip()
        }
    }
}
