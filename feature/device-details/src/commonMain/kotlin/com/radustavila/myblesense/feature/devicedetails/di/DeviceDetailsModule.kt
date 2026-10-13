package com.radustavila.myblesense.feature.devicedetails.di

import com.radustavila.myblesense.core.common.di.ApplicationScope
import com.radustavila.myblesense.feature.devicedetails.DeviceDetailsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val deviceDetailsModule: Module = module {
    viewModel {
        DeviceDetailsViewModel(
            savedStateHandle = get(),
            observeScannedDevice = get(),
            observeKnownDevice = get(),
            observeGattConnection = get(),
            observeGattErrors = get(),
            observePocService = get(),
            observeNotifyingCharacteristics = get(),
            observePocHardwareEvents = get(),
            observeBluetoothState = get(),
            connectToDevice = get(),
            reconnectToDevice = get(),
            disconnectFromDevice = get(),
            readPocStatus = get(),
            sendPocCommand = get(),
            setPocEventsNotifications = get(),
            applicationScope = get(ApplicationScope),
        )
    }
}
