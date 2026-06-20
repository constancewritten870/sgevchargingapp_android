package com.alfredang.sgevcharging.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette mirroring the iOS Theme.swift.
val BrandPrimary = Color(0xFF057A5C)   // 0.02, 0.48, 0.36
val BrandSecondary = Color(0xFF1F57A1) // 0.12, 0.34, 0.62
val BrandHighlight = Color(0xFFF5AB2E) // 0.96, 0.67, 0.18
val AvailableGreen = Color(0xFF2E9E4F)
val OccupiedOrange = Color(0xFFE08A1E)
val UnavailableGray = Color(0xFF9E9E9E)

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    secondary = BrandSecondary,
    tertiary = BrandHighlight,
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimary,
    secondary = BrandSecondary,
    tertiary = BrandHighlight,
)

@Composable
fun SGEVChargingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
