package dev.tvnclr.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalTvColors = staticCompositionLocalOf { neutralColors(TvThemeMode.DARK, true, TvAccent.default.dark) }

object Tv {
  val colors: TvColors
    @Composable get() = LocalTvColors.current
}

fun isDynamicColorAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun TvnclrTheme(settings: TvThemeSettings = TvThemeSettings(), content: @Composable () -> Unit) {
  val dark =
      when (settings.mode) {
        TvThemeMode.SYSTEM -> isSystemInDarkTheme()
        TvThemeMode.LIGHT -> false
        TvThemeMode.DARK, TvThemeMode.AMOLED -> true
      }
  val context = LocalContext.current
  val dynamic = settings.accent == TvAccent.DYNAMIC && isDynamicColorAvailable()

  val colors: TvColors
  val scheme: ColorScheme
  if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val d = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val amoled = settings.mode == TvThemeMode.AMOLED
    val base = neutralColors(settings.mode, dark, d.primary)
    colors =
        base.copy(
            background = if (amoled) Color.Black else d.surface,
            circles = if (amoled) Color(0xFF101014) else d.surfaceContainerLow,
            card = d.surfaceContainer,
            cardBorder = d.outlineVariant.copy(alpha = if (dark) 0.6f else 0.8f),
            elevated = d.surfaceContainerHigh,
            textPrimary = d.onSurface,
            textSecondary = d.onSurfaceVariant,
            onAccent = d.onPrimary,
            accentSoft = d.primary.copy(alpha = if (dark) 0.16f else 0.12f),
        )
    scheme = d.copy(background = colors.background)
  } else {
    val accentColor = if (dark) settings.accent.dark else settings.accent.light
    colors = neutralColors(settings.mode, dark, accentColor)
    scheme = colorSchemeFrom(colors)
  }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window ?: return@SideEffect
      val controller = WindowCompat.getInsetsController(window, view)
      controller.isAppearanceLightStatusBars = !dark
      controller.isAppearanceLightNavigationBars = !dark
    }
  }

  CompositionLocalProvider(LocalTvColors provides colors, LocalTvBackgroundStyle provides settings.background) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
  }
}

private fun colorSchemeFrom(c: TvColors): ColorScheme {
  val base = if (c.isDark) darkColorScheme() else lightColorScheme()
  return base.copy(
      primary = c.accent,
      onPrimary = c.onAccent,
      primaryContainer = c.accentSoft,
      onPrimaryContainer = c.textPrimary,
      secondary = c.accent,
      onSecondary = c.onAccent,
      secondaryContainer = c.elevated,
      onSecondaryContainer = c.textPrimary,
      tertiary = c.accent,
      background = c.background,
      onBackground = c.textPrimary,
      surface = c.background,
      onSurface = c.textPrimary,
      surfaceVariant = c.elevated,
      onSurfaceVariant = c.textSecondary,
      surfaceContainerLowest = c.card,
      surfaceContainerLow = c.card,
      surfaceContainer = c.card,
      surfaceContainerHigh = c.elevated,
      surfaceContainerHighest = c.elevated,
      surfaceBright = c.elevated,
      surfaceDim = c.background,
      outline = c.cardBorder,
      outlineVariant = c.cardBorder,
      error = c.danger,
  )
}
