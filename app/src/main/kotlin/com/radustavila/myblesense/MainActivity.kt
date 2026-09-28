package com.radustavila.myblesense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.radustavila.myblesense.core.designsystem.theme.MyBleSenseTheme
import com.radustavila.myblesense.navigation.MyBleSenseNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MyBleSenseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MyBleSenseNavHost()
                }
            }
        }
    }
}
