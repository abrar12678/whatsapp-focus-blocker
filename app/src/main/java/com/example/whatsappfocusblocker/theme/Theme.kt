package com.example.whatsappfocusblocker.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = FocusGreenPrimaryDark,
    onPrimary = FocusGreenOnPrimaryDark,
    primaryContainer = FocusGreenPrimaryContainerDark,
    onPrimaryContainer = FocusGreenOnPrimaryContainerDark,
    secondary = FocusGreenSecondaryDark,
    onSecondary = FocusGreenOnSecondaryDark,
    secondaryContainer = FocusGreenSecondaryContainerDark,
    onSecondaryContainer = FocusGreenOnSecondaryContainerDark,
    background = FocusGreenBackgroundDark,
    onBackground = FocusGreenOnBackgroundDark,
    surface = FocusGreenSurfaceDark,
    onSurface = FocusGreenOnSurfaceDark,
    surfaceVariant = FocusGreenSurfaceVariantDark,
    onSurfaceVariant = FocusGreenOnSurfaceVariantDark,
    outline = FocusGreenOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = FocusGreenPrimary,
    onPrimary = FocusGreenOnPrimary,
    primaryContainer = FocusGreenPrimaryContainer,
    onPrimaryContainer = FocusGreenOnPrimaryContainer,
    secondary = FocusGreenSecondary,
    onSecondary = FocusGreenOnSecondary,
    secondaryContainer = FocusGreenSecondaryContainer,
    onSecondaryContainer = FocusGreenOnSecondaryContainer,
    background = FocusGreenBackground,
    onBackground = FocusGreenOnBackground,
    surface = FocusGreenSurface,
    onSurface = FocusGreenOnSurface,
    surfaceVariant = FocusGreenSurfaceVariant,
    onSurfaceVariant = FocusGreenOnSurfaceVariant,
    outline = FocusGreenOutline
)

@Composable
fun WhatsAppFocusBlockerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
