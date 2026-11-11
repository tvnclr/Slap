package dev.tvnclr.slap.ui

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tvnclr.slap.R
import dev.tvnclr.slap.engine.PackStore
import dev.tvnclr.slap.engine.StickerMaker
import dev.tvnclr.ui.TvButton
import dev.tvnclr.ui.TvCallout
import dev.tvnclr.ui.TvDivider
import dev.tvnclr.ui.TvStatus
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import dev.tvnclr.ui.TvPage
import dev.tvnclr.ui.TvRow
import dev.tvnclr.ui.TvSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "Create sticker" from another app: choose the pack (animated packs only take GIFs and videos). */
@Composable
fun ShareScreen(uri: Uri, onPick: (String) -> Unit, onClose: () -> Unit) {
  val context = LocalContext.current
  val check by produceState<ShareCheck?>(null, uri) {
    value = withContext(Dispatchers.IO) { checkShared { StickerMaker.detectKind(context, uri) != StickerMaker.Kind.IMAGE } }
  }
  if (check == ShareCheck.UNREADABLE) {
    TvPage(title = stringResource(R.string.share_title), onBack = onClose) {
      TvCallout(stringResource(R.string.share_unreadable), TvStatus.ERROR)
      TvButton(stringResource(R.string.close), onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
    return
  }
  val moving: Boolean? = check?.let { it == ShareCheck.MOVING }
  val packs = rememberPacks()
  var creating by remember { mutableStateOf(false) }
  val canUse = { p: PackStore.Pack -> !p.animated || moving == true }

  TvPage(
      title = stringResource(R.string.share_title),
      subtitle = stringResource(R.string.share_subtitle),
      onBack = onClose,
  ) {
    TvSection(stringResource(R.string.home_packs), Icons.Rounded.Folder) {
      packs.orEmpty().filter(canUse).forEach { p ->
        TvRow(
            title = p.name,
            subtitle = packSubtitle(p),
            onClick = { onPick(p.id) },
            leading = { TrayImage(p.file(PackStore.TRAY_FILE), size = 40.dp) },
        )
        TvDivider()
      }
      TvRow(
          title = stringResource(R.string.home_new_pack),
          subtitle = stringResource(if (moving == true) R.string.share_new_pack_sub_moving else R.string.share_new_pack_sub_any),
          icon = Icons.Rounded.Add,
          onClick = { creating = true },
      )
    }
  }

  if (creating) {
    NewPackDialog(
        animatedDefault = moving == true,
        onCreate = { name, animated ->
          creating = false
          runCatching { PackStore.create(context, name, animated) }
              .onSuccess { onPick(it.id) }
              .onFailure { context.toast(context.getString(R.string.error_generic, it.message)) }
        },
        onDismiss = { creating = false },
    )
  }
}
