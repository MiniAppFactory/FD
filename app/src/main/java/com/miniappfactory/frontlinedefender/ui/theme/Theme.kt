package com.miniappfactory.frontlinedefender.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SleekColorScheme =
  darkColorScheme(
    primary = SleekPrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = SleekSurfaceCard,
    onPrimaryContainer = SleekTextAccent,
    secondary = SleekGold,
    onSecondary = Color.Black,
    surface = SleekSurfaceCard,
    onSurface = SleekTextAccent,
    background = SleekDarkBg,
    onBackground = SleekTextAccent,
    surfaceVariant = SleekSurfaceHeader,
    error = SleekRed,
    onError = SleekRedText
  )

@Composable
fun FrontlineDefenderTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = SleekColorScheme,
    typography = Typography,
    content = content
  )
}


