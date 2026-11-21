package com.personal.cardvault.vault

import java.security.KeyStore
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T4.9: dev-plan.md 8.8 requires an explicit reset to delete vault_kek; 12.7 limits
 * deletion to that alias. A missing-alias check must preserve data until the user decides.
 * Uses an in-memory JVM store, not Android Keystore, device authentication, or Room.
 */
class VaultKeyStoreResetTest {
    private val password = "test-only".toCharArray()
    private val unrelatedAlias = "unrelated_test_key"
    private val unrelatedKey = ByteArray(32) { 7 }

    @Test
    fun resetDeletesVaultKekAndPreservesOtherAliases() {
        val store = newStore()
        store.setKeyEntry("vault_kek", SecretKeySpec(ByteArray(32) { 1 }, "AES"), password, null)

        VaultKeyStore(store).deleteKek()

        assertEquals(setOf(unrelatedAlias), store.aliases().toList().toSet())
        assertArrayEquals(unrelatedKey, store.getKey(unrelatedAlias, password).encoded)
    }

    @Test
    fun resetAfterAliasWasLostCompletesWithoutDeletingOtherAliases() {
        // M25 reaches reset after the debug action has already deleted the vault alias.
        val store = newStore()

        VaultKeyStore(store).deleteKek()

        assertEquals(setOf(unrelatedAlias), store.aliases().toList().toSet())
        assertArrayEquals(unrelatedKey, store.getKey(unrelatedAlias, password).encoded)
    }

    @Test
    fun checkingMissingKekDoesNotCreateOrDeleteKeys() {
        val store = newStore()

        assertNull(VaultKeyStore(store).getExistingKek())

        assertEquals(setOf(unrelatedAlias), store.aliases().toList().toSet())
        assertArrayEquals(unrelatedKey, store.getKey(unrelatedAlias, password).encoded)
    }

    private fun newStore(): KeyStore = KeyStore.getInstance("JCEKS").apply {
        load(null, password)
        setKeyEntry(unrelatedAlias, SecretKeySpec(unrelatedKey, "AES"), password, null)
    }
}
