package com.example.dairyflow2.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        ProductEntity::class,
        CustomerEntity::class,
        AssignmentEntity::class,
        DeliveryEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        PaymentEntity::class,
        PendingMutationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DairyDatabase : RoomDatabase() {
    abstract fun dao(): DairyDao

    companion object {
        @Volatile private var instance: DairyDatabase? = null

        fun get(context: Context): DairyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DairyDatabase::class.java,
                    "dairyflow.db",
                ).fallbackToDestructiveMigration(false).build().also { instance = it }
            }
    }
}

