package com.radustavila.bleexplorer

import android.app.Application
import com.radustavila.bleexplorer.core.common.di.ApplicationScope
import com.radustavila.bleexplorer.core.common.log.BleLog
import com.radustavila.bleexplorer.domain.usecase.TrackKnownDeviceUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BleExplorerApplication : Application() {

    @Inject
    lateinit var trackKnownDevice: TrackKnownDeviceUseCase

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Verbose BLE logging (payloads, addresses) is limited to debug builds.
        BleLog.isDebug = BuildConfig.DEBUG
        // Remembers every device the app connects to, whichever screen opened the link.
        applicationScope.launch { trackKnownDevice() }
    }
}
