package dev.tvnclr.slap.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.tvnclr.slap.R
import dev.tvnclr.slap.engine.CropView
import dev.tvnclr.slap.engine.PackStore
import dev.tvnclr.slap.engine.StickerMaker
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State and work behind the editor: loading the file, background removal, the looping preview
 * (GIF frames or MediaPlayer on a TextureView that follows the crop) and the final render.
 * The crop and video views are classic Views hosted by the Compose screen.
 */
class EditorController(private val context: Context, private val scope: CoroutineScope, val pack: PackStore.Pack, private val uri: Uri) {

  enum class Bg { KEEP, SOLID, AI }

  // ── state shown by the screen ──
  var status by mutableStateOf(context.getString(R.string.editor_opening))
  var loaded by mutableStateOf(false)
  var fatal by mutableStateOf<String?>(null)
  var errorDialog by mutableStateOf<Pair<String, String>?>(null)
  var kind by mutableStateOf<StickerMaker.Kind?>(null)
  var fileTitle by mutableStateOf("")
  var fileInfo by mutableStateOf("")
  var bg by mutableStateOf(Bg.KEEP)
  var tolerance by mutableIntStateOf(StickerMaker.DEFAULT_TOLERANCE)
  var everywhere by mutableStateOf(false)
  var bgBusy by mutableStateOf(false)
  var busy by mutableStateOf(false)
  var progress by mutableFloatStateOf(-1f)
  var previewing by mutableStateOf(false)
  var fillMode by mutableStateOf(true)
  var videoMs by mutableLongStateOf(0L)
  var startMs by mutableLongStateOf(0L)
  var lenMs by mutableLongStateOf(3000L)
  var speed by mutableFloatStateOf(1f)
  var emoji by mutableStateOf("😀")

  val animatedOutput: Boolean
    get() = pack.animated && kind != null && kind != StickerMaker.Kind.IMAGE

  /** The AI cutout is for single-frame stickers only (per frame it would be far too slow). */
  val aiAllowed: Boolean
    get() = !animatedOutput

  val effectiveLen: Long
    get() = maxOf(100L, minOf(lenMs, videoMs - startMs))

  /** Length of the finished sticker, before the 10 s cap. */
  val outputMs: Long
    get() = if (kind == StickerMaker.Kind.GIF) Math.round((source?.gifTotalMs ?: 0) / speed).toLong() else Math.round(effectiveLen / speed).toLong()

  // ── internals ──
  private var source: StickerMaker.Source? = null
  private var raw: Bitmap? = null
  private var aiCut: Bitmap? = null
  private var preview: Bitmap? = null
  private var prevW = 0
  private var prevH = 0
  private val bgToken = AtomicInteger()
  private val stillToken = AtomicInteger()
  private val ui = Handler(Looper.getMainLooper())
  private var player: MediaPlayer? = null
  private var surface: Surface? = null
  private var gifFrames: List<Bitmap>? = null
  private var gifDelays: IntArray = IntArray(0)
  private var gifIdx = 0

  var crop: CropView? = null
    private set

  private var video: TextureView? = null

  fun attachViews(cropView: CropView, videoView: TextureView) {
    crop = cropView
    video = videoView
    cropView.setOnChange { applyVideoTransform() }
    preview?.let { cropView.setSource(it, false) }
  }

  // ── loading ──

