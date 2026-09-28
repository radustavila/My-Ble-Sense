package com.radustavila.myblesense.core.datastore

import javax.inject.Qualifier

/** The Preferences DataStore holding the known device, and nothing else. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class KnownDevicePreferences
