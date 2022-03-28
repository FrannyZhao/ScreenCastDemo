package com.franny.screencastdemo

import android.app.Application
import timber.log.Timber

class ScreenCastApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}