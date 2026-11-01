package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val CutePurpleLightColorScheme = lightColorScheme(
  primary = SerenaPurplePrimary,
  onPrimary = SerenaPurpleOnPrimary,
  primaryContainer = SerenaPurplePrimaryContainer,
  onPrimaryContainer = SerenaPurpleOnPrimaryContainer,
  secondary = SerenaLilacSecondary,
  onSecondary = SerenaLilacOnSecondary,
  secondaryContainer = SerenaLilacSecondaryContainer,
  onSecondaryContainer = SerenaLilacOnSecondaryContainer,
  tertiary = SerenaSparkleTertiary,
  onTertiary = SerenaSparkleOnTertiary,
  tertiaryContainer = SerenaSparkleTertiaryContainer,
  onTertiaryContainer = SerenaSparkleOnTertiaryContainer,
  background = SerenaBackgroundLight,
  surface = SerenaSurfaceLight,
  surfaceVariant = SerenaSurfaceVariantLight,
  onBackground = SerenaOnSurfaceLight,
  onSurface = SerenaOnSurfaceLight,
  onSurfaceVariant = SerenaOnSurfaceVariantLight,
  outline = SerenaOutlineLight,
)

private val CutePurpleDarkColorScheme = darkColorScheme(
  primary = SerenaPurplePrimaryDark,
  onPrimary = SerenaPurpleOnPrimaryDark,
  primaryContainer = SerenaPurplePrimaryContainerDark,
  onPrimaryContainer = SerenaPurpleOnPrimaryContainerDark,
  secondary = SerenaLilacSecondaryDark,
  onSecondary = SerenaLilacOnSecondaryDark,
  secondaryContainer = SerenaLilacSecondaryContainerDark,
  onSecondaryContainer = SerenaLilacOnSecondaryContainerDark,
  tertiary = SerenaSparkleTertiaryDark,
  onTertiary = SerenaPurpleOnPrimaryDark,
  background = SerenaBackgroundDark,
  surface = SerenaSurfaceDark,
  surfaceVariant = SerenaSurfaceVariantDark,
  onBackground = SerenaOnSurfaceDark,
  onSurface = SerenaOnSurfaceDark,
  onSurfaceVariant = SerenaOnSurfaceVariantDark,
  outline = SerenaOutlineDark,
)

@Composable
fun SerenaTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Set to false to keep Serena's signature cute purple aesthetic
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> CutePurpleDarkColorScheme
    else -> CutePurpleLightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
