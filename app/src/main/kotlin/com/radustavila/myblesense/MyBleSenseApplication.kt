package com.radustavila.myblesense

import android.app.Application
import com.radustavila.myblesense.di.startApp
import org.koin.android.ext.koin.androidContext

class MyBleSenseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Verbose BLE logging (payloads, addresses) is limited to debug builds.
        startApp(isDebug = BuildConfig.DEBUG) {
            androidContext(this@MyBleSenseApplication)
        }
    }
}
