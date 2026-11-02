package dev.tvnclr.slap.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.tvnclr.slap.R
import dev.tvnclr.slap.engine.PackStore
import dev.tvnclr.ui.TvDivider
import dev.tvnclr.ui.TvNote
import dev.tvnclr.ui.TvPage
import dev.tvnclr.ui.TvRow
import dev.tvnclr.ui.TvSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads the pack list again every time the screen comes back to the front. */
@Composable
fun rememberPacks(): List<PackStore.Pack>? {
  val context = LocalContext.current
  var refresh by remember { mutableIntStateOf(0) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
  val packs by produceState<List<PackStore.Pack>?>(null, refresh) { value = withContext(Dispatchers.IO) { PackStore.list(context) } }
  return packs
}

@Composable
fun packSubtitle(p: PackStore.Pack): String {
  val n = p.stickers.size
  val kind = stringResource(if (p.animated) R.string.pack_kind_animated else R.string.pack_kind_static)
  val count = pluralStringResource(R.plurals.sticker_count, n, n)
  val base = "$kind · $count"
  return if (n < PackStore.MIN_STICKERS) {
    val missing = PackStore.MIN_STICKERS - n
    "$base · " + pluralStringResource(R.plurals.stickers_missing, missing, missing)
  } else base
}

@Composable
fun HomeScreen(onOpenPack: (String) -> Unit, onSettings: () -> Unit, onAbout: () -> Unit) {
  val context = LocalContext.current
  val packs = rememberPacks()
  var creating by remember { mutableStateOf(false) }

  TvPage(title = stringResource(R.string.app_name), subtitle = stringResource(R.string.home_subtitle), hero = true) {
    TvSection(stringResource(R.string.home_packs), Icons.Rounded.Folder) {
      packs.orEmpty().forEach { p ->
        TvRow(
            title = p.name,
            subtitle = packSubtitle(p),
            onClick = { onOpenPack(p.id) },
            leading = { TrayImage(p.file(PackStore.TRAY_FILE), size = 40.dp) },
        )
        TvDivider()
      }
      TvRow(
          title = stringResource(R.string.home_new_pack),
          subtitle = stringResource(if (packs.isNullOrEmpty()) R.string.home_new_pack_first else R.string.home_new_pack_sub),
          icon = Icons.Rounded.Add,
          onClick = { creating = true },
      )
    }

    TvSection(stringResource(R.string.home_more), Icons.Rounded.MoreHoriz) {
      TvRow(stringResource(R.string.home_settings), stringResource(R.string.home_settings_desc), Icons.Rounded.Palette, onClick = onSettings)
      TvDivider()
      TvRow(stringResource(R.string.home_about), stringResource(R.string.home_about_desc), Icons.Rounded.Info, onClick = onAbout)
    }
    TvNote(stringResource(R.string.home_share_tip))
  }

  if (creating) {
    NewPackDialog(
        animatedDefault = false,
        onCreate = { name, animated ->
          creating = false
          runCatching { PackStore.create(context, name, animated) }
              .onSuccess { onOpenPack(it.id) }
              .onFailure { context.toast(context.getString(R.string.error_generic, it.message)) }
        },
        onDismiss = { creating = false },
    )
  }
}
