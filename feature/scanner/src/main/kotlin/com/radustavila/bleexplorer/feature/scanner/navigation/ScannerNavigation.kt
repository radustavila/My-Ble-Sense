package com.radustavila.bleexplorer.feature.scanner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.radustavila.bleexplorer.feature.scanner.ScannerRoute
import kotlinx.serialization.Serializable

/** Type safe route of the scanner screen. */
@Serializable
data object ScannerRouteKey

fun NavGraphBuilder.scannerScreen(onDeviceClick: (String) -> Unit) {
    composable<ScannerRouteKey> {
        ScannerRoute(onDeviceClick = onDeviceClick)
    }
}
