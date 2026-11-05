package dev.tvnclr.slap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.tvnclr.slap.R
import dev.tvnclr.slap.engine.Fonts
import dev.tvnclr.slap.engine.TextLayer
import kotlin.math.abs

private const val PREVIEW_SP = 34f

/**
 * Full-screen text editor in the style of story editors: live text in the middle, alignment and
 * background style on top, a color strip on the right and the fonts above the keyboard.
 * [onDone] gets the edited layer, or null to delete it.
 */
@Composable
fun TextEditorOverlay(initial: TextLayer, existing: Boolean, onDone: (TextLayer?) -> Unit) {
  var value by remember { mutableStateOf(TextFieldValue(initial.text, TextRange(initial.text.length))) }
  var font by remember { mutableIntStateOf(Fonts.indexOf(initial.font)) }
  var color by remember { mutableIntStateOf(initial.color) }
  var align by remember { mutableIntStateOf(initial.align) }
  var style by remember { mutableIntStateOf(initial.style) }
  var colorPos by remember { mutableFloatStateOf(positionOf(initial.color)) }

  fun result(): TextLayer =
      initial.copy().also {
        it.text = value.text
        it.font = Fonts.ALL[font].id
        it.color = color
        it.align = align
        it.style = style
      }

  val keyboard = LocalSoftwareKeyboardController.current
  fun finish(layer: TextLayer?) {
    keyboard?.hide()
    onDone(layer)
  }

  Dialog(
      onDismissRequest = { finish(result()) },
      properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
      focus.requestFocus()
      keyboard?.show()
    }
    val layer = result()
    val family = remember(font) { FontFamily(Fonts.typeface(Fonts.ALL[font].id)) }
    Box(
        Modifier.fillMaxSize()
            .background(Color(0xD9000000))
            // tapping the empty area closes the editor, like in story editors
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish(result()) }
            .systemBarsPadding()
            .imePadding()
    ) {
      Column(Modifier.fillMaxSize()) {
        // ── top bar ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Pill(stringResource(R.string.text_done)) { finish(result()) }
          Spacer(Modifier.weight(1f))
          CircleIcon(stringResource(R.string.text_align), onClick = { align = (align + 1) % 3 }) {
            Icon(
                when (align) {
                  TextLayer.ALIGN_LEFT -> Icons.AutoMirrored.Rounded.FormatAlignLeft
                  TextLayer.ALIGN_RIGHT -> Icons.AutoMirrored.Rounded.FormatAlignRight
                  else -> Icons.Rounded.FormatAlignCenter
                },
                null,
                tint = Color.White,
            )
          }
          CircleIcon(stringResource(R.string.text_style), onClick = { style = (style + 1) % TextLayer.STYLE_COUNT }) { StyleGlyph(style) }
          Spacer(Modifier.weight(1f))
          if (existing) {
            CircleIcon(stringResource(R.string.text_delete), onClick = { finish(null) }) { Icon(Icons.Rounded.Delete, null, tint = Color.White) }
          } else {
            Spacer(Modifier.width(48.dp))
          }
        }
        // ── live text + color strip ──
        Box(Modifier.weight(1f).fillMaxWidth()) {
          Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 64.dp), contentAlignment = Alignment.Center) {
            LiveText(value, { value = it }, layer, family, align, focus)
          }
          ColorStrip(
              pos = colorPos,
              onPick = { p ->
                colorPos = p
                color = colorAt(p).toArgb()
              },
              modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
          )
        }

        // ── fonts ──
        Text(
            Fonts.ALL[font].name,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp),
        )
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (font - 2).coerceAtLeast(0))
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        ) {
          itemsIndexed(Fonts.ALL) { i, d ->
            val sel = i == font
            val fam = remember(d.id) { FontFamily(Fonts.typeface(d.id)) }
            Box(
                Modifier.size(52.dp)
                    .background(if (sel) Color.White else Color(0x33FFFFFF), CircleShape)
                    .clickable { font = i },
                contentAlignment = Alignment.Center,
            ) {
              Text("Aa", fontFamily = fam, fontSize = 19.sp, color = if (sel) Color.Black else Color.White, maxLines = 1)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LiveText(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, layer: TextLayer, family: FontFamily, align: Int, focus: FocusRequester) {
  val textAlign =
      when (align) {
        TextLayer.ALIGN_LEFT -> TextAlign.Left
        TextLayer.ALIGN_RIGHT -> TextAlign.Right
        else -> TextAlign.Center
      }
  val base = TextStyle(fontFamily = family, fontSize = PREVIEW_SP.sp, lineHeight = (PREVIEW_SP * 1.2f).sp, textAlign = textAlign, color = Color(layer.fillColor()))
  val strokePx = with(LocalDensity.current) { (PREVIEW_SP * 0.16f).sp.toPx() }
  val box = layer.boxColor()
  val stroke = layer.strokeColor()
  BasicTextField(
      value = value,
      onValueChange = onChange,
      textStyle = base,
      cursorBrush = SolidColor(Color(layer.fillColor())),
      modifier = Modifier.focusRequester(focus).widthIn(min = 4.dp),
      decorationBox = { inner ->
        Box(
            Modifier.then(if (box != 0) Modifier.background(Color(box), RoundedCornerShape(12.dp)) else Modifier)
                .padding(horizontal = if (box != 0) 14.dp else 4.dp, vertical = if (box != 0) 6.dp else 2.dp)
        ) {
          if (value.text.isEmpty()) {
            Text(stringResource(R.string.text_type_here), style = base.copy(color = Color(layer.fillColor()).copy(alpha = 0.45f)))
          }
          if (stroke != 0 && value.text.isNotEmpty()) {
            Text(value.text, style = base.copy(color = Color(stroke), drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round)))
          }
          inner()
        }
      },
  )
}

/** Icon for the background style button: the letter drawn the way the style draws text. */
@Composable
private fun StyleGlyph(style: Int) {
  val letter = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Black)
  when (style) {
    TextLayer.STYLE_BOX ->
        Box(Modifier.size(26.dp).background(Color.White, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Text("A", style = letter.copy(color = Color.Black)) }
    TextLayer.STYLE_GLASS ->
        Box(Modifier.size(26.dp).background(Color(0x66FFFFFF), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Text("A", style = letter.copy(color = Color.White)) }
    TextLayer.STYLE_OUTLINE ->
        Box(contentAlignment = Alignment.Center) {
          Text("A", style = letter.copy(color = Color.White, drawStyle = Stroke(width = 6f, join = StrokeJoin.Round)))
          Text("A", style = letter.copy(color = Color.Black))
        }
    else -> Text("A", style = letter.copy(color = Color.White))
  }
}

// ── color strip: white to black on top, then the hue circle ──

private const val GRAY_END = 0.16f
private const val HUE_START = 0.19f

private fun colorAt(p: Float): Color {
  val q = p.coerceIn(0f, 1f)
  return when {
    q <= GRAY_END -> {
      val v = 1f - q / GRAY_END
      Color(v, v, v)
    }
    q < HUE_START -> Color.Black
    else -> Color.hsv(((q - HUE_START) / (1f - HUE_START)) * 360f, 1f, 1f)
  }
}

/** Closest point of the strip to [argb], so an existing text opens with its color marked. */
private fun positionOf(argb: Int): Float {
  val target = Color(argb)
  var best = 0f
  var bestD = Float.MAX_VALUE
  for (i in 0..400) {
    val p = i / 400f
    val c = colorAt(p)
    val d = abs(c.red - target.red) + abs(c.green - target.green) + abs(c.blue - target.blue)
    if (d < bestD) {
      bestD = d
      best = p
    }
  }
  return best
}

@Composable
private fun ColorStrip(pos: Float, onPick: (Float) -> Unit, modifier: Modifier = Modifier) {
  val stops = remember {
    buildList {
      add(0f to Color.White)
      add(GRAY_END to Color.Black)
      add(HUE_START to Color.Black)
      for (i in 0..12) add(HUE_START + (1f - HUE_START) * i / 12f to Color.hsv(i * 30f % 360f, 1f, 1f))
    }.toTypedArray()
  }
  val h = 280.dp
  val hPx = with(LocalDensity.current) { h.toPx() }
  Box(modifier.width(36.dp).height(h)) {
    Canvas(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onPick(it.y / size.height) } }
            .pointerInput(Unit) {
              detectVerticalDragGestures(onDragStart = { onPick(it.y / size.height) }) { change, _ ->
                change.consume()
                onPick(change.position.y / size.height)
              }
            }
    ) {
      val w = 8.dp.toPx()
      drawRoundRect(
          brush = Brush.verticalGradient(*stops),
          topLeft = Offset(size.width / 2 - w / 2, 0f),
          size = androidx.compose.ui.geometry.Size(w, size.height),
          cornerRadius = CornerRadius(w / 2, w / 2),
      )
    }
    // thumb
    val y = with(LocalDensity.current) { (pos.coerceIn(0f, 1f) * hPx).toDp() } - 13.dp
    Box(
        Modifier.align(Alignment.TopCenter)
            .offset(y = y)
            .size(26.dp)
            .background(colorAt(pos), CircleShape)
            .border(3.dp, Color.White, CircleShape)
    )
  }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
  Surface(onClick = onClick, shape = RoundedCornerShape(50), color = Color(0x33FFFFFF)) {
    Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
  }
}


@Composable
private fun CircleIcon(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
  Surface(onClick = onClick, shape = CircleShape, color = Color(0x33FFFFFF), modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
    Box(contentAlignment = Alignment.Center) { content() }
  }
}
