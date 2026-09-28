package com.radustavila.myblesense.feature.devicedetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DeviceDetailsRoute(
    onBackClick: () -> Unit,
    onOpenGattInspector: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DeviceDetailsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBackClick = onBackClick,
        onOpenGattInspector = onOpenGattInspector,
        modifier = modifier,
    )
}
