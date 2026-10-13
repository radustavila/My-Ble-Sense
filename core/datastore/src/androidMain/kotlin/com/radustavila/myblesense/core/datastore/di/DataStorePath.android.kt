package com.radustavila.myblesense.core.datastore.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope

// Same location as `Context.preferencesDataStoreFile("known_device")`, so a device remembered
// before the multiplatform migration is still known after an update.
internal actual fun Scope.knownDeviceDataStorePath(): String =
    androidContext().filesDir.resolve("datastore/$KNOWN_DEVICE_FILE").absolutePath
