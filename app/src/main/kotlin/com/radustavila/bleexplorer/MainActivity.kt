package com.radustavila.bleexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.radustavila.bleexplorer.core.designsystem.theme.BleExplorerTheme
import com.radustavila.bleexplorer.navigation.BleExplorerNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BleExplorerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BleExplorerNavHost()
                }
            }
        }
    }
}
