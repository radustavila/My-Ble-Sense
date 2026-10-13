package com.radustavila.myblesense.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun MyBleSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = (if (dynamicColor) dynamicColorSchemeOrNull(darkTheme) else null)
        ?: if (darkTheme) darkColorScheme(primary = BlueSignalDark) else lightColorScheme(primary = BlueSignal)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MyBleSenseTypography,
        content = content,
    )
}

/** The wallpaper-derived scheme of Android 12+; `null` where the platform has none (iOS). */
@Composable
internal expect fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme?
