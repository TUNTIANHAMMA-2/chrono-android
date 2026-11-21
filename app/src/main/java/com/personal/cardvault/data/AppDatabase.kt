package com.personal.cardvault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction

@Database(
    entities = [ExpiryItemEntity::class, PaymentCardEntity::class, VaultMetaEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(AppDatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expiryItemDao(): ExpiryItemDao
    abstract fun paymentCardDao(): PaymentCardDao
    abstract fun vaultMetaDao(): VaultMetaDao

    /** Replaces all backup-covered records as one Room transaction. */
    open suspend fun replaceWithBackup(
        expiryItems: List<ExpiryItemEntity>,
        paymentCards: List<PaymentCardEntity>,
        vaultMeta: VaultMetaEntity?,
    ) = withTransaction {
        paymentCardDao().deleteAll()
        vaultMetaDao().deleteAll()
        expiryItemDao().deleteAll()
        expiryItemDao().insertAll(expiryItems)
        paymentCardDao().insertAll(paymentCards)
        if (vaultMeta != null) vaultMetaDao().upsert(vaultMeta)
    }
}

@Volatile
private var cachedExpiryDatabase: AppDatabase? = null

/**
 * 进程级单例 Room 数据库实例。
 * 确保所有 ViewModel、Activity 与后台 Worker 共享同一个 InvalidationTracker，
 * 写入（新增/修改/删除/暂停/续期）后界面 Flow 立即刷新，无需重启应用。
 */
fun expiryDatabase(context: Context): AppDatabase =
    cachedExpiryDatabase ?: synchronized(AppDatabase::class.java) {
        cachedExpiryDatabase ?: Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "cardvault.db",
        ).build().also { cachedExpiryDatabase = it }
    }
