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
import androidx.compose.material.icons.rounded.IosShare
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
  val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
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
      TvRow(stringResource(R.string.home_share_tip_title), stringResource(R.string.home_share_tip), Icons.Rounded.IosShare)
    }
  }
}
