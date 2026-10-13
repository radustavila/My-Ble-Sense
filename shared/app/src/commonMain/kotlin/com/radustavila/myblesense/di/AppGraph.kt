package com.radustavila.myblesense.di

import com.radustavila.myblesense.core.bluetooth.di.bluetoothModule
import com.radustavila.myblesense.core.common.di.ApplicationScope
import com.radustavila.myblesense.core.common.di.commonModule
import com.radustavila.myblesense.core.common.log.BleLog
import com.radustavila.myblesense.core.datastore.di.dataStoreModule
import com.radustavila.myblesense.domain.usecase.TrackKnownDeviceUseCase
import com.radustavila.myblesense.feature.devicedetails.di.deviceDetailsModule
import com.radustavila.myblesense.feature.gattinspector.di.gattInspectorModule
import com.radustavila.myblesense.feature.scanner.di.scannerModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts the app's dependency graph; called once per process by each platform shell (the Android
 * `Application`, the iOS `App` initializer) before any UI is shown.
 *
 * @param isDebug enables verbose BLE logging (payloads, addresses); debug builds only.
 * @param platformDeclaration platform additions, e.g. the Android context.
 */
fun startApp(isDebug: Boolean, platformDeclaration: KoinAppDeclaration = {}): KoinApplication {
    BleLog.isDebug = isDebug

    val application = startKoin {
        platformDeclaration()
        modules(
            commonModule,
            bluetoothModule,
            dataStoreModule,
            domainModule,
            scannerModule,
            deviceDetailsModule,
            gattInspectorModule,
        )
    }

    // Remembers every device the app connects to, whichever screen opened the link.
    val koin = application.koin
    val trackKnownDevice = koin.get<TrackKnownDeviceUseCase>()
    koin.get<CoroutineScope>(ApplicationScope).launch { trackKnownDevice() }

    return application
}
