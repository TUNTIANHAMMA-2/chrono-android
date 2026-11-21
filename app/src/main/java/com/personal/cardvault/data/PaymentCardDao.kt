package com.personal.cardvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PaymentCardDao {
    @Insert
    suspend fun insert(card: PaymentCardEntity): Long

    @Query("SELECT * FROM payment_cards")
    suspend fun getAll(): List<PaymentCardEntity>

    @Insert
    suspend fun insertAll(cards: List<PaymentCardEntity>)

    @Query("DELETE FROM payment_cards")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM payment_cards WHERE id = :id")
    suspend fun findById(id: Int): PaymentCardEntity?

    @Query("UPDATE payment_cards SET iv = :iv, ciphertext = :ciphertext WHERE id = :id")
    suspend fun update(id: Int, iv: ByteArray, ciphertext: ByteArray): Int

    @Query("DELETE FROM payment_cards WHERE id = :id")
    suspend fun deleteById(id: Int): Int
}
