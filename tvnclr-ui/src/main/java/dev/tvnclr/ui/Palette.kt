package dev.tvnclr.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

enum class TvThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/** Accent options. [DYNAMIC] follows the wallpaper (Material You, Android 12+). */
enum class TvAccent(val dark: Color, val light: Color) {
  VIOLET(Color(0xFFAE94FF), Color(0xFF6646D6)),
  BLUE(Color(0xFF8DB4FF), Color(0xFF2F5FD0)),
  CYAN(Color(0xFF6FD3E6), Color(0xFF00768A)),
  GREEN(Color(0xFF82D99D), Color(0xFF2B7A44)),
  AMBER(Color(0xFFF2C25A), Color(0xFF805A00)),
  ORANGE(Color(0xFFFFAB73), Color(0xFFB04F00)),
  RED(Color(0xFFFF8E86), Color(0xFFC0282D)),
  PINK(Color(0xFFFF92C9), Color(0xFFAD2E6D)),
  DYNAMIC(Color(0xFFAE94FF), Color(0xFF6646D6));

  companion object {
    val default = VIOLET
  }
}

/** Resolved colors a Tvnclr screen draws with. */
data class TvColors(
    val isDark: Boolean,
    val background: Color,
    val circles: Color,
    val card: Color,
    val cardBorder: Color,
    val elevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
)

internal fun onColorFor(c: Color): Color = if (c.luminance() > 0.45f) Color(0xFF15131C) else Color.White

internal fun neutralColors(mode: TvThemeMode, dark: Boolean, accent: Color): TvColors {
  return if (!dark) {
    TvColors(
        isDark = false,
        background = Color(0xFFF3F2F8),
        circles = Color(0xFFE7E5F0),
        card = Color(0xFFFFFFFF),
        cardBorder = Color(0xFFE0DDEA),
        elevated = Color(0xFFEFEDF6),
        textPrimary = Color(0xFF1A1823),
        textSecondary = Color(0xFF5E5A6E),
        accent = accent,
        onAccent = onColorFor(accent),
        accentSoft = accent.copy(alpha = 0.12f),
        success = Color(0xFF2B7A44),
        warning = Color(0xFF8A5A00),
        danger = Color(0xFFC0282D),
    )
  } else {
    val amoled = mode == TvThemeMode.AMOLED
    TvColors(
        isDark = true,
        background = if (amoled) Color.Black else Color(0xFF121118),
        circles = if (amoled) Color(0xFF111016) else Color(0xFF1B1A23),
        card = Color(0xFF1B1A22),
        cardBorder = Color(0xFF2C2A37),
        elevated = Color(0xFF25232F),
        textPrimary = Color(0xFFEDEBF4),
        textSecondary = Color(0xFFA6A2B6),
        accent = accent,
        onAccent = onColorFor(accent),
        accentSoft = accent.copy(alpha = 0.16f),
        success = Color(0xFF82D99D),
        warning = Color(0xFFF2C25A),
        danger = Color(0xFFFF8E86),
    )
  }
}
