package com.radustavila.myblesense.core.bluetooth.di

import com.radustavila.myblesense.core.bluetooth.repository.DefaultBleGattRepository
import com.radustavila.myblesense.core.bluetooth.repository.DefaultBleScanRepository
import com.radustavila.myblesense.core.common.di.IoDispatcher
import com.radustavila.myblesense.domain.model.BleScanMode
import com.radustavila.myblesense.domain.model.BleScanSettings
import com.radustavila.myblesense.domain.repository.BleGattRepository
import com.radustavila.myblesense.domain.repository.BleScanRepository
import com.radustavila.myblesense.domain.session.InMemoryScanSessionStore
import com.radustavila.myblesense.domain.session.ScanSessionStore
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds

/**
 * Binds the domain abstractions to their implementations. The platform half
 * ([bluetoothPlatformModule]) provides the scanner, the GATT client, the adapter state monitor
 * and the permission checker — the only classes that touch Android Bluetooth or CoreBluetooth.
 */
val bluetoothModule: Module = module {
    includes(bluetoothPlatformModule)

    single<BleScanRepository> { DefaultBleScanRepository(get(), get(), get(), get(IoDispatcher)) }
    single<BleGattRepository> { DefaultBleGattRepository(get(), get()) }
    single<ScanSessionStore> { InMemoryScanSessionStore() }

    // Single place where the scan policy is configured, so the duration can be tuned (or
    // overridden in tests) without touching the presentation layer.
    factory { BleScanSettings(duration = SCAN_DURATION_SECONDS.seconds, mode = BleScanMode.BALANCED) }
}

internal expect val bluetoothPlatformModule: Module

private const val SCAN_DURATION_SECONDS = 15
