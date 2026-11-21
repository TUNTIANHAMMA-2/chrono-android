package com.personal.cardvault.data

import android.content.Context
import android.content.SharedPreferences

/** Synchronously readable application settings; these values are not part of backups. */
class AppSettings(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var earlyReminderEnabled: Boolean
        get() = preferences.getBoolean(KEY_EARLY_REMINDER_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_EARLY_REMINDER_ENABLED, value).apply()

    /** Whether lock-screen notification public versions must omit the item name. */
    var hideNameOnLockScreen: Boolean
        get() = preferences.getBoolean(KEY_HIDE_NAME_ON_LOCK_SCREEN, false)
        set(value) = preferences.edit().putBoolean(KEY_HIDE_NAME_ON_LOCK_SCREEN, value).apply()

    /** Whether the application uses wallpaper-derived dynamic colors (legacy mirror of themePreset == MONET). */
    var dynamicColorEnabled: Boolean
        get() = preferences.getBoolean(KEY_DYNAMIC_COLOR_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_DYNAMIC_COLOR_ENABLED, value).apply()

    /** Selected theme preset name (see AppThemePreset). Migrates cleanly from legacy dynamicColorEnabled. */
    var themePreset: String
        get() = preferences.getString(KEY_THEME_PRESET, null)
            ?: if (dynamicColorEnabled) "MONET" else "ARCHIVAL_PAPER"
        set(value) {
            preferences.edit()
                .putString(KEY_THEME_PRESET, value)
                .putBoolean(KEY_DYNAMIC_COLOR_ENABLED, value == "MONET")
                .apply()
        }

    /** Selected dark mode policy ("SYSTEM", "LIGHT", or "DARK"). */
    var themeDarkMode: String
        get() = preferences.getString(KEY_THEME_DARK_MODE, "SYSTEM") ?: "SYSTEM"
        set(value) = preferences.edit().putString(KEY_THEME_DARK_MODE, value).apply()

    /** Whether dark theme uses pure black AMOLED surfaces. */
    var amoledDarkEnabled: Boolean
        get() = preferences.getBoolean(KEY_AMOLED_DARK_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_AMOLED_DARK_ENABLED, value).apply()

    /** Custom Monet seed color ARGB int used when themePreset == "CUSTOM_SEED". */
    var customSeedArgb: Int
        get() = preferences.getInt(KEY_CUSTOM_SEED_ARGB, 0xFF3B82F6.toInt())
        set(value) = preferences.edit().putInt(KEY_CUSTOM_SEED_ARGB, value).apply()

    /** Whether FLAG_SECURE (block screenshots & hide recent-tasks preview) is enforced across the app. */
    var screenshotProtectionEnabled: Boolean
        get() = preferences.getBoolean(KEY_SCREENSHOT_PROTECTION_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_SCREENSHOT_PROTECTION_ENABLED, value).apply()

    /** Preferred vault card layout mode name ("WALLET_STACK" or "CAROUSEL"). */
    var vaultLayoutMode: String
        get() = preferences.getString(KEY_VAULT_LAYOUT_MODE, "WALLET_STACK") ?: "WALLET_STACK"
        set(value) = preferences.edit().putString(KEY_VAULT_LAYOUT_MODE, value).apply()

    /** Optional self-hosted CardFace aggregator base URL (e.g. https://cards.example.com). */
    var cardFaceServerUrl: String
        get() = preferences.getString(KEY_CARDFACE_SERVER_URL, "") ?: ""
        set(value) = preferences.edit().putString(KEY_CARDFACE_SERVER_URL, value.trim()).apply()

    /** Custom display order of vault card IDs. */
    var vaultCardOrder: List<Int>
        get() = (preferences.getString(KEY_VAULT_CARD_ORDER, "") ?: "")
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
        set(value) = preferences.edit()
            .putString(KEY_VAULT_CARD_ORDER, value.joinToString(","))
            .apply()

    /** Non-sensitive card cover metadata JSON (id/name/issuer/tail4/artFileName/network/cardType) for instant wallet stack display before biometric detail unlock. */
    var vaultCardCoversJson: String
        get() = preferences.getString(KEY_VAULT_CARD_COVERS_JSON, "") ?: ""
        set(value) = preferences.edit().putString(KEY_VAULT_CARD_COVERS_JSON, value).apply()

    private companion object {
        const val PREFERENCES_NAME = "cardvault_settings"
        const val KEY_EARLY_REMINDER_ENABLED = "early_reminder_enabled"
        const val KEY_HIDE_NAME_ON_LOCK_SCREEN = "hide_name_on_lock_screen"
        const val KEY_DYNAMIC_COLOR_ENABLED = "dynamic_color_enabled"
        const val KEY_THEME_PRESET = "theme_preset"
        const val KEY_THEME_DARK_MODE = "theme_dark_mode"
        const val KEY_AMOLED_DARK_ENABLED = "amoled_dark_enabled"
        const val KEY_CUSTOM_SEED_ARGB = "custom_seed_argb"
        const val KEY_SCREENSHOT_PROTECTION_ENABLED = "screenshot_protection_enabled"
        const val KEY_VAULT_LAYOUT_MODE = "vault_layout_mode"
        const val KEY_CARDFACE_SERVER_URL = "cardface_server_url"
        const val KEY_VAULT_CARD_ORDER = "vault_card_order"
        const val KEY_VAULT_CARD_COVERS_JSON = "vault_card_covers_json"
    }
}
