package dev.tvnclr.slap.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.tvnclr.slap.R
import dev.tvnclr.slap.engine.PackStore
import dev.tvnclr.slap.engine.StickerMaker
import dev.tvnclr.slap.engine.WhatsApp
import dev.tvnclr.ui.Tv
import dev.tvnclr.ui.TvButton
import dev.tvnclr.ui.TvButtonStyle
import dev.tvnclr.ui.TvCard
import dev.tvnclr.ui.TvConfirmDialog
import dev.tvnclr.ui.TvEmptyState
import dev.tvnclr.ui.TvMenuDialog
import dev.tvnclr.ui.TvMenuItem
import dev.tvnclr.ui.TvNote
import dev.tvnclr.ui.TvOverflowMenu
import dev.tvnclr.ui.TvPage
import dev.tvnclr.ui.TvRow
import dev.tvnclr.ui.TvSection
import dev.tvnclr.ui.TvShape
import dev.tvnclr.ui.TvSpace
import dev.tvnclr.ui.TvStatus
import dev.tvnclr.ui.TvStatusPill
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val COLUMNS = 3

@Composable
fun PackScreen(packId: String, onBack: () -> Unit, onEdit: (String, Uri) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var refresh by remember { mutableIntStateOf(0) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
  val pack by produceState<PackStore.Pack?>(null, refresh) { value = withContext(Dispatchers.IO) { PackStore.get(context, packId) } }
  val added by produceState<Boolean?>(null, refresh) {
    value = withContext(Dispatchers.IO) { WhatsApp.isAdded(context, WhatsApp.CONSUMER, packId) ?: WhatsApp.isAdded(context, WhatsApp.BUSINESS, packId) }
  }

  var picking by remember { mutableStateOf(false) }
  var menuFor by remember { mutableStateOf<PackStore.Sticker?>(null) }
  var emojiFor by remember { mutableStateOf<PackStore.Sticker?>(null) }
  var deleteFor by remember { mutableStateOf<PackStore.Sticker?>(null) }
  var exportFor by remember { mutableStateOf<PackStore.Sticker?>(null) }
  var renaming by remember { mutableStateOf(false) }
  var deletingPack by remember { mutableStateOf(false) }
  var waError by remember { mutableStateOf<String?>(null) }

  val p = pack
  // a pack that disappeared (deleted elsewhere) closes the screen
  LaunchedEffect(p, refresh) { if (refresh > 0 && p == null && withContext(Dispatchers.IO) { PackStore.get(context, packId) } == null) onBack() }

  val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onEdit(packId, uri) }
  val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onEdit(packId, uri) }
  val whatsapp =
      rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) context.toast(context.getString(R.string.pack_sent))
        else res.data?.getStringExtra("validation_error")?.let { waError = it }
        refresh++
      }
  val exporter =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/webp")) { dst ->
        val s = exportFor
        val cur = pack
        exportFor = null
        if (dst == null || s == null || cur == null) return@rememberLauncherForActivityResult
        scope.launch {
          val err =
              withContext(Dispatchers.IO) {
                runCatching {
                      context.contentResolver.openOutputStream(dst).use { os ->
                        requireNotNull(os) { "no destination" }
                        os.write(Files.readAllBytes(cur.file(s.file).toPath()))
                      }
                    }
                    .exceptionOrNull()
              }
          context.toast(if (err == null) context.getString(R.string.exported) else context.getString(R.string.export_failed, err.message))
        }
      }

  if (p == null) {
    TvPage(title = "", onBack = onBack) {}
    return
  }

  val n = p.stickers.size
  TvPage(
      title = p.name,
      subtitle = stringResource(if (p.animated) R.string.pack_animated_sub else R.string.pack_static_sub),
      onBack = onBack,
      actions = {
        TvOverflowMenu(
            listOf(
                TvMenuItem(stringResource(R.string.pack_rename), Icons.Rounded.Edit) { renaming = true },
                TvMenuItem(stringResource(R.string.pack_delete), Icons.Rounded.Delete, destructive = true) { deletingPack = true },
            )
        )
      },
      bottomBar = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TvSpace.m)) {
          TvButton(
              stringResource(R.string.pack_add),
              onClick = {
                if (n >= PackStore.MAX_STICKERS) context.toast(context.getString(R.string.pack_full)) else picking = true
              },
              icon = Icons.Rounded.Add,
              style = TvButtonStyle.OUTLINE,
              modifier = Modifier.weight(1f),
          )
          TvButton(
              stringResource(R.string.pack_send),
              onClick = {
                if (n < PackStore.MIN_STICKERS) {
                  val missing = PackStore.MIN_STICKERS - n
                  context.toast(context.resources.getQuantityString(R.plurals.pack_need_more, missing, missing))
                } else {
                  val i = WhatsApp.addIntent(context, p)
                  if (i == null) context.toast(context.getString(R.string.pack_no_whatsapp))
                  else
                      try {
                        whatsapp.launch(i)
                      } catch (e: ActivityNotFoundException) {
                        context.toast(context.getString(R.string.pack_wa_refused))
                      }
                }
              },
              icon = Icons.AutoMirrored.Rounded.Send,
              modifier = Modifier.weight(1.25f),
          )
        }
      },
  ) {
    TvCard {
      val (status, text) =
          when {
            n < PackStore.MIN_STICKERS -> {
              val missing = PackStore.MIN_STICKERS - n
              TvStatus.WARN to pluralStringResource(R.plurals.pack_status_missing, missing, missing)
            }
            added == true -> TvStatus.OK to stringResource(R.string.pack_status_added)
            else -> TvStatus.NEUTRAL to stringResource(R.string.pack_status_ready)
          }
      TvRow(
          title = stringResource(R.string.pack_count, n, PackStore.MAX_STICKERS),
          subtitle = text,
          icon = if (status == TvStatus.OK) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule,
          trailing = { if (added == true) TvStatusPill(stringResource(R.string.pack_in_whatsapp), TvStatus.OK) },
      )
    }

    TvSection(stringResource(R.string.pack_stickers), Icons.Rounded.Image) {
      if (n == 0) {
        TvEmptyState(stringResource(if (p.animated) R.string.pack_empty_animated else R.string.pack_empty_static), Icons.Rounded.Add)
      } else {
        androidx.compose.foundation.layout.Column(Modifier.padding(TvSpace.m), verticalArrangement = Arrangement.spacedBy(TvSpace.s)) {
          p.stickers.chunked(COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TvSpace.s)) {
              row.forEach { s ->
                Surface(
                    onClick = { menuFor = s },
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    shape = TvShape.inner,
                    color = Tv.colors.elevated,
                ) {
                  Box(Modifier.fillMaxSize().padding(6.dp)) { StickerImage(p.file(s.file), Modifier.fillMaxSize()) }
                }
              }
              repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
          }
        }
      }
      TvNote(stringResource(R.string.pack_tip))
    }
  }

  if (picking) {
    TvMenuDialog(
        title = stringResource(R.string.pick_title),
        items =
            listOf(
                TvMenuItem(stringResource(R.string.pick_gallery), Icons.Rounded.PhotoLibrary, subtitle = stringResource(R.string.pick_gallery_desc)) {
                  gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                TvMenuItem(stringResource(R.string.pick_files), Icons.Rounded.FolderOpen, subtitle = stringResource(R.string.pick_files_desc)) {
                  try {
                    files.launch(if (p.animated) arrayOf("image/gif", "video/*") else arrayOf("image/*", "video/*"))
                  } catch (e: ActivityNotFoundException) {
                    context.toast(context.getString(R.string.pick_no_app))
                  }
                },
            ),
        onDismiss = { picking = false },
    )
  }

  menuFor?.let { s ->
    TvMenuDialog(
        title = null,
        items =
            listOf(
                TvMenuItem(stringResource(R.string.sticker_menu_emoji), Icons.Rounded.EmojiEmotions, subtitle = stringResource(R.string.sticker_menu_emoji_desc, s.emoji.replace(",", " "))) { emojiFor = s },
                TvMenuItem(stringResource(R.string.sticker_menu_export), Icons.Rounded.Save, subtitle = stringResource(R.string.sticker_menu_export_desc)) {
                  exportFor = s
                  exporter.launch(p.name.replace(Regex("[^A-Za-z0-9 _-]"), "") + "_" + s.file)
                },
                TvMenuItem(stringResource(R.string.sticker_menu_delete), Icons.Rounded.Delete, destructive = true) { deleteFor = s },
            ),
        onDismiss = { menuFor = null },
    )
  }

  emojiFor?.let { s ->
    TextEntryDialog(
        title = stringResource(R.string.emoji_title),
        initial = s.emoji.replace(",", " "),
        placeholder = "😀",
        hint = stringResource(R.string.emoji_hint),
        onSave = { v ->
          emojiFor = null
          runCatching { PackStore.setEmoji(p, s.file, StickerMaker.parseEmoji(v)) }.onFailure { context.toast(context.getString(R.string.error_generic, it.message)) }
          refresh++
        },
        onDismiss = { emojiFor = null },
    )
  }

  deleteFor?.let { s ->
    TvConfirmDialog(
        title = stringResource(R.string.sticker_menu_delete),
        text = stringResource(R.string.delete_sticker_q),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        destructive = true,
        onConfirm = {
          deleteFor = null
          runCatching { PackStore.removeSticker(p, s.file) }.onFailure { context.toast(context.getString(R.string.error_generic, it.message)) }
          refresh++
        },
        onDismiss = { deleteFor = null },
    )
  }

  if (renaming) {
    TextEntryDialog(
        title = stringResource(R.string.pack_rename),
        initial = p.name,
        placeholder = stringResource(R.string.newpack_name),
        onSave = { v ->
          renaming = false
          runCatching { PackStore.rename(p, v) }.onFailure { context.toast(context.getString(R.string.error_generic, it.message)) }
          refresh++
        },
        onDismiss = { renaming = false },
    )
  }

  if (deletingPack) {
    TvConfirmDialog(
        title = stringResource(R.string.pack_delete),
        text = stringResource(R.string.pack_delete_q, p.name),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        destructive = true,
        onConfirm = {
          deletingPack = false
          PackStore.delete(p)
          onBack()
        },
        onDismiss = { deletingPack = false },
    )
  }

  waError?.let { msg ->
    TvConfirmDialog(
        title = stringResource(R.string.pack_wa_error_title),
        text = msg,
        confirmText = stringResource(R.string.ok),
        dismissText = null,
        onConfirm = { waError = null },
        onDismiss = { waError = null },
    )
  }
}
