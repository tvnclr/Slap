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
import android.os.SystemClock
import android.util.Log
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
import dev.tvnclr.slap.engine.GifAi
import dev.tvnclr.slap.engine.PackStore
import dev.tvnclr.slap.engine.StickerMaker
import dev.tvnclr.slap.engine.TextLayer
import dev.tvnclr.slap.engine.TextLayersView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State and work behind the editor: loading the file, background removal, the looping preview
 * (GIF frames or MediaPlayer on a TextureView that follows the crop) and the final render.
 * The crop and video views are classic Views hosted by the Compose screen.
 */
/**
 * [editFile] is set when re-editing a saved sticker: [uri] then points at its source copy and the
 * saved settings (framing, background, clip, text) are restored.
 */
class EditorController(
    private val context: Context,
    private val scope: CoroutineScope,
    val pack: PackStore.Pack,
    private val uri: Uri,
    val editFile: String? = null,
) {

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

  // ── text boxes (shared with the overlay view, which edits them in place) ──
  private val texts = ArrayList<TextLayer>()
  var textCount by mutableIntStateOf(0)
    private set
  /** The box open in the full-screen text editor, or null. */
  var textDraft by mutableStateOf<TextLayer?>(null)
    private set
  /** Index of the box being edited, -1 for a new one. */
  private var draftIndex = -1
  val draftIsExisting: Boolean
    get() = draftIndex >= 0

  fun addText() {
    draftIndex = -1
    textDraft = TextLayer().also { it.y = if (texts.isEmpty()) 0.5f else (0.5f + 0.12f * (texts.size % 3)).coerceAtMost(0.85f) }
  }

  fun editText(index: Int) {
    if (index !in texts.indices) return
    draftIndex = index
    textDraft = texts[index].copy()
    overlay?.setHidden(index)
  }

  /** Closes the full-screen editor; blank text removes the box. */
  fun commitText(layer: TextLayer?) {
    val i = draftIndex
    if (layer == null || layer.isEmpty) {
      if (i in texts.indices) texts.removeAt(i)
    } else if (i in texts.indices) {
      texts[i] = layer
    } else {
      texts.add(layer)
    }
    textDraft = null
    draftIndex = -1
    overlay?.setHidden(-1)
    textsChanged()
  }

  private fun textsChanged() {
    textCount = texts.size
    overlay?.invalidate()
  }
  var emoji by mutableStateOf("😀")

  val animatedOutput: Boolean
    get() = pack.animated && kind != null && kind != StickerMaker.Kind.IMAGE

  /** AI cutout: single-frame stickers and GIFs (cut frame by frame); not videos. */
  val aiAllowed: Boolean
    get() = !animatedOutput || kind == StickerMaker.Kind.GIF

  val effectiveLen: Long
    get() = maxOf(100L, minOf(lenMs, videoMs - startMs))

  /** Length of the finished sticker, before the 10 s cap. */
  val outputMs: Long
    get() = if (kind == StickerMaker.Kind.GIF) Math.round((source?.gifTotalMs ?: 0) / speed).toLong() else Math.round(effectiveLen / speed).toLong()

  // ── internals ──
  private var source: StickerMaker.Source? = null
  private var raw: Bitmap? = null
  private var aiCut: Bitmap? = null
  /** AI masks of every GIF frame, computed once per file. */
  private var gifMasks: GifAi.Masks? = null
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
  private var overlay: TextLayersView? = null
  /** Saved framing waiting for the first frame (re-edit). */
  private var pendingMatrix: Matrix? = null

  fun attachViews(cropView: CropView, videoView: TextureView, textView: TextLayersView) {
    crop = cropView
    video = videoView
    overlay = textView
    cropView.setOnChange { applyVideoTransform() }
    textView.setLayers(texts)
    textView.setListener(
        object : TextLayersView.Listener {
          override fun onTap(index: Int) = editText(index)

          override fun onChanged() = textsChanged()
        }
    )
    preview?.let { show(it, false) }
  }

  // ── loading ──

  fun load() {
    scope.launch {
      try {
        val saved =
            editFile?.let { f -> withContext(Dispatchers.IO) { runCatching { JSONObject(PackStore.settingsFile(pack, f).readText()) }.getOrNull() } }
        val known = saved?.optString("kind")?.let { k -> runCatching { StickerMaker.Kind.valueOf(k) }.getOrNull() }
        val s = withContext(Dispatchers.IO) { StickerMaker.open(context, uri, known) }
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
        if (saved != null) restore(saved)
        setStill(f, keep = false)
        if (saved != null && s.kind == StickerMaker.Kind.VIDEO && startMs > 0) refreshVideoStill()
        status = context.getString(if (animatedOutput) R.string.editor_ready_animated else R.string.editor_ready_static)
        loaded = true
      } catch (p: StickerMaker.Problem) {
        fatal =
            when (p.code) {
              StickerMaker.Problem.Code.FILE_TOO_LARGE -> context.getString(R.string.editor_too_large)
              StickerMaker.Problem.Code.VIDEO_DURATION, StickerMaker.Problem.Code.VIDEO_FRAMES -> context.getString(R.string.editor_video_unreadable)
              else -> context.getString(R.string.editor_open_failed, p.message)
            }
      } catch (e: SecurityException) {
        Log.w(TAG, "no read access to $uri", e)
        fatal = context.getString(R.string.editor_no_permission)
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
    Log.d(TAG, "show ${b.width}x${b.height} keep=$keep view=${crop != null}")
    preview = b
    val c = crop ?: return
    c.setSource(b, keep)
    if (!keep) {
      pendingMatrix?.let {
        c.restoreOutputMatrix(it, StickerMaker.SIZE)
        fillMode = c.isFillMode
        pendingMatrix = null
      }
    }
  }

  // ── saved settings (re-edit) ──

  private fun restore(o: JSONObject) {
    bg = runCatching { Bg.valueOf(o.optString("bg", "KEEP")) }.getOrDefault(Bg.KEEP)
    if (bg == Bg.AI && !aiAllowed) bg = Bg.KEEP
    tolerance = o.optInt("tolerance", tolerance)
    everywhere = o.optBoolean("everywhere", false)
    speed = o.optDouble("speed", 1.0).toFloat()
    startMs = o.optLong("startMs", 0L)
    lenMs = o.optLong("lengthMs", lenMs)
    emoji = o.optString("emoji", emoji).replace(",", " ")
    texts.clear()
    texts.addAll(TextLayer.listFrom(o))
    textsChanged()
    o.optJSONArray("matrix")?.let { a ->
      if (a.length() == 9) pendingMatrix = Matrix().apply { setValues(FloatArray(9) { a.optDouble(it).toFloat() }) }
    }
  }

  private fun settingsJson(base: Matrix, em: String): JSONObject {
    val v = FloatArray(9).also { base.getValues(it) }
    return JSONObject().apply {
      put("kind", kind?.name)
      put("bg", bg.name)
      put("tolerance", tolerance)
      put("everywhere", everywhere)
      put("speed", speed.toDouble())
      put("startMs", startMs)
      put("lengthMs", lenMs)
      put("emoji", em)
      put("texts", TextLayer.toJson(texts))
      put("matrix", JSONArray().apply { v.forEach { put(it.toDouble()) } })
    }
  }

  /** Copies the original file next to the sticker so it can be edited later (skipped above the size cap). */
  private fun saveSource(sticker: String): Boolean {
    val dst = PackStore.sourceFile(pack, sticker)
    dst.parentFile?.mkdirs()
    return try {
      var total = 0L
      context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input)
        dst.outputStream().use { out ->
          val buf = ByteArray(64 * 1024)
          while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            total += n
            if (total > MAX_SOURCE_BYTES) throw java.io.IOException("source too large")
            out.write(buf, 0, n)
          }
        }
      }
      true
    } catch (e: Exception) {
      dst.delete()
      false
    }
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
        Log.d(TAG, "solid start token=$token ${src.width}x${src.height} tol=$tol everywhere=$all")
        scope.launch {
          val t0 = SystemClock.elapsedRealtime()
          val r =
              try {
                Result.success(withContext(BG) { StickerMaker.removeSolid(src, tol, all).let { it to StickerMaker.transparentFraction(it) } })
              } catch (e: CancellationException) {
                throw e
              } catch (e: Throwable) {
                Result.failure(e)
              }
          if (token != bgToken.get()) {
            Log.d(TAG, "solid discarded token=$token current=${bgToken.get()}")
            return@launch
          }
          bgBusy = false
          r.onSuccess { (out, clear) ->
                Log.d(TAG, "solid done token=$token clear=${(clear * 100).roundToInt()}% in ${SystemClock.elapsedRealtime() - t0} ms")
                show(out, keep)
                if (clear < NOTHING_REMOVED) {
                  status = context.getString(R.string.bg_nothing_removed)
                  context.toast(status)
                }
              }
              .onFailure { e ->
                Log.w(TAG, "solid failed token=$token", e)
                status = context.getString(R.string.bg_failed_title)
                errorDialog = context.getString(R.string.bg_failed_title) to (e.message ?: e.javaClass.simpleName)
                bg = Bg.KEEP
                show(src, keep)
              }
        }
      }
      Bg.AI -> {
        if (animatedOutput && kind == StickerMaker.Kind.GIF) {
          aiGif(src, token, keep)
          return
        }
        aiCut?.let {
          show(it, keep)
          bgBusy = false
          return
        }
        bgBusy = true
        status = context.getString(R.string.bg_ai_cutting)
        Log.d(TAG, "ai start token=$token ${src.width}x${src.height}")
        StickerMaker.cutWithAi(context, src, { msg -> ui.post { status = msg } }) { fg, err ->
          if (token != bgToken.get()) {
            Log.d(TAG, "ai discarded token=$token current=${bgToken.get()}")
            return@cutWithAi
          }
          bgBusy = false
          Log.d(TAG, "ai done token=$token ok=${fg != null} err=$err")
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

  /** GIF with AI: one cutout of the first frame (installs the model, reports errors), then every frame. */
  private fun aiGif(src: Bitmap, token: Int, keep: Boolean) {
    gifMasks?.let { m ->
      show(StickerMaker.applyAiMask(src, m), keep)
      bgBusy = false
      return
    }
    val s = source ?: return
    bgBusy = true
    status = context.getString(R.string.bg_ai_cutting)
    StickerMaker.cutWithAi(context, src, { msg -> ui.post { status = msg } }) { fg, err ->
      if (token != bgToken.get()) return@cutWithAi
      if (fg == null) {
        bgBusy = false
        status = context.getString(R.string.bg_ai_unavailable)
        errorDialog = context.getString(R.string.bg_ai_error_title) to (err ?: "")
        bg = Bg.KEEP
        show(src, keep)
        return@cutWithAi
      }
      show(fg, keep)
      scope.launch {
        val r =
            withContext(BG) {
              runCatching {
                Log.d(TAG, "gif ai start token=$token frames=${s.gifFrames}")
                StickerMaker.gifAiMasks(s) { cur, total -> ui.post { if (token == bgToken.get()) status = context.getString(R.string.bg_ai_frames, cur, total) } }
              }
            }
        r.onSuccess { gifMasks = it }.onFailure { Log.w(TAG, "gif ai failed token=$token", it) }
        if (token != bgToken.get()) return@launch
        bgBusy = false
        r.onSuccess { m ->
              show(StickerMaker.applyAiMask(src, m), true)
              status = context.getString(R.string.bg_ai_frames_done)
            }
            .onFailure { e ->
              status = context.getString(R.string.bg_ai_unavailable)
              errorDialog = context.getString(R.string.bg_ai_error_title) to (e.message ?: "")
              bg = Bg.KEEP
              show(src, true)
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
          val fr = withContext(Dispatchers.Default) { StickerMaker.gifPreview(data, solid, tol, all, if (bg == Bg.AI) gifMasks else null) }
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
          aiMasks = if (bg == Bg.AI && animatedOutput) gifMasks else null
          everywhere = this@EditorController.everywhere
          tolerance = this@EditorController.tolerance
          speed = this@EditorController.speed
          startMs = this@EditorController.startMs
          lengthMs = effectiveLen
          texts = this@EditorController.texts.filter { !it.isEmpty }.map { it.copy() }
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
        val settings = settingsJson(base, em)
        withContext(Dispatchers.IO) {
          val edit = editFile
          if (edit != null) {
            PackStore.replaceSticker(pack, edit, webp, em)
            PackStore.settingsFile(pack, edit).writeText(settings.toString())
          } else {
            val name = PackStore.addSticker(pack, webp, em)
            if (saveSource(name)) PackStore.settingsFile(pack, name).writeText(settings.toString())
          }
        }
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
    overlay?.isEnabled = !b
    (context as? Activity)?.window?.let { if (b) it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
  }

  fun release() {
    stopPreview()
    releasePlayer()
    source?.release()
  }
}

/** Above this, the original is not kept and the sticker cannot be re-edited. */
private const val MAX_SOURCE_BYTES = 100L * 1024 * 1024

private const val TAG = "Slap/bg"

/**
 * Background removal runs on its own pool instead of Dispatchers.Default: a stuck job (the GIF AI
 * waits on ML Kit) can then never starve the flat-color removal or anything else.
 */
private val BG = Executors.newCachedThreadPool { r -> Thread(r, "slap-bg").apply { isDaemon = true } }.asCoroutineDispatcher()

/** Below this share of transparent pixels the removal is treated as having removed nothing. */
private const val NOTHING_REMOVED = 0.01f
