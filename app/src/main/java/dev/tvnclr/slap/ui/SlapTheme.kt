package dev.tvnclr.slap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.tvnclr.ui.TvAccent
import dev.tvnclr.ui.TvThemePrefs
import dev.tvnclr.ui.TvThemeSettings
import dev.tvnclr.ui.TvnclrTheme

/** Slap's own starting accent; the user can change it in Settings. */
val SlapDefaults = TvThemeSettings(accent = TvAccent.PINK)

@Composable
fun SlapTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val flow = remember { TvThemePrefs.flow(context, SlapDefaults) }
  val settings by flow.collectAsState(initial = SlapDefaults)
  TvnclrTheme(settings, content)
}
