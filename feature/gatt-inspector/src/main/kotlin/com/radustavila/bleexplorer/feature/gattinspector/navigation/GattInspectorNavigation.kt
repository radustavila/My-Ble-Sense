package com.radustavila.bleexplorer.feature.gattinspector.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.radustavila.bleexplorer.feature.gattinspector.GattInspectorRoute
import kotlinx.serialization.Serializable

/** Type safe route of the GATT inspector, carrying the id of the connected device. */
@Serializable
data class GattInspectorRouteKey(val deviceId: String)

/**
 * Navigation stores the arguments of a type safe route under the property names of the route
 * class. Reading the id through that key — instead of `SavedStateHandle.toRoute()` — keeps the
 * ViewModel testable as plain Kotlin, without the Android framework classes `toRoute` needs.
 */
internal fun SavedStateHandle.gattInspectorDeviceId(): String =
    checkNotNull(get<String>(DEVICE_ID_ARG)) { "GATT inspector route is missing $DEVICE_ID_ARG" }

internal const val DEVICE_ID_ARG = "deviceId"

fun NavGraphBuilder.gattInspectorScreen(onBackClick: () -> Unit) {
    composable<GattInspectorRouteKey> {
        GattInspectorRoute(onBackClick = onBackClick)
    }
}
