package com.radustavila.myblesense

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.radustavila.myblesense.core.designsystem.theme.MyBleSenseTheme
import com.radustavila.myblesense.navigation.MyBleSenseNavHost

/** Root of the UI on every platform. The dependency graph must already be started. */
@Composable
fun App() {
    MyBleSenseTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            MyBleSenseNavHost()
        }
    }
}
