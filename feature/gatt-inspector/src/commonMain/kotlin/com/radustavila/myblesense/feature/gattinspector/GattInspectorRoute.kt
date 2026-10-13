package com.radustavila.myblesense.feature.gattinspector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

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
    viewModel: GattInspectorViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GattInspectorScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
