package dev.tvnclr.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/** Animated page backgrounds the user can pick in the app settings. */
enum class TvBackgroundStyle { ORBS, AURORA, HORIZON, RADAR, DEPTH, PLAIN }

val LocalTvBackgroundStyle = staticCompositionLocalOf { TvBackgroundStyle.ORBS }

private const val TWO_PI = (2 * PI).toFloat()

/** One loop of every background. All motion uses integer multiples of it, so the loop is seamless. */
private const val LOOP_MS = 60_000

/**
 * Page background. [scroll] is the page scroll offset in px (DEPTH and HORIZON follow it);
 * [hero] marks the main screen (RADAR sweeps a little brighter there). Everything freezes when the
 * system "Remove animations" setting is on.
 */
@Composable
fun TvBackground(
    modifier: Modifier = Modifier,
    scroll: () -> Float = { 0f },
    hero: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
  val c = Tv.colors
  val style = LocalTvBackgroundStyle.current
  val animate = remember { ValueAnimator.areAnimatorsEnabled() }
  val moving = animate && style != TvBackgroundStyle.PLAIN
  val t: State<Float> =
      if (moving) {
        rememberInfiniteTransition(label = "tv-bg")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing), RepeatMode.Restart), label = "tv-bg-t")
      } else {
        remember { mutableFloatStateOf(0.12f) }
      }

  Box(modifier.fillMaxSize().background(c.background)) {
    if (style != TvBackgroundStyle.PLAIN) {
      Canvas(Modifier.fillMaxSize()) {
        when (style) {
          TvBackgroundStyle.ORBS -> drawOrbs(t.value, c.circles)
          TvBackgroundStyle.AURORA -> drawAurora(t.value, c.accent, c.isDark)
          TvBackgroundStyle.HORIZON -> drawHorizon(t.value, scroll(), c)
          TvBackgroundStyle.RADAR -> drawRadar(t.value, hero, c)
          TvBackgroundStyle.DEPTH -> drawDepth(t.value, scroll(), c.circles)
          TvBackgroundStyle.PLAIN -> Unit
        }
      }
    }
    content()
  }
}

// ── Orbs: large circles wandering across most of the screen ─────────────────

private class Orbit(val x: Float, val y: Float, val r: Float, val ax: Float, val ay: Float, val kx: Int, val ky: Int, val phase: Float)

private val orbits =
    listOf(
        Orbit(0.30f, 0.22f, 0.42f, 0.28f, 0.18f, 1, 2, 0.00f),
        Orbit(0.72f, 0.40f, 0.34f, 0.30f, 0.26f, 2, 1, 0.30f),
        Orbit(0.35f, 0.65f, 0.28f, 0.32f, 0.22f, 1, 1, 0.60f),
        Orbit(0.68f, 0.82f, 0.46f, 0.26f, 0.16f, 1, 2, 0.15f),
    )

/** Lissajous position; integer frequencies keep the loop seamless. */
private fun DrawScope.orbit(o: Orbit, t: Float): Offset {
  val a = TWO_PI * (t + o.phase)
  return Offset((o.x + o.ax * sin(a * o.kx)) * size.width, (o.y + o.ay * cos(a * o.ky)) * size.height)
}

private fun DrawScope.drawOrbs(t: Float, color: Color) {
  val unit = minOf(size.width, size.height)
  for (o in orbits) drawCircle(color, radius = o.r * unit, center = orbit(o, t))
}

// ── Aurora: four soft glows that wander, breathe and drift in hue ───────────

private fun hueShift(color: Color, degrees: Float): Color {
  val hsv = FloatArray(3)
  android.graphics.Color.colorToHSV(color.toArgb(), hsv)
  hsv[0] = (hsv[0] + degrees + 360f) % 360f
  return Color(android.graphics.Color.HSVToColor(hsv))
}

private val auroraHues = listOf(0f, 42f, -42f, 84f)