  fun load() {
    scope.launch {
      try {
        val s = withContext(Dispatchers.IO) { StickerMaker.open(context, uri) }
        source = s
        kind = s.kind
        if (pack.animated && s.kind == StickerMaker.Kind.IMAGE) {
          fatal = context.getString(R.string.editor_needs_motion)
          return@launch
        }
        if (pack.animated && s.kind == StickerMaker.Kind.GIF && s.gifFrames < 2) {
          fatal = context.getString(R.string.editor_gif_one_frame)
          return@launch
        }
        val f = s.first
        when (s.kind) {
          StickerMaker.Kind.GIF -> {
            fileTitle = context.getString(R.string.editor_kind_gif)
            fileInfo = "${f.width}×${f.height} · " + context.resources.getQuantityString(R.plurals.frames, s.gifFrames, s.gifFrames) + " · " + seconds(s.gifTotalMs.toLong())
          }
          StickerMaker.Kind.VIDEO -> {
            fileTitle = context.getString(R.string.editor_kind_video)
            fileInfo = seconds(s.videoMs)
            videoMs = s.videoMs
            lenMs = minOf(3000L, s.videoMs)
          }
          else -> {
            fileTitle = context.getString(R.string.editor_kind_image)
            fileInfo = "${f.width}×${f.height}"
          }
        }
        // removing the background of a video does not work well: photo and GIF only
        if (s.kind == StickerMaker.Kind.VIDEO) bg = Bg.KEEP
        setStill(f, keep = false)
        status = context.getString(if (animatedOutput) R.string.editor_ready_animated else R.string.editor_ready_static)
        loaded = true
      } catch (p: StickerMaker.Problem) {
        fatal =
            when (p.code) {
              StickerMaker.Problem.Code.FILE_TOO_LARGE -> context.getString(R.string.editor_too_large)
              StickerMaker.Problem.Code.VIDEO_DURATION, StickerMaker.Problem.Code.VIDEO_FRAMES -> context.getString(R.string.editor_video_unreadable)
              else -> context.getString(R.string.editor_open_failed, p.message)
            }
      } catch (t: Throwable) {
        fatal = context.getString(R.string.editor_open_failed, t.message)
      }
    }
  }

  /** New still frame (original); the background option is applied on top. */
  private fun setStill(b: Bitmap, keep: Boolean) {
    raw = b
    aiCut = null
    prevW = b.width
    prevH = b.height
    applyBg(keep)
  }

  private fun show(b: Bitmap, keep: Boolean) {
    preview = b
    crop?.setSource(b, keep)
  }

  // ── background ──

  fun selectBg(mode: Bg) {
    if (mode == bg) return
    bg = mode
    bgChanged()
  }

  fun changeEverywhere(on: Boolean) {
    everywhere = on
    bgChanged()
  }

  /** Called when the tolerance slider is released. */
  fun toleranceCommitted() = bgChanged()

  private fun bgChanged() {
    stopPreview()
    gifFrames = null
    if (raw != null) applyBg(true)
  }

  private fun applyBg(keep: Boolean) {
    val src = raw ?: return
    val token = bgToken.incrementAndGet()
    when (bg) {
      Bg.KEEP -> {
        show(src, keep)
        bgBusy = false
      }
      Bg.SOLID -> {
        bgBusy = true
        val tol = tolerance
        val all = everywhere
        scope.launch {
          val out = withContext(Dispatchers.Default) { StickerMaker.removeSolid(src, tol, all) }
          if (token != bgToken.get()) return@launch
          show(out, keep)
          bgBusy = false
        }
      }
      Bg.AI -> {
        aiCut?.let {
          show(it, keep)
          bgBusy = false
          return
        }
        bgBusy = true
        status = context.getString(R.string.bg_ai_cutting)
        StickerMaker.cutWithAi(context, src, { msg -> ui.post { status = msg } }) { fg, err ->
          if (token != bgToken.get()) return@cutWithAi
          bgBusy = false
          if (fg == null) {
            status = context.getString(R.string.bg_ai_unavailable)
            errorDialog = context.getString(R.string.bg_ai_error_title) to (err ?: "")
            bg = Bg.KEEP
            show(src, keep)
          } else {
            if (src === raw) aiCut = fg
            show(fg, keep)
            status = context.getString(R.string.bg_ai_done)
          }
        }
      }
    }
  }

  // ── video clip ──

  fun startChanged(ms: Long) {
    startMs = ms
  }

