package dev.tvnclr.slap.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.tvnclr.slap.R
import dev.tvnclr.ui.Tv
import dev.tvnclr.ui.TvSegmented
import dev.tvnclr.ui.TvShape
import dev.tvnclr.ui.TvSpace
import dev.tvnclr.ui.TvTextButton
import dev.tvnclr.ui.TvTextField
import dev.tvnclr.ui.TvType
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

/** "1.5 s" / "1,5 s" depending on the language. */
fun seconds(ms: Long): String = String.format(Locale.getDefault(), "%.1f s", ms / 1000.0)

/** Sticker file preview; WebP animations play in a loop. */
@Composable
fun StickerImage(file: File, modifier: Modifier = Modifier) {
  var drawable by remember(file.path) { mutableStateOf<Drawable?>(null) }
  LaunchedEffect(file.path, file.lastModified()) {
    drawable = withContext(Dispatchers.IO) { runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) }.getOrNull() }
  }
  DisposableEffect(drawable) { onDispose { (drawable as? AnimatedImageDrawable)?.stop() } }
  AndroidView(
      factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
      update = { iv ->
        iv.setImageDrawable(drawable)
        (drawable as? AnimatedImageDrawable)?.apply {
          repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
          start()
        }
      },
      modifier = modifier,
  )
}

/** Pack tray icon (first sticker), on a rounded tile. */
@Composable
fun TrayImage(file: File, size: Dp = 48.dp) {
  val c = Tv.colors
  var bmp by remember(file.path) { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(file.path, file.lastModified()) {
    bmp = withContext(Dispatchers.IO) { if (file.isFile) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null }
  }
  Box(Modifier.size(size).clip(TvShape.badge).background(c.elevated), contentAlignment = Alignment.Center) {
    bmp?.let { Image(it, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit) }
  }
}

/** One-field dialog (pack name, emojis). */
@Composable
fun TextEntryDialog(
    title: String,
    initial: String,
    placeholder: String,
    hint: String? = null,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  val c = Tv.colors
  var value by remember { mutableStateOf(initial) }
  AlertDialog(
      onDismissRequest = onDismiss,
      containerColor = c.card,
      shape = TvShape.card,
      title = { Text(title, style = TvType.title, color = c.textPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(TvSpace.m)) {
          TvTextField(value, { value = it }, placeholder = placeholder)
          if (hint != null) Text(hint, style = TvType.caption, color = c.textSecondary)
        }
      },
      confirmButton = { TvTextButton(stringResource(R.string.save), { onSave(value.trim()) }, enabled = value.isNotBlank()) },
      dismissButton = { TvTextButton(stringResource(R.string.cancel), onDismiss, color = c.textSecondary) },
  )
}

/** New pack: name plus static/animated. [allowStatic] is false when the file being shared moves. */
@Composable
fun NewPackDialog(animatedDefault: Boolean, allowStatic: Boolean = true, onCreate: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
  val c = Tv.colors
  var name by remember { mutableStateOf("") }
  var animated by remember { mutableStateOf(animatedDefault || !allowStatic) }
  AlertDialog(
      onDismissRequest = onDismiss,
      containerColor = c.card,
      shape = TvShape.card,
      title = { Text(stringResource(R.string.newpack_title), style = TvType.title, color = c.textPrimary) },
      text = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TvSpace.m)) {
          TvTextField(name, { name = it }, placeholder = stringResource(R.string.newpack_name))
          if (allowStatic) {
            TvSegmented(
                listOf(stringResource(R.string.pack_kind_static), stringResource(R.string.pack_kind_animated)),
                selected = if (animated) 1 else 0,
                onSelect = { animated = it == 1 },
            )
          }
          Text(stringResource(R.string.newpack_hint), style = TvType.caption, color = c.textSecondary)
        }
      },
      confirmButton = { TvTextButton(stringResource(R.string.create), { onCreate(name.trim(), animated) }, enabled = name.isNotBlank()) },
      dismissButton = { TvTextButton(stringResource(R.string.cancel), onDismiss, color = c.textSecondary) },
  )
}
