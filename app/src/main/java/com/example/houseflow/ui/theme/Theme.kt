package com.example.houseflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = Color.White,
    primaryContainer = BrandIndigoSoft,
    onPrimaryContainer = Ink,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = TealSoft,
    onSecondaryContainer = Ink,
    tertiary = Mauve,
    onTertiary = Color.White,
    tertiaryContainer = MauveSoft,
    onTertiaryContainer = Ink,
    background = AppBackground,
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
    error = Coral,
    onError = Color.White,
    errorContainer = CoralSoft,
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