  /** Slider released: jump the preview, or show the still frame at the new start. */
  fun startCommitted() {
    if (previewing && player != null) seekPlayer(startMs) else refreshVideoStill()
  }

  private fun refreshVideoStill() {
    val s = source ?: return
    val token = stillToken.incrementAndGet()
    val at = startMs
    scope.launch {
      val f = withContext(Dispatchers.IO) { s.videoFrame(at) } ?: return@launch
      if (token == stillToken.get() && !busy && !previewing) setStill(f, keep = true)
    }
  }

  fun setSpeedValue(v: Float) {
    speed = v
    player?.let { p ->
      if (previewing) runCatching { p.playbackParams = p.playbackParams.setSpeed(v) }
    }
  }

  // ── crop ──

  fun toggleFill() {
    val c = crop ?: return
    c.setFillMode(!c.isFillMode)
    fillMode = c.isFillMode
  }

  // ── preview ──

  fun togglePreview() = if (previewing) stopPreview() else startPreview()

  private fun startPreview() {
    if (busy || preview == null || previewing) return
    val s = source ?: return
    previewing = true
    when (s.kind) {
      StickerMaker.Kind.VIDEO -> {
        val v = video ?: return
        v.visibility = View.VISIBLE
        if (v.isAvailable) {
          startPlayer(v.surfaceTexture!!)
        } else {
          v.surfaceTextureListener =
              object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                  if (previewing) startPlayer(st)
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = applyVideoTransform()

                override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true

                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
              }
        }
      }
      StickerMaker.Kind.GIF -> {
        if (gifFrames != null) {
          gifIdx = 0
          ui.post(gifTick)
          return
        }
        status = context.getString(R.string.editor_preview_preparing)
        val solid = bg == Bg.SOLID
        val tol = tolerance
        val all = everywhere
        val data = s.gifData
        scope.launch {
          val fr = withContext(Dispatchers.Default) { StickerMaker.gifPreview(data, solid, tol, all) }
          gifFrames = fr.frames
          gifDelays = fr.delays
          status = context.getString(R.string.editor_preview_loop)
          if (previewing && fr.frames.isNotEmpty()) {
            gifIdx = 0
            ui.post(gifTick)
          }
        }
      }
      else -> previewing = false
    }
  }

  private val gifTick: Runnable =
      object : Runnable {
        override fun run() {
          val frames = gifFrames
          if (!previewing || frames.isNullOrEmpty()) return
          if (gifIdx >= frames.size) gifIdx = 0
          crop?.setSource(frames[gifIdx], true)
          val d = maxOf(StickerMaker.MIN_FRAME_MS, Math.round(gifDelays[gifIdx] / speed))
          gifIdx++
          ui.postDelayed(this, d.toLong())
        }
      }

  private val loopCheck: Runnable =
      object : Runnable {
        override fun run() {
          val p = player ?: return
          if (!previewing) return
          try {
            val pos = p.currentPosition
            val end = startMs + effectiveLen
            if (pos >= end || pos < startMs - 250) seekPlayer(startMs)
          } catch (_: IllegalStateException) {}
          ui.postDelayed(this, 30)
        }
      }

  private fun seekPlayer(ms: Long) {
    runCatching { player?.seekTo(ms, MediaPlayer.SEEK_CLOSEST) }
  }

  private fun startPlayer(st: SurfaceTexture) {
    try {
      releasePlayer()
      val sf = Surface(st)
      surface = sf
      val mp = MediaPlayer()
      player = mp
      mp.setSurface(sf)
      mp.setDataSource(context, uri)
      mp.setVolume(0f, 0f)
      mp.setOnPreparedListener {
        if (!previewing) return@setOnPreparedListener
        applyVideoTransform()
        runCatching { it.playbackParams = it.playbackParams.setSpeed(speed) }
        seekPlayer(startMs)
        it.start()
        ui.removeCallbacks(loopCheck)
        ui.post(loopCheck)
        status = context.getString(R.string.editor_preview_loop_video)
      }
      mp.setOnErrorListener { _, _, _ ->
        stopPreview()
        context.toast(context.getString(R.string.editor_preview_video_error))
        true
      }
      mp.prepareAsync()
    } catch (e: Exception) {
      stopPreview()
      context.toast(context.getString(R.string.editor_preview_error, e.message))
    }
  }

  /** Makes the preview video follow the same framing as the crop. */
  private fun applyVideoTransform() {
    val v = video ?: return
    val c = crop ?: return
    if (prevW == 0 || v.width == 0 || v.height == 0) return
    val t = Matrix()
    t.setScale(prevW.toFloat() / v.width, prevH.toFloat() / v.height)
    t.postConcat(c.displayMatrix())
    v.setTransform(t)
    v.invalidate()
  }

  private fun releasePlayer() {
    ui.removeCallbacks(loopCheck)
    player?.let { runCatching { it.release() } }
    player = null
    surface?.release()
    surface = null
  }

  fun stopPreview() {
    if (!previewing) return
    previewing = false
    ui.removeCallbacks(gifTick)
    releasePlayer()
    video?.visibility = View.INVISIBLE
    preview?.let { crop?.setSource(it, true) }
    if (kind == StickerMaker.Kind.VIDEO && !busy) refreshVideoStill()
  }

  // ── save ──

  fun save(onSaved: () -> Unit) {
    val still = preview ?: return
    val c = crop ?: return
    val s = source ?: return
    if (busy) return
    if (bgBusy) {
      context.toast(context.getString(R.string.editor_wait_bg))
      return
    }
    stopPreview()
    val o =
        StickerMaker.Options().apply {
          animated = animatedOutput
          solidBackground = bg == Bg.SOLID
          everywhere = this@EditorController.everywhere
          tolerance = this@EditorController.tolerance
          speed = this@EditorController.speed
          startMs = this@EditorController.startMs
          lengthMs = effectiveLen
        }
    val base = c.outputMatrix(StickerMaker.SIZE)
    val em = StickerMaker.parseEmoji(emoji)
    val w = prevW
    val h = prevH
    markBusy(true)
    scope.launch {
      try {
        val webp =
            withContext(Dispatchers.Default) {
              StickerMaker.make(s, still, base, w, h, o) { stage, cur, total ->
                val text =
                    when (stage) {
                      StickerMaker.STAGE_READ_GIF -> context.getString(R.string.stage_read_gif, cur, total)
                      StickerMaker.STAGE_EXTRACT_VIDEO -> context.getString(R.string.stage_extract, cur, total)
                      StickerMaker.STAGE_ENCODE -> context.getString(R.string.stage_encode, cur, total)
                      else -> context.getString(R.string.stage_static)
                    }
                val frac = if (total > 0) cur.toFloat() / total else -1f
                ui.post {
                  status = text
                  progress = frac
                }
              }
            }
        withContext(Dispatchers.IO) { PackStore.addSticker(pack, webp, em) }
        markBusy(false)
        context.toast(context.getString(R.string.editor_saved, (webp.size + 1023) / 1024))
        onSaved()
      } catch (t: Throwable) {
        val msg = if (t is OutOfMemoryError) context.getString(R.string.editor_oom) else describe(t)
        markBusy(false)
        status = context.getString(R.string.error_generic, msg)
        errorDialog = context.getString(R.string.editor_failed_title) to msg
      }
    }
  }

  private fun describe(t: Throwable): String =
      if (t is StickerMaker.Problem && t.code == StickerMaker.Problem.Code.NOT_ENOUGH_FRAMES) context.getString(R.string.editor_frames_failed) else t.message.orEmpty()

  private fun markBusy(b: Boolean) {
    busy = b
    progress = -1f
    crop?.isEnabled = !b
    (context as? Activity)?.window?.let { if (b) it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
  }

  fun release() {
    stopPreview()
    releasePlayer()
    source?.release()
  }
}
