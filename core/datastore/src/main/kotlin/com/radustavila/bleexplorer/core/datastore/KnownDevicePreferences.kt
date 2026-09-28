package com.radustavila.bleexplorer.core.datastore

import javax.inject.Qualifier

/** The Preferences DataStore holding the known device, and nothing else. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class KnownDevicePreferences
