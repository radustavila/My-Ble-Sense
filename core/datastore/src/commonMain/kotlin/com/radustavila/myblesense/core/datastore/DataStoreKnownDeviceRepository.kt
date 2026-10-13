package com.radustavila.myblesense.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.radustavila.myblesense.core.common.log.BleLog
import com.radustavila.myblesense.domain.model.KnownDevice
import com.radustavila.myblesense.domain.repository.KnownDeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.datastore.core.IOException

/**
 * [KnownDeviceRepository] over a Preferences DataStore holding two plain strings: the device
 * identifier and its name. Nothing else is written — in particular no Android Bluetooth object,
 * which would be meaningless in the next process anyway.
 *
 * A file that cannot be read is treated as "no known device" rather than as a failure: the device
 * list must open regardless, and the next successful connection writes a fresh entry.
 */
class DataStoreKnownDeviceRepository(
    private val dataStore: DataStore<Preferences>,
) : KnownDeviceRepository {

    override val knownDevice: Flow<KnownDevice?> = dataStore.data
        .catch { throwable ->
            if (throwable !is IOException) throw throwable
            BleLog.w("Known device could not be read", throwable)
            emit(emptyPreferences())
        }
        .map { preferences ->
            preferences[DEVICE_ID]
                ?.takeIf { it.isNotBlank() }
                ?.let { id -> KnownDevice(id = id, name = preferences[DEVICE_NAME]) }
        }
        .distinctUntilChanged()

    override suspend fun save(device: KnownDevice): Result<Unit> = try {
        dataStore.edit { preferences ->
            preferences[DEVICE_ID] = device.id
            // Replacing the device must not leave the previous device's name behind.
            val name = device.name
            if (name != null) preferences[DEVICE_NAME] = name else preferences.remove(DEVICE_NAME)
        }
        Result.success(Unit)
    } catch (e: IOException) {
        BleLog.w("Known device could not be saved", e)
        Result.failure(e)
    }

    override suspend fun clear(): Result<Unit> = try {
        dataStore.edit { preferences ->
            preferences.remove(DEVICE_ID)
            preferences.remove(DEVICE_NAME)
        }
        Result.success(Unit)
    } catch (e: IOException) {
        BleLog.w("Known device could not be forgotten", e)
        Result.failure(e)
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("known_device_id")
        val DEVICE_NAME = stringPreferencesKey("known_device_name")
    }
}
