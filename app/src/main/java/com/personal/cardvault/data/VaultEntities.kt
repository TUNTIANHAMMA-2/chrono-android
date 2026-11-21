package com.personal.cardvault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payment_cards")
data class PaymentCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

@Entity(tableName = "vault_meta")
data class VaultMetaEntity(
    @PrimaryKey val id: Int = VAULT_META_ID,
    val wrappedDek: ByteArray,
    val wrapIv: ByteArray,
) {
    companion object {
        const val VAULT_META_ID = 1
    }
}
