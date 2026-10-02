package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClinicalDarkColorScheme = darkColorScheme(
    primary = BioCyan,
    onPrimary = Color(0xFF002A32),
    primaryContainer = BioCyanSoft,
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = VitalEmerald,
    onSecondary = Color(0xFF00291B),
    secondaryContainer = VitalEmeraldSoft,
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = CautionAmber,
    onTertiary = Color(0xFF2E1500),
    tertiaryContainer = CautionAmberSoft,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = CriticalCrimson,
    onError = Color.White,
    errorContainer = CriticalCrimsonSoft,
    onErrorContainer = Color(0xFFFECACA),
    background = TelemetryNavyBg,
    onBackground = IceWhite,
    surface = TelemetrySurface,
    onSurface = IceWhite,
    surfaceVariant = TelemetryCard,
    onSurfaceVariant = SlateMuted,
    surfaceContainerHighest = TelemetryCardElevated,
    outline = TelemetryBorder,
    outlineVariant = SlateSubtle
)

private val ClinicalLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFDC2626),
    onError = Color.White,
    background = LightClinicalBg,
    onBackground = DeepSlateText,
    surface = LightClinicalSurface,
    onSurface = DeepSlateText,
    surfaceVariant = LightClinicalCard,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Clinical telemetry defaults to high-contrast dark mode
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) ClinicalDarkColorScheme else ClinicalLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
