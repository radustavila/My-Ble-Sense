package com.radustavila.myblesense

import androidx.compose.ui.window.ComposeUIViewController
import com.radustavila.myblesense.di.startApp
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi

/** Entry points of the iOS shell (`iosApp/`): called from Swift as `MainViewControllerKt`. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }

/** Starts the dependency graph; called once from the SwiftUI `App` initializer. */
@OptIn(ExperimentalNativeApi::class)
fun startIosApp() {
    startApp(isDebug = Platform.isDebugBinary)
}
