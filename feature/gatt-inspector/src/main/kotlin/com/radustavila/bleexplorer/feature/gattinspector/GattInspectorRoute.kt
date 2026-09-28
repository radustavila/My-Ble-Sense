package com.radustavila.bleexplorer.feature.gattinspector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful entry point of the GATT inspector.
 *
 * It intentionally does *not* connect: the connection is opened on the device details screen and
 * lives in the app-scoped GATT client, so entering this screen — or recomposing it — can never
 * start a second connection.
 */
@Composable
fun GattInspectorRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GattInspectorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GattInspectorScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
