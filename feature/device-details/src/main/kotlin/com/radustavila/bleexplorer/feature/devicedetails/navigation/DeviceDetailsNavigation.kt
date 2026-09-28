package com.radustavila.bleexplorer.feature.devicedetails.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.radustavila.bleexplorer.feature.devicedetails.DeviceDetailsRoute
import kotlinx.serialization.Serializable

/** Type safe route carrying the stable device id of the selected peripheral. */
@Serializable
data class DeviceDetailsRouteKey(val deviceId: String)

/**
 * Navigation stores the arguments of a type safe route under the property names of the route
 * class. Reading the id through that key — instead of `SavedStateHandle.toRoute()` — keeps the
 * ViewModel testable as plain Kotlin, without the Android framework classes `toRoute` needs.
 */
internal fun SavedStateHandle.deviceDetailsDeviceId(): String =
    checkNotNull(get<String>(DEVICE_ID_ARG)) { "Device details route is missing $DEVICE_ID_ARG" }

internal const val DEVICE_ID_ARG = "deviceId"

fun NavGraphBuilder.deviceDetailsScreen(
    onBackClick: () -> Unit,
    onOpenGattInspector: (String) -> Unit,
) {
    composable<DeviceDetailsRouteKey> {
        DeviceDetailsRoute(onBackClick = onBackClick, onOpenGattInspector = onOpenGattInspector)
    }
}
