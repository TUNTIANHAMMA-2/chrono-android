package com.personal.cardvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface VaultMetaDao {
    @Query("SELECT * FROM vault_meta WHERE id = :id")
    suspend fun get(id: Int = VaultMetaEntity.VAULT_META_ID): VaultMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: VaultMetaEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(meta: VaultMetaEntity): Long

    @Query("DELETE FROM vault_meta")
    suspend fun deleteAll(): Int

    /** Removes all encrypted vault records together; expiry data is deliberately untouched. */
    @Transaction
    suspend fun resetVault() {
        deleteAllPaymentCards()
        deleteAll()
    }

    @Query("DELETE FROM payment_cards")
    suspend fun deleteAllPaymentCards(): Int
}
