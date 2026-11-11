package dev.tvnclr.slap.ui

import android.animation.ValueAnimator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Storage
import dev.tvnclr.slap.engine.Backup
import dev.tvnclr.ui.TvNote
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.tvnclr.slap.R
import dev.tvnclr.ui.Tv
import dev.tvnclr.ui.TvAccent
import dev.tvnclr.ui.TvBackgroundStyle
import dev.tvnclr.ui.TvCallout
import dev.tvnclr.ui.TvChoiceChips
import dev.tvnclr.ui.TvColorDots
import dev.tvnclr.ui.TvDivider
import dev.tvnclr.ui.TvPage
import dev.tvnclr.ui.TvRow
import dev.tvnclr.ui.TvSection
import dev.tvnclr.ui.TvSegmented
import dev.tvnclr.ui.TvSpace
import dev.tvnclr.ui.TvStatus
import dev.tvnclr.ui.TvThemeMode
import dev.tvnclr.ui.TvThemePrefs
import dev.tvnclr.ui.isDynamicColorAvailable
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val flow = remember { TvThemePrefs.flow(context, SlapDefaults) }
  val settings by flow.collectAsState(initial = SlapDefaults)
  val dark = Tv.colors.isDark

  val accents = TvAccent.entries.filter { it != TvAccent.DYNAMIC || isDynamicColorAvailable() }
  val accentNames =
      mapOf(
          TvAccent.VIOLET to R.string.accent_violet,
          TvAccent.BLUE to R.string.accent_blue,
          TvAccent.CYAN to R.string.accent_cyan,
          TvAccent.GREEN to R.string.accent_green,
          TvAccent.AMBER to R.string.accent_amber,
          TvAccent.ORANGE to R.string.accent_orange,
          TvAccent.RED to R.string.accent_red,
          TvAccent.PINK to R.string.accent_pink,
          TvAccent.DYNAMIC to R.string.accent_dynamic,
      )
  val modes = listOf(TvThemeMode.SYSTEM, TvThemeMode.LIGHT, TvThemeMode.DARK, TvThemeMode.AMOLED)
  val backgrounds =
      listOf(
          TvBackgroundStyle.ORBS to R.string.bg_orbs,
          TvBackgroundStyle.AURORA to R.string.bg_aurora,
          TvBackgroundStyle.HORIZON to R.string.bg_horizon,
          TvBackgroundStyle.RADAR to R.string.bg_radar,
          TvBackgroundStyle.DEPTH to R.string.bg_depth,
          TvBackgroundStyle.PLAIN to R.string.bg_none,
      )
  val animationsOn = remember { ValueAnimator.areAnimatorsEnabled() }

  val backupLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { dst ->
        if (dst == null) return@rememberLauncherForActivityResult
        scope.launch {
          val r =
              withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(dst).use { os -> Backup.write(context, requireNotNull(os) { "no destination" }) } }
              }
          r.onSuccess { context.toast(context.resources.getQuantityString(R.plurals.backup_done, it.packs, it.packs)) }
              .onFailure { context.toast(context.getString(R.string.backup_failed, it.message)) }
        }
      }
  val restoreLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { src ->
        if (src == null) return@rememberLauncherForActivityResult
        scope.launch {
          val r =
              withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(src).use { s -> Backup.restore(context, requireNotNull(s) { "no source" }) } }
              }
          r.onSuccess {
                val msg = context.resources.getQuantityString(R.plurals.restore_done, it.packs, it.packs)
                context.toast(if (it.skipped > 0) msg + " " + context.resources.getQuantityString(R.plurals.restore_skipped, it.skipped, it.skipped) else msg)
              }
              .onFailure { context.toast(context.getString(R.string.restore_failed, it.message)) }
        }
      }

  TvPage(title = stringResource(R.string.settings_title), subtitle = stringResource(R.string.settings_subtitle), onBack = onBack) {
    TvSection(stringResource(R.string.appearance), Icons.Rounded.Palette) {
      TvRow(stringResource(R.string.theme), stringResource(R.string.theme_desc), Icons.Rounded.DarkMode)
      Column(Modifier.padding(start = TvSpace.l, end = TvSpace.l, bottom = TvSpace.l)) {
        TvSegmented(
            listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark), stringResource(R.string.theme_amoled)),
            modes.indexOf(settings.mode),
            { i -> scope.launch { TvThemePrefs.setMode(context, modes[i]) } },
        )
      }
      TvDivider(inset = false)
      TvRow(stringResource(R.string.accent), stringResource(accentNames.getValue(settings.accent)), Icons.Rounded.Palette)
      TvColorDots(
          colors = accents.map { if (dark) it.dark else it.light },
          selected = accents.indexOf(settings.accent),
          onSelect = { i -> scope.launch { TvThemePrefs.setAccent(context, accents[i]) } },
          dynamicIndex = accents.indexOf(TvAccent.DYNAMIC),
          labels = accents.map { stringResource(accentNames.getValue(it)) },
      )
      TvDivider(inset = false)
      TvRow(stringResource(R.string.background_anim), stringResource(backgrounds.first { it.first == settings.background }.second), Icons.Rounded.Animation)
      Column(Modifier.padding(start = TvSpace.l, end = TvSpace.l, bottom = TvSpace.l), verticalArrangement = Arrangement.spacedBy(TvSpace.m)) {
        TvChoiceChips(
            backgrounds.map { stringResource(it.second) },
            backgrounds.indexOfFirst { it.first == settings.background },
            { i -> scope.launch { TvThemePrefs.setBackground(context, backgrounds[i].first) } },
        )
        if (!animationsOn) TvCallout(stringResource(R.string.background_anim_off), TvStatus.NEUTRAL)
      }
    }

    TvSection(stringResource(R.string.data), Icons.Rounded.Storage) {
      TvRow(stringResource(R.string.backup), stringResource(R.string.backup_desc), Icons.Rounded.Backup, onClick = { backupLauncher.launch("slap-backup-${LocalDate.now()}.zip") })
      TvDivider()
      TvRow(
          stringResource(R.string.restore),
          stringResource(R.string.restore_desc),
          Icons.Rounded.Restore,
          onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
      )
      TvNote(stringResource(R.string.backup_note))
    }
  }
}
