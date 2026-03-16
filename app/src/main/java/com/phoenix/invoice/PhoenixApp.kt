package com.phoenix.invoice

import android.app.Application
import com.phoenix.invoice.data.db.AppDatabase
import com.phoenix.invoice.data.repository.Repository

class PhoenixApp : Application() {
    val db   by lazy { AppDatabase.get(this) }
    val repo by lazy { Repository(db) }

    companion object {
        lateinit var instance: PhoenixApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
