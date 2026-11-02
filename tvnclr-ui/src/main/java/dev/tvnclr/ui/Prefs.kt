package dev.tvnclr.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class TvThemeSettings(
    val mode: TvThemeMode = TvThemeMode.SYSTEM,
    val accent: TvAccent = TvAccent.default,
    val background: TvBackgroundStyle = TvBackgroundStyle.ORBS,
)

private val Context.tvnclrStore by preferencesDataStore(name = "tvnclr_ui")

/**
 * Appearance preferences, stored apart from the app's own settings. [defaults] lets each app pick
 * its own starting accent (every app in the family has one) while sharing the same storage keys.
 */
object TvThemePrefs {
  private val MODE = stringPreferencesKey("theme_mode")
  private val ACCENT = stringPreferencesKey("accent")
  private val BACKGROUND = stringPreferencesKey("background")

  fun flow(context: Context, defaults: TvThemeSettings = TvThemeSettings()): Flow<TvThemeSettings> =
      context.applicationContext.tvnclrStore.data.map { p ->
        TvThemeSettings(
            mode = p[MODE]?.let { runCatching { TvThemeMode.valueOf(it) }.getOrNull() } ?: defaults.mode,
            accent = p[ACCENT]?.let { runCatching { TvAccent.valueOf(it) }.getOrNull() } ?: defaults.accent,
            background = p[BACKGROUND]?.let { runCatching { TvBackgroundStyle.valueOf(it) }.getOrNull() } ?: defaults.background,
        )
      }

  suspend fun setMode(context: Context, mode: TvThemeMode) {
    context.applicationContext.tvnclrStore.edit { it[MODE] = mode.name }
  }

  suspend fun setAccent(context: Context, accent: TvAccent) {
    context.applicationContext.tvnclrStore.edit { it[ACCENT] = accent.name }
  }

  suspend fun setBackground(context: Context, style: TvBackgroundStyle) {
    context.applicationContext.tvnclrStore.edit { it[BACKGROUND] = style.name }
  }
}
