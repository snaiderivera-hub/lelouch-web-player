package com.lelouch.player

import android.app.Application
import com.lelouch.core.database.LelouchDatabase

class LelouchApplication : Application() {

    lateinit var database: LelouchDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = LelouchDatabase.getInstance(this)
    }

    companion object {
        lateinit var instance: LelouchApplication
            private set
    }
}
