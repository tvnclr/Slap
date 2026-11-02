package dev.tvnclr.slap.ui

import android.net.Uri
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.tvnclr.slap.R
import dev.tvnclr.slap.engine.CropView
import dev.tvnclr.slap.engine.PackStore
import dev.tvnclr.slap.engine.StickerMaker
import dev.tvnclr.ui.Tv
import dev.tvnclr.ui.TvButton
import dev.tvnclr.ui.TvButtonStyle
import dev.tvnclr.ui.TvChoiceChips
import dev.tvnclr.ui.TvConfirmDialog
import dev.tvnclr.ui.TvDivider
import dev.tvnclr.ui.TvPage
import dev.tvnclr.ui.TvRow
import dev.tvnclr.ui.TvSection
import dev.tvnclr.ui.TvSegmented
import dev.tvnclr.ui.TvShape
import dev.tvnclr.ui.TvSpace
import dev.tvnclr.ui.TvSwitchRow
import dev.tvnclr.ui.TvTextField
import dev.tvnclr.ui.TvType
import java.text.NumberFormat

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f)
private const val TOL_MIN = 5
private const val TOL_MAX = 120

@Composable
fun EditorScreen(packId: String, uri: Uri, onDone: (saved: Boolean) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val pack = remember(packId) { PackStore.get(context, packId) }
  if (pack == null) {
    LaunchedEffect(Unit) { onDone(false) }
    return
  }
  val ed = remember(packId, uri) { EditorController(context, scope, pack, uri) }
  LaunchedEffect(ed) { ed.load() }
  DisposableEffect(ed) { onDispose { ed.release() } }
  LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { ed.stopPreview() }
  BackHandler(enabled = ed.busy) { context.toast(context.getString(R.string.editor_wait)) }

  val c = Tv.colors
  val loading = stringResource(R.string.editor_loading)
  val animated = ed.animatedOutput

  TvPage(
      title = stringResource(R.string.editor_title),
      subtitle = pack.name,
      onBack = { if (ed.busy) context.toast(context.getString(R.string.editor_wait)) else onDone(false) },
      bottomBar = {
        Column(verticalArrangement = Arrangement.spacedBy(TvSpace.s)) {
          if (ed.busy) {
            if (ed.progress >= 0f) {
              LinearProgressIndicator(progress = { ed.progress }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.elevated)
            } else {
              LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.elevated)
            }
          }
          TvButton(
              stringResource(R.string.editor_save),
              onClick = { ed.save { onDone(true) } },
              icon = Icons.Rounded.Save,
              enabled = ed.loaded && !ed.busy && !ed.bgBusy,
              modifier = Modifier.fillMaxWidth(),
          )
        }
      },
  ) {
    Text(ed.status, style = TvType.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = TvSpace.xs))

    // crop area: the classic CropView, with a TextureView on top for the video preview
    AndroidView(
        factory = { ctx ->
          val frame = FrameLayout(ctx)
          val cropView = CropView(ctx)
          frame.addView(cropView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
          val video = TextureView(ctx).apply { visibility = View.INVISIBLE }
          frame.addView(video, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
          ed.attachViews(cropView, video)
          frame
        },
        update = { ed.crop?.setColors(c.cardBorder.toArgb(), c.elevated.toArgb(), c.card.toArgb(), c.textSecondary.toArgb(), loading) },
        modifier = Modifier.fillMaxWidth().clip(TvShape.card),
    )

    Column(verticalArrangement = Arrangement.spacedBy(TvSpace.s)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TvSpace.m)) {
        if (animated) {
          TvButton(
              stringResource(if (ed.previewing) R.string.editor_stop else R.string.editor_preview),
              onClick = ed::togglePreview,
              icon = if (ed.previewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
              style = TvButtonStyle.TONAL,
              enabled = ed.loaded && !ed.busy,
              modifier = Modifier.weight(1f),
          )
        }
        TvButton(
            stringResource(if (ed.fillMode) R.string.editor_fit else R.string.editor_fill),
            onClick = ed::toggleFill,
            icon = Icons.Rounded.AspectRatio,
            style = TvButtonStyle.OUTLINE,
            enabled = ed.loaded && !ed.busy,
            modifier = Modifier.weight(1f),
        )
      }
      Text(stringResource(R.string.editor_gestures), style = TvType.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = TvSpace.xs))
    }

    if (ed.loaded) {
      TvSection(stringResource(R.string.editor_file), Icons.Rounded.Image) {
        TvRow(
            title = ed.fileTitle + " · " + ed.fileInfo,
            subtitle = stringResource(if (animated) R.string.editor_out_animated else R.string.editor_out_static),
            icon =
                when (ed.kind) {
                  StickerMaker.Kind.GIF -> Icons.Rounded.Gif
                  StickerMaker.Kind.VIDEO -> Icons.Rounded.Movie
                  else -> Icons.Rounded.Image
                },
        )
      }

      if (ed.kind != StickerMaker.Kind.VIDEO) {
        TvSection(stringResource(R.string.editor_background), Icons.Rounded.AutoFixHigh) {
          Column(Modifier.padding(TvSpace.l), verticalArrangement = Arrangement.spacedBy(TvSpace.m)) {
            val modes = if (ed.aiAllowed) EditorController.Bg.entries else listOf(EditorController.Bg.KEEP, EditorController.Bg.SOLID)
            val labels =
                modes.map {
                  stringResource(
                      when (it) {
                        EditorController.Bg.KEEP -> R.string.bg_keep
                        EditorController.Bg.SOLID -> R.string.bg_solid
                        EditorController.Bg.AI -> R.string.bg_ai
                      }
                  )
                }
            TvSegmented(labels, modes.indexOf(ed.bg).coerceAtLeast(0), { ed.selectBg(modes[it]) })
            Text(
                stringResource(
                    when (ed.bg) {
                      EditorController.Bg.KEEP -> R.string.bg_keep_hint
                      EditorController.Bg.SOLID -> R.string.bg_solid_hint
                      EditorController.Bg.AI -> R.string.bg_ai_hint
                    }
                ),
                style = TvType.caption,
                color = c.textSecondary,
            )
            if (ed.bg == EditorController.Bg.SOLID) {
              Text(stringResource(R.string.bg_tolerance, ed.tolerance), style = TvType.item, color = c.textPrimary)
              Slider(
                  value = ed.tolerance.toFloat(),
                  onValueChange = { ed.tolerance = it.toInt() },
                  onValueChangeFinished = ed::toleranceCommitted,
                  valueRange = TOL_MIN.toFloat()..TOL_MAX.toFloat(),
                  colors = sliderColors(),
              )
            }
          }
          if (ed.bg == EditorController.Bg.SOLID) {
            TvDivider(inset = false)
            TvSwitchRow(stringResource(R.string.bg_everywhere), ed.everywhere, ed::changeEverywhere, subtitle = stringResource(R.string.bg_everywhere_desc))
          }
        }
      }

      if (ed.kind == StickerMaker.Kind.VIDEO) {
        TvSection(stringResource(R.string.editor_clip), Icons.Rounded.Schedule) {
          Column(Modifier.padding(TvSpace.l), verticalArrangement = Arrangement.spacedBy(TvSpace.s)) {
            Text(stringResource(R.string.editor_start, seconds(ed.startMs), seconds(ed.videoMs)), style = TvType.item, color = c.textPrimary)
            Slider(
                value = ed.startMs.toFloat(),
                onValueChange = { ed.startChanged((it / 100).toLong() * 100) },
                onValueChangeFinished = ed::startCommitted,
                valueRange = 0f..maxOf(1f, ed.videoMs.toFloat()),
                colors = sliderColors(),
            )
            if (animated) {
              Text(stringResource(R.string.editor_length, seconds(ed.effectiveLen)), style = TvType.item, color = c.textPrimary)
              Slider(
                  value = ed.lenMs.toFloat(),
                  onValueChange = { ed.lenMs = (it / 100).toLong() * 100 },
                  valueRange = 500f..10_000f,
                  colors = sliderColors(),
              )
            }
          }
        }
      }

      if (animated) {
        TvSection(stringResource(R.string.editor_speed), Icons.Rounded.Speed) {
          Column(Modifier.padding(TvSpace.l), verticalArrangement = Arrangement.spacedBy(TvSpace.m)) {
            val nf = remember { NumberFormat.getNumberInstance() }
            TvChoiceChips(SPEEDS.map { nf.format(it) + "×" }, SPEEDS.indexOf(ed.speed), { ed.setSpeedValue(SPEEDS[it]) })
            val out = ed.outputMs
            Text(
                if (out > StickerMaker.MAX_TOTAL_MS) stringResource(R.string.editor_duration_cut, seconds(StickerMaker.MAX_TOTAL_MS.toLong()))
                else stringResource(R.string.editor_duration, seconds(out)),
                style = TvType.caption,
                color = c.textSecondary,
            )
          }
        }
      }

      TvSection(stringResource(R.string.editor_emoji), Icons.Rounded.EmojiEmotions) {
        Column(Modifier.padding(TvSpace.l), verticalArrangement = Arrangement.spacedBy(TvSpace.s)) {
          TvTextField(ed.emoji, { ed.emoji = it }, placeholder = "😀")
          Text(stringResource(R.string.editor_emoji_hint), style = TvType.caption, color = c.textSecondary)
        }
      }
    }
  }

  ed.fatal?.let { msg ->
    TvConfirmDialog(
        title = stringResource(R.string.editor_title),
        text = msg,
        confirmText = stringResource(R.string.ok),
        dismissText = null,
        onConfirm = { onDone(false) },
        onDismiss = { onDone(false) },
    )
  }
  ed.errorDialog?.let { (title, msg) ->
    TvConfirmDialog(
        title = title,
        text = msg,
        confirmText = stringResource(R.string.ok),
        dismissText = null,
        onConfirm = { ed.errorDialog = null },
        onDismiss = { ed.errorDialog = null },
    )
  }
}

@Composable
private fun sliderColors() =
    SliderDefaults.colors(
        thumbColor = Tv.colors.accent,
        activeTrackColor = Tv.colors.accent,
        inactiveTrackColor = Tv.colors.elevated,
        activeTickColor = Tv.colors.onAccent,
        inactiveTickColor = Tv.colors.textSecondary,
    )
