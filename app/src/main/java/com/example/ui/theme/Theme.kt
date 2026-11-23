package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val KairoDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = EmeraldGlow,
    secondary = CyanSecondary,
    onSecondary = Color(0xFF082F49),
    secondaryContainer = CyanDarkContainer,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AmberWarning,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = AmberDarkContainer,
    onTertiaryContainer = Color(0xFFFDE68A),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = MidnightSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateCard,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = MidnightSurface,
    surfaceContainer = SlateCard,
    surfaceContainerHigh = SlateElevated,
    outline = SlateBorder,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralDarkContainer,
    onErrorContainer = Color(0xFFFECDD3)
)

private val KairoLightColorScheme = lightColorScheme(
    primary = EmeraldLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF065F46),
    secondary = CyanLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF075985),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF92400E),
    background = PearlBg,
    onBackground = TextPrimaryLight,
    surface = PureSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = SoftSlateCard,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = PureSurface,
    surfaceContainer = SoftSlateCard,
    surfaceContainerHigh = Color(0xFFDDE6F2),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF9F1239)
)

val KairoShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KairoIaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KairoDarkColorScheme else KairoLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = KairoShapes,
        content = content
    )
}
