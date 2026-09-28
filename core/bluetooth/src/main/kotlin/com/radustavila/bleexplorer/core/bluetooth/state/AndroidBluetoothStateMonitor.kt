package com.radustavila.bleexplorer.core.bluetooth.state

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.radustavila.bleexplorer.core.common.log.BleLog
import com.radustavila.bleexplorer.domain.model.BleCapability
import com.radustavila.bleexplorer.domain.model.BluetoothState
import com.radustavila.bleexplorer.domain.repository.BluetoothStateMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports hardware capabilities and streams adapter state changes.
 *
 * The broadcast receiver is registered per collector and unregistered in `awaitClose`, so it is
 * tied to the lifetime of the collecting coroutine.
 */
@Singleton
class AndroidBluetoothStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : BluetoothStateMonitor {

    private val bluetoothManager: BluetoothManager? =
        ContextCompat.getSystemService(context, BluetoothManager::class.java)

    internal val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    override fun capability(): BleCapability = BleCapability(
        hasBluetooth = adapter != null,
        hasBluetoothLowEnergy = context.packageManager
            .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
    )

    override fun currentState(): BluetoothState = adapter.toDomainState()

    override fun observeState(): Flow<BluetoothState> = callbackFlow {
        trySend(currentState())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                val state = intent
                    .getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    .toDomainState()
                BleLog.i("Bluetooth state changed: $state")
                trySend(state)
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}

internal fun BluetoothAdapter?.toDomainState(): BluetoothState = when {
    this == null -> BluetoothState.UNKNOWN
    isEnabled -> BluetoothState.ENABLED
    else -> BluetoothState.DISABLED
}

internal fun Int.toDomainState(): BluetoothState = when (this) {
    BluetoothAdapter.STATE_ON -> BluetoothState.ENABLED
    BluetoothAdapter.STATE_TURNING_ON -> BluetoothState.TURNING_ON
    BluetoothAdapter.STATE_TURNING_OFF -> BluetoothState.TURNING_OFF
    BluetoothAdapter.STATE_OFF -> BluetoothState.DISABLED
    else -> BluetoothState.UNKNOWN
}
