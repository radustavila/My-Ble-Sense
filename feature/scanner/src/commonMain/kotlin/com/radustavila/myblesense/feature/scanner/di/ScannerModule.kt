package com.radustavila.myblesense.feature.scanner.di

import com.radustavila.myblesense.core.common.di.ApplicationScope
import com.radustavila.myblesense.feature.scanner.ScannerViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val scannerModule: Module = module {
    viewModel {
        ScannerViewModel(
            startBleScan = get(),
            observeBluetoothState = get(),
            getBleCapability = get(),
            clearScanSession = get(),
            sortDevices = get(),
            scanSettings = get(),
            observeKnownDevice = get(),
            observeGattConnection = get(),
            connectToDevice = get(),
            disconnectFromDevice = get(),
            forgetKnownDevice = get(),
            applicationScope = get(ApplicationScope),
        )
    }
}
