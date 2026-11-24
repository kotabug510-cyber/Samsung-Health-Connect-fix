package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = BridgeTealDark,
    onPrimary = BridgeTealOnDark,
    primaryContainer = BridgeTealContainerDark,
    onPrimaryContainer = BridgeTealOnContainerDark,
    secondary = SamsungCobaltDark,
    onSecondary = SamsungCobaltOnDark,
    secondaryContainer = SamsungCobaltContainerDark,
    onSecondaryContainer = SamsungCobaltOnContainerDark,
    tertiary = HealthEmeraldDark,
    onTertiary = HealthEmeraldOnDark,
    tertiaryContainer = HealthEmeraldContainerDark,
    onTertiaryContainer = HealthEmeraldOnContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceContainerDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = StatusError,
    errorContainer = StatusErrorContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = BridgeTealPrimary,
    onPrimary = BridgeTealOnPrimary,
    primaryContainer = BridgeTealContainer,
    onPrimaryContainer = BridgeTealOnContainer,
    secondary = SamsungCobalt,
    onSecondary = SamsungCobaltOn,
    secondaryContainer = SamsungCobaltContainer,
    onSecondaryContainer = SamsungCobaltOnContainer,
    tertiary = HealthEmerald,
    onTertiary = HealthEmeraldOn,
    tertiaryContainer = HealthEmeraldContainer,
    onTertiaryContainer = HealthEmeraldOnContainer,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceContainerLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = StatusError,
    errorContainer = StatusErrorContainerLight
)

val HealthBridgeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HealthBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = HealthBridgeShapes,
        content = content
    )
}
