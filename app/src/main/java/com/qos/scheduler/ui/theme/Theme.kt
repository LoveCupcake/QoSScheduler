package com.qos.scheduler.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Cyber-Space Dark Colour Scheme (always dark — space has no "light mode") ─
private val CyberDarkColorScheme = darkColorScheme(
    primary              = NeonCyan,
    onPrimary            = VoidBlack,
    primaryContainer     = NeonCyanGlow,
    onPrimaryContainer   = NeonCyan,

    secondary            = ElectricPurple,
    onSecondary          = StarWhite,
    secondaryContainer   = ElectricPurpleGlow,
    onSecondaryContainer = ElectricPurple,

    tertiary             = MatrixGreen,
    onTertiary           = VoidBlack,
    tertiaryContainer    = Color(0xFF003320),
    onTertiaryContainer  = MatrixGreen,

    error                = CyberPink,
    onError              = StarWhite,
    errorContainer       = Color(0xFF3D0018),
    onErrorContainer     = CyberPink,

    background           = VoidBlack,
    onBackground         = StarWhite,

    surface              = SurfaceDark,
    onSurface            = StarWhite,
    surfaceVariant       = SurfaceCard,
    onSurfaceVariant     = StarGrey,

    outline              = StarDim,
    outlineVariant       = Color(0xFF2A3548),

    inverseSurface       = StarWhite,
    inverseOnSurface     = VoidBlack,
    inversePrimary       = NeonCyanDim,

    surfaceTint          = NeonCyan
)

@Composable
fun QoSSchedulerTheme(
    content: @Composable () -> Unit
) {
    // Force edge-to-edge so the status bar blends into the space background
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = VoidBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = CyberDarkColorScheme,
        typography  = Typography,
        content     = content
    )
}