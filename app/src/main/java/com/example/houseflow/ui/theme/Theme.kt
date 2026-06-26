package com.example.houseflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueSoft,
    onPrimaryContainer = Ink,
    secondary = Emerald,
    onSecondary = Color.White,
    secondaryContainer = EmeraldSoft,
    onSecondaryContainer = Ink,
    tertiary = Violet,
    onTertiary = Color.White,
    tertiaryContainer = VioletSoft,
    onTertiaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Canvas,
    onSurface = Ink,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Canvas,
    surfaceContainerLow = SurfaceSoft,
    surfaceContainer = SurfaceSoft,
    surfaceContainerHigh = SurfaceCard,
    surfaceContainerHighest = SurfaceCard,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedSoft,
    onErrorContainer = Ink,
    outline = Hairline,
    outlineVariant = HairlineSoft
)

@Composable
fun HouseFlowTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
