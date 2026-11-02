package dev.tvnclr.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 4/8 dp spacing grid. Every gap and padding in Tvnclr is one of these. */
object TvSpace {
  val xxs = 2.dp
  val xs = 4.dp
  val s = 8.dp
  val m = 12.dp
  val l = 16.dp
  val xl = 24.dp
  val xxl = 32.dp
  /** Horizontal page margin. */
  val page = 16.dp
  /** Minimum touch target. */
  val touch = 48.dp
}

object TvShape {
  val card = RoundedCornerShape(22.dp)
  val inner = RoundedCornerShape(14.dp)
  val badge = RoundedCornerShape(12.dp)
  val pill = RoundedCornerShape(50)
}

/** Fixed type scale: hierarchy comes from size/weight, not from random colors. */
object TvType {
  val display = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
  val title = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
  val section = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
  val item = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
  val body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
  val caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
  val label = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
  val code = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
  val mono = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace)
}

/** Motion: short and purposeful. */
object TvMotion {
  const val fast = 150
  const val normal = 220
}