private fun DrawScope.drawAurora(t: Float, accent: Color, dark: Boolean) {
  val unit = minOf(size.width, size.height)
  val base = if (dark) 0.20f else 0.14f
  orbits.forEachIndexed { i, o ->
    val a = TWO_PI * (t + o.phase)
    val center = orbit(o, t)
    val radius = unit * (0.68f + 0.18f * sin(a * 2))
    val alpha = base * (0.72f + 0.28f * sin(a * 3 + i))
    val tint = hueShift(accent, auroraHues[i] + 18f * sin(a))
    drawCircle(
        Brush.radialGradient(
            listOf(tint.copy(alpha = alpha), tint.copy(alpha = alpha * 0.35f), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
  }
}

// ── Horizon: an artificial horizon in flight — roll, pitch and a ground grid rushing by ──

private fun DrawScope.drawHorizon(t: Float, scroll: Float, c: TvColors) {
  val w = size.width
  val h = size.height
  val a = TWO_PI * t
  val roll = 6f * sin(a) + 2.5f * sin(a * 3 + 0.8f)
  val pitch = h * (0.04f * sin(a * 2 + 0.6f) + 0.015f * sin(a * 5))
  val cx = w / 2
  val cy = h * 0.42f + pitch - scroll * 0.2f
  val step = h * 0.09f
  val stroke = 1.5f * density
  val grid = c.textSecondary.copy(alpha = 0.09f)
  // forward motion: 8 grid rows pass per loop
  val travel = (t * 8f) - floor(t * 8f)
  rotate(roll, pivot = Offset(cx, cy)) {
    drawRect(c.circles, topLeft = Offset(-w, cy), size = Size(w * 3, h * 3))
    // ground rows, closer ones further apart (perspective)
    for (n in 1..12) {
      val z = n - travel
      if (z < 0.15f) continue
      val y = cy + h * 0.10f / z * 1.6f
      val fade = (1f - z / 12f).coerceIn(0f, 1f)
      drawLine(grid.copy(alpha = grid.alpha * fade), Offset(-w, y), Offset(w * 2, y), strokeWidth = stroke)
    }
    // ground rays converging on the horizon
    for (j in -9..9) {
      drawLine(grid, Offset(cx, cy), Offset(cx + j * w * 0.35f, cy + h * 1.6f), strokeWidth = stroke)
    }
    drawLine(c.accent.copy(alpha = 0.35f), Offset(-w, cy), Offset(w * 2, cy), strokeWidth = 2f * density)
    for (k in -6..6) {
      if (k == 0) continue
      val y = cy - k * step
      val half = if (k % 2 == 0) w * 0.16f else w * 0.08f
      drawLine(c.textSecondary.copy(alpha = 0.16f), Offset(cx - half, y), Offset(cx + half, y), strokeWidth = stroke)
    }
  }
  val wing = w * 0.07f
  val ref = c.accent.copy(alpha = 0.28f)
  val y = h * 0.42f - scroll * 0.2f
  drawLine(ref, Offset(cx - wing * 2, y), Offset(cx - wing * 0.6f, y), strokeWidth = 3f * density)
  drawLine(ref, Offset(cx + wing * 0.6f, y), Offset(cx + wing * 2, y), strokeWidth = 3f * density)
  drawCircle(ref, radius = 3f * density, center = Offset(cx, y))
}

// ── Radar: drifting scope with a sweep and contacts that glow as it passes ──

private class Blip(val angle: Float, val dist: Float)

private val blips =
    listOf(Blip(35f, 0.55f), Blip(118f, 0.92f), Blip(200f, 0.38f), Blip(262f, 1.25f), Blip(318f, 0.78f), Blip(160f, 1.45f))

private fun DrawScope.drawRadar(t: Float, hero: Boolean, c: TvColors) {
  val unit = minOf(size.width, size.height)
  val a = TWO_PI * t
  val center = Offset(size.width * (0.78f + 0.05f * sin(a)), size.height * (0.18f + 0.04f * cos(a * 2)))
  val ringStep = unit * 0.24f
  val ring = c.textSecondary.copy(alpha = 0.10f)
  // rings breathe outward slightly so the scope never looks frozen
  val breathe = 1f + 0.03f * sin(a * 3)
  for (i in 1..7) drawCircle(ring, radius = ringStep * i * breathe, center = center, style = Stroke(1.5f * density))
  drawLine(ring, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = density)
  drawLine(ring, Offset(center.x, 0f), Offset(center.x, size.height), strokeWidth = density)
  // 6 turns per loop = one every 10 s
  val sweep = (360f * t * 6f) % 360f
  val strength = if (hero) 0.24f else 0.16f
  rotate(sweep, pivot = center) {
    drawCircle(
        Brush.sweepGradient(
            0f to Color.Transparent,
            0.80f to Color.Transparent,
            1f to c.accent.copy(alpha = strength),
            center = center,
        ),
        radius = unit * 1.8f,
        center = center,
    )
  }
  for (b in blips) {
    val since = ((sweep - b.angle) % 360f + 360f) % 360f / 360f
    val glow = (1f - since).pow(3)
    if (glow < 0.02f) continue
    val rad = Math.toRadians(b.angle.toDouble())
    val p = Offset(center.x + (cos(rad) * b.dist * ringStep * 2).toFloat(), center.y + (sin(rad) * b.dist * ringStep * 2).toFloat())
    drawCircle(c.accent.copy(alpha = 0.55f * glow), radius = 4f * density, center = p)
    drawCircle(c.accent.copy(alpha = 0.18f * glow), radius = 10f * density * (1f + since), center = p)
  }
}

// ── Depth: layers drifting upward at different speeds, plus parallax while scrolling ──

private class Dot(val x: Float, val y: Float, val r: Float, val sway: Float, val phase: Float)

private class Layer(val scrollSpeed: Float, val drift: Int, val dots: List<Dot>)

private val layers =
    listOf(
        Layer(0.08f, 1, listOf(Dot(0.15f, 0.20f, 0.30f, 0.04f, 0.0f), Dot(0.85f, 0.70f, 0.38f, 0.03f, 0.4f), Dot(0.40f, 1.30f, 0.34f, 0.05f, 0.7f))),
        Layer(0.22f, 1, listOf(Dot(0.80f, 0.15f, 0.22f, 0.05f, 0.2f), Dot(0.10f, 0.85f, 0.26f, 0.04f, 0.9f), Dot(0.70f, 1.45f, 0.20f, 0.06f, 0.5f))),
        Layer(0.45f, 2, listOf(Dot(0.55f, 0.45f, 0.14f, 0.06f, 0.3f), Dot(0.92f, 1.05f, 0.16f, 0.05f, 0.8f), Dot(0.05f, 1.60f, 0.12f, 0.07f, 0.1f))),
    )

private fun DrawScope.drawDepth(t: Float, scroll: Float, color: Color) {
  val unit = minOf(size.width, size.height)
  val span = size.height * 2f
  layers.forEachIndexed { i, layer ->
    val alpha = 0.55f + 0.225f * i
    for (d in layer.dots) {
      val a = TWO_PI * (t + d.phase)
      var y = (d.y * size.height - scroll * layer.scrollSpeed - t * span * layer.drift) % span
      if (y < -unit) y += span
      val x = (d.x + d.sway * sin(a * (i + 1))) * size.width
      drawCircle(color.copy(alpha = color.alpha * alpha), radius = d.r * unit, center = Offset(x, y))
    }
  }
}
