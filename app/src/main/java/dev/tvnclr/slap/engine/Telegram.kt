package dev.tvnclr.slap.engine

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a static pack to Telegram's sticker importer. Each import creates a new Telegram set;
 * animated packs are not supported (Telegram wants WEBM VP9 or TGS, not animated WebP).
 */
object Telegram {
  const val ACTION = "org.telegram.messenger.CREATE_STICKER_PACK"
  private val PACKAGES = listOf("org.telegram.messenger", "org.telegram.messenger.web")
  private const val AUTHORITY = "dev.tvnclr.slap.files"

  fun isInstalled(c: Context): Boolean =
      PACKAGES.any { runCatching { c.packageManager.getPackageInfo(it, 0) }.isSuccess }

  /** Copies the stickers to a shareable cache dir and builds the import intent (call off the main thread). */
  fun importIntent(c: Context, p: PackStore.Pack): Intent {
    val dir = File(c.cacheDir, "telegram/${p.id}")
    dir.deleteRecursively()
    dir.mkdirs()
    val uris = ArrayList<Uri>()
    val emojis = ArrayList<String>()
    for (s in p.stickers) {
      val out = File(dir, s.file)
      p.file(s.file).copyTo(out, overwrite = true)
      uris += FileProvider.getUriForFile(c, AUTHORITY, out)
      emojis += s.emoji.split(",").firstOrNull { it.isNotBlank() }?.trim() ?: "\uD83D\uDE00"
    }
    return Intent(ACTION).apply {
      type = "image/*"
      putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
      putStringArrayListExtra("STICKER_EMOJIS", emojis)
      putExtra("IMPORTER", c.packageName)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      clipData = ClipData.newRawUri(null, uris.first()).also { cd -> uris.drop(1).forEach { cd.addItem(ClipData.Item(it)) } }
    }
  }
}