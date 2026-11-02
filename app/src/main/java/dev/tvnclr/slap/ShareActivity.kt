package dev.tvnclr.slap

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.tvnclr.slap.ui.SlapApp
import dev.tvnclr.slap.ui.SlapTheme

/** Receives an image, GIF or video shared from another app ("Create sticker"). */
class ShareActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val uri = sharedUri(intent)
    if (uri == null) {
      Toast.makeText(this, R.string.share_nothing, Toast.LENGTH_SHORT).show()
      finish()
      return
    }
    enableEdgeToEdge()
    setContent { SlapTheme { SlapApp(sharedUri = uri, onFinish = ::finish) } }
  }

  private fun sharedUri(i: Intent): Uri? {
    val extra =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)
    if (extra != null) return extra
    val clip = i.clipData
    return if (clip != null && clip.itemCount > 0) clip.getItemAt(0).uri else null
  }
}
