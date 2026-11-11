package dev.tvnclr.slap.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import dev.tvnclr.slap.engine.Fonts
import dev.tvnclr.ui.Tv
import dev.tvnclr.ui.TvShape
import dev.tvnclr.ui.TvSpace
import dev.tvnclr.ui.TvType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tvnclr.slap.R
import dev.tvnclr.ui.TvDivider
import dev.tvnclr.ui.TvPage
import dev.tvnclr.ui.TvRow
import dev.tvnclr.ui.TvSection

private const val SOURCE_URL = "https://github.com/tvnclr/Slap"

@Composable
fun AboutScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty().replace(Regex("-dev\\.(\\d+)$"), " (dev \$1)") }
  var showFonts by remember { mutableStateOf(false) }
  TvPage(title = stringResource(R.string.about_title), onBack = onBack) {
    TvSection(stringResource(R.string.app_name), Icons.Rounded.Info) {
      TvRow(
          title = stringResource(R.string.app_name),
          subtitle = stringResource(R.string.about_version, version),
          leading = {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
              Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(60.dp), colorFilter = ColorFilter.tint(Color.White))
            }
          },
      )
      TvDivider()
      TvRow(
          title = stringResource(R.string.about_source),
          subtitle = stringResource(R.string.about_source_desc),
          icon = Icons.Rounded.Code,
          onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) },
      )
      TvDivider()
      val bundled = remember { Fonts.ALL.filter { it.license != null } }
      TvRow(
          title = stringResource(R.string.about_fonts),
          subtitle = stringResource(R.string.about_fonts_desc, bundled.size),
          icon = Icons.Rounded.TextFields,
          onClick = { showFonts = true },
      )
    }
  }

  if (showFonts) FontLicensesDialog { showFonts = false }
}

/** Font list; tapping one shows its license text, which the SIL OFL asks to ship with the fonts. */
@Composable
private fun FontLicensesDialog(onDismiss: () -> Unit) {
  val context = LocalContext.current
  var open by remember { mutableStateOf<Fonts.Def?>(null) }
  Dialog(onDismissRequest = { if (open != null) open = null else onDismiss() }) {
    Surface(shape = TvShape.card, color = Tv.colors.card, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = TvSpace.s)) {
        val d = open
        if (d == null) {
          Fonts.ALL.filter { it.license != null }.forEach { f ->
            TvRow(title = f.name, subtitle = f.license, onClick = { open = f })
          }
        } else {
          val text = remember(d.id) { runCatching { context.assets.open("licenses/fonts/${d.id}.txt").bufferedReader().use { it.readText() } }.getOrDefault("") }
          TvRow(title = d.name, subtitle = d.license)
          Text(text, style = TvType.mono, color = Tv.colors.textSecondary, modifier = Modifier.padding(horizontal = TvSpace.l, vertical = TvSpace.s))
        }
      }
    }
  }
}
