package com.radustavila.myblesense.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.radustavila.myblesense.feature.devicedetails.navigation.DeviceDetailsRouteKey
import com.radustavila.myblesense.feature.devicedetails.navigation.deviceDetailsScreen
import com.radustavila.myblesense.feature.gattinspector.navigation.GattInspectorRouteKey
import com.radustavila.myblesense.feature.gattinspector.navigation.gattInspectorScreen
import com.radustavila.myblesense.feature.scanner.navigation.ScannerRouteKey
import com.radustavila.myblesense.feature.scanner.navigation.scannerScreen

/**
 * Top level navigation. This module is the only place that knows about both features; the
 * features themselves never depend on each other.
 */
@Composable
fun MyBleSenseNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ScannerRouteKey,
        modifier = modifier,
    ) {
        scannerScreen(
            onDeviceClick = { deviceId ->
                navController.navigate(DeviceDetailsRouteKey(deviceId))
            },
        )
        deviceDetailsScreen(
            onBackClick = navController::popBackStack,
            onOpenGattInspector = { deviceId ->
                navController.navigate(GattInspectorRouteKey(deviceId))
            },
        )
        gattInspectorScreen(onBackClick = navController::popBackStack)
    }
}
