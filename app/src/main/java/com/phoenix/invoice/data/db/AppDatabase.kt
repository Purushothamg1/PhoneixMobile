package com.phoenix.invoice.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.phoenix.invoice.data.db.dao.*
import com.phoenix.invoice.data.db.entities.*

@Database(
    entities = [CompanyProfile::class, Customer::class, Product::class,
                Invoice::class, InvoiceItem::class],
    version  = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun companyDao(): CompanyDao
    abstract fun customerDao(): CustomerDao
    abstract fun productDao(): ProductDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun invoiceItemDao(): InvoiceItemDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "phoenix.db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
