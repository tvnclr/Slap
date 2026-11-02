package dev.tvnclr.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ── Page scaffolding ─────────────────────────────────────────────────────────

/**
 * Top bar: optional round back button, a compact title that fades in once the page has scrolled
 * past its large title, and trailing actions. Gets a solid background + hairline when [scrolled].
 */
@Composable
fun TvTopBar(
    onBack: (() -> Unit)? = null,
    title: String? = null,
    scrolled: Boolean = false,
    showTitle: Boolean = scrolled,
    actions: @Composable RowScope.() -> Unit = {},
) {
  val c = Tv.colors
  val bg by animateColorAsState(if (scrolled) c.background else Color.Transparent, label = "bar-bg")
  val line by animateColorAsState(if (scrolled) c.cardBorder else Color.Transparent, label = "bar-line")
  val titleAlpha by androidx.compose.animation.core.animateFloatAsState(if (showTitle && title != null) 1f else 0f, label = "bar-title")
  Column(Modifier.fillMaxWidth().background(bg)) {
    Row(
        Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = TvSpace.s, vertical = TvSpace.xs)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (onBack != null) {
        TvIconButton(Icons.AutoMirrored.Rounded.ArrowBack, androidx.compose.ui.res.stringResource(R.string.tv_back), onBack)
      } else {
        Spacer(Modifier.width(TvSpace.s))
      }
      Text(
          title ?: "",
          style = TvType.title,
          color = c.textPrimary.copy(alpha = titleAlpha),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(horizontal = TvSpace.s),
      )
      actions()
    }
    HorizontalDivider(thickness = 1.dp, color = line)
  }
}

@Composable
fun TvIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val c = Tv.colors
  Box(
      modifier
          .size(TvSpace.touch)
          .clip(CircleShape)
          .clickable(role = Role.Button, onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(c.card).border(1.dp, c.cardBorder, CircleShape), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription, tint = c.textPrimary, modifier = Modifier.size(22.dp))
    }
  }
}

/** Large page title with an optional subtitle. */
@Composable
fun TvPageTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
  val c = Tv.colors
  Column(modifier.fillMaxWidth().padding(start = TvSpace.xs, top = TvSpace.xs, bottom = TvSpace.s)) {
    Text(title, style = TvType.display, color = c.textPrimary)
    if (subtitle != null) {
      Spacer(Modifier.size(TvSpace.xs))
      Text(subtitle, style = TvType.body, color = c.textSecondary)
    }
  }
}

/**
 * Standard scrolling page: animated background, scroll-aware top bar, large title and a column of
 * sections spaced on the grid. [bottomBar] stays pinned above the navigation bar.
 */
@Composable
fun TvPage(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    hero: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
  val scroll = rememberScrollState()
  TvBackground(scroll = { scroll.value.toFloat() }, hero = hero) {
    Column(Modifier.fillMaxSize().imePadding()) {
      TvTopBar(onBack, title, scrolled = scroll.value > 0, showTitle = scroll.value > 160, actions = actions)
      Column(
          Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = TvSpace.page),
          verticalArrangement = Arrangement.spacedBy(TvSpace.xl),
      ) {
        TvPageTitle(title, subtitle)
        content()
        if (bottomBar == null) Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        Spacer(Modifier.size(TvSpace.l))
      }
      if (bottomBar != null) TvBottomBar(bottomBar)
    }
  }
}

/** Page whose body is a lazy list (history, phrase lists...). Header items scroll with the list. */
@Composable
fun TvListPage(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    bottomBar: (@Composable () -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
  val scrolled = state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0
  val showTitle = state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 160
  TvBackground(scroll = { state.firstVisibleItemIndex * 300f + state.firstVisibleItemScrollOffset }) {
    Column(Modifier.fillMaxSize().imePadding()) {
      TvTopBar(onBack, title, scrolled = scrolled, showTitle = showTitle, actions = actions)
      androidx.compose.foundation.lazy.LazyColumn(
          state = state,
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentPadding = PaddingValues(start = TvSpace.page, end = TvSpace.page, bottom = TvSpace.l),
          verticalArrangement = Arrangement.spacedBy(TvSpace.m),
      ) {
        item(key = "fd-title") { TvPageTitle(title, subtitle) }
        content()
        if (bottomBar == null) item(key = "fd-nav-space") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
      }
      if (bottomBar != null) TvBottomBar(bottomBar)
    }
  }
}

/** Pinned bottom area for primary actions (setup flows, add buttons). */
@Composable
fun TvBottomBar(content: @Composable () -> Unit) {
  val c = Tv.colors
  Column(Modifier.fillMaxWidth().background(c.background)) {
    HorizontalDivider(thickness = 1.dp, color = c.cardBorder)
    Box(Modifier.fillMaxWidth().padding(horizontal = TvSpace.page, vertical = TvSpace.m)) { content() }
    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
  }
}

// ── Sections, cards, rows ────────────────────────────────────────────────────

/** Section: small accent header with icon, then one card holding the rows. */
@Composable
fun TvSection(
    title: String?,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
  val c = Tv.colors
  Column(modifier.fillMaxWidth()) {
    if (title != null) {
      Row(
          Modifier.padding(start = TvSpace.xs, bottom = TvSpace.s),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(TvSpace.s),
      ) {
        if (icon != null) Icon(icon, null, tint = c.accent, modifier = Modifier.size(18.dp))
        Text(title, style = TvType.section, color = c.accent)
      }
    }
    TvCard(content = content)
  }
}

@Composable
fun TvCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
  val c = Tv.colors
  val m = modifier.fillMaxWidth()
  val inner: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
  if (onClick != null) {
    Surface(onClick = onClick, modifier = m, shape = TvShape.card, color = c.card, border = BorderStroke(1.dp, c.cardBorder), content = inner)
  } else {
    Surface(modifier = m, shape = TvShape.card, color = c.card, border = BorderStroke(1.dp, c.cardBorder), content = inner)
  }
}

/** Rounded square holding a row icon, tinted with the accent. */
@Composable
fun TvIconBadge(icon: ImageVector, tint: Color = Tv.colors.accent, background: Color = Tv.colors.accentSoft, size: androidx.compose.ui.unit.Dp = 40.dp) {
  Box(Modifier.size(size).clip(TvShape.badge).background(background), contentAlignment = Alignment.Center) {
    Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
  }
}

/**
 * The main list row: icon badge, title, optional subtitle, and a trailing slot (chevron by default
 * when clickable). Min height 64 dp, so the whole row is an easy touch target.
 */
@Composable
fun TvRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    iconBackground: Color? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    trailing: (@Composable () -> Unit)? = null,
    /** Custom leading content (thumbnail, avatar) when there is no [icon]. */
    leading: (@Composable () -> Unit)? = null,
) {
  val c = Tv.colors
  val alpha = if (enabled) 1f else 0.45f
  Row(
      Modifier.fillMaxWidth()
          .heightIn(min = 64.dp)
          .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
          .padding(horizontal = TvSpace.l, vertical = TvSpace.m),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(TvSpace.m),
  ) {
    if (icon != null) {
      TvIconBadge(icon, tint = (iconTint ?: c.accent).copy(alpha = alpha), background = iconBackground ?: c.accentSoft)
    } else if (leading != null) {
      leading()
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TvSpace.xxs)) {
      Text(title, style = TvType.item, color = c.textPrimary.copy(alpha = alpha))
      if (subtitle != null) Text(subtitle, style = TvType.caption, color = c.textSecondary.copy(alpha = alpha))
    }
    if (trailing != null) trailing()
    if (showChevron) {
      Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textSecondary.copy(alpha = alpha), modifier = Modifier.size(24.dp))
    }
  }
}

@Composable
fun TvSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
  TvRow(
      title = title,
      subtitle = subtitle,
      icon = icon,
      enabled = enabled,
      onClick = { onCheckedChange(!checked) },
      showChevron = false,
      trailing = { TvSwitch(checked, onCheckedChange, enabled) },
  )
}

@Composable
fun TvSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
  val c = Tv.colors
  Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      colors =
          SwitchDefaults.colors(
              checkedTrackColor = c.accent,
              checkedThumbColor = c.onAccent,
              checkedBorderColor = c.accent,
              uncheckedTrackColor = c.elevated,
              uncheckedThumbColor = c.textSecondary,
              uncheckedBorderColor = c.cardBorder,
          ),
  )
}

/** Divider between rows inside a card, inset past the icon column. */
@Composable
fun TvDivider(inset: Boolean = true) {
  HorizontalDivider(
      Modifier.padding(start = if (inset) 68.dp else 0.dp),
      thickness = 1.dp,
      color = Tv.colors.cardBorder,
  )
}

/** Plain explanatory text inside a card. */
@Composable
fun TvNote(text: String, modifier: Modifier = Modifier) {
  Text(text, style = TvType.caption, color = Tv.colors.textSecondary, modifier = modifier.padding(horizontal = TvSpace.l, vertical = TvSpace.m))
}

// ── Status ───────────────────────────────────────────────────────────────────

enum class TvStatus { OK, WARN, ERROR, NEUTRAL }

@Composable
fun tvStatusColor(status: TvStatus): Color {
  val c = Tv.colors
  return when (status) {
    TvStatus.OK -> c.success
    TvStatus.WARN -> c.warning
    TvStatus.ERROR -> c.danger
    TvStatus.NEUTRAL -> c.textSecondary
  }
}

@Composable
fun TvStatusPill(text: String, status: TvStatus) {
  val color = tvStatusColor(status)
  Row(
      Modifier.clip(TvShape.pill).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
    Text(text, style = TvType.label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

// ── Buttons ──────────────────────────────────────────────────────────────────

enum class TvButtonStyle { PRIMARY, TONAL, OUTLINE }

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: TvButtonStyle = TvButtonStyle.PRIMARY,
    enabled: Boolean = true,
) {
  val c = Tv.colors
  val bg =
      when (style) {
        TvButtonStyle.PRIMARY -> c.accent
        TvButtonStyle.TONAL -> c.accentSoft
        TvButtonStyle.OUTLINE -> Color.Transparent
      }
  val fg = if (style == TvButtonStyle.PRIMARY) c.onAccent else c.accent
  Surface(
      onClick = onClick,
      enabled = enabled,
      modifier = modifier.heightIn(min = TvSpace.touch),
      shape = TvShape.pill,
      color = bg.copy(alpha = if (enabled) bg.alpha else bg.alpha * 0.4f),
      border = if (style == TvButtonStyle.OUTLINE) BorderStroke(1.dp, c.cardBorder) else null,
  ) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = TvSpace.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpace.s, Alignment.CenterHorizontally),
    ) {
      if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
      Text(text, style = TvType.item, color = fg.copy(alpha = if (enabled) 1f else 0.5f), maxLines = 1)
    }
  }
}

// ── Pickers ──────────────────────────────────────────────────────────────────

/** Segmented single choice (pill track with a sliding highlight color). */
@Composable
fun TvSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  val c = Tv.colors
  Row(
      modifier.fillMaxWidth().clip(TvShape.pill).background(c.elevated).padding(TvSpace.xs),
      horizontalArrangement = Arrangement.spacedBy(TvSpace.xs),
  ) {
    options.forEachIndexed { i, label ->
      val isSel = i == selected
      val bg by animateColorAsState(if (isSel) c.accent else Color.Transparent, label = "seg-bg")
      val fg by animateColorAsState(if (isSel) c.onAccent else c.textSecondary, label = "seg-fg")
      Box(
          Modifier.weight(1f)
              .heightIn(min = 40.dp)
              .clip(TvShape.pill)
              .background(bg)
              .clickable(role = Role.RadioButton) { onSelect(i) },
          contentAlignment = Alignment.Center,
      ) {
        Text(label, style = TvType.label, color = fg, maxLines = 1)
      }
    }
  }
}

/** Accent color dots. [dynamic] draws the Material You option as a multicolor dot. */
@Composable
fun TvColorDots(
    colors: List<Color>,
    selected: Int,
    onSelect: (Int) -> Unit,
    dynamicIndex: Int = -1,
    labels: List<String> = emptyList(),
) {
  val c = Tv.colors
  Row(
      Modifier.fillMaxWidth().padding(horizontal = TvSpace.l, vertical = TvSpace.m),
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    colors.forEachIndexed { i, color ->
      val isSel = i == selected
      Box(
          Modifier.size(34.dp)
              .clip(CircleShape)
              .then(
                  if (i == dynamicIndex)
                      Modifier.background(Brush.sweepGradient(listOf(Color(0xFFAE94FF), Color(0xFF6FD3E6), Color(0xFF82D99D), Color(0xFFF2C25A), Color(0xFFFF92C9), Color(0xFFAE94FF))))
                  else Modifier.background(color)
              )
              .border(if (isSel) 3.dp else 0.dp, if (isSel) c.textPrimary else Color.Transparent, CircleShape)
              .clickable(role = Role.RadioButton) { onSelect(i) },
          contentAlignment = Alignment.Center,
      ) {
        if (isSel) Icon(Icons.Rounded.Check, labels.getOrNull(i), tint = Color.White, modifier = Modifier.size(18.dp))
      }
    }
  }
}

/** Monospace block for commands/identifiers, with an optional trailing action. */
@Composable
fun TvCodeBlock(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
  val c = Tv.colors
  Row(
      modifier.fillMaxWidth().clip(TvShape.inner).background(c.elevated).padding(start = TvSpace.m, end = TvSpace.xs, top = TvSpace.s, bottom = TvSpace.s),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text, style = TvType.mono, color = c.textPrimary, modifier = Modifier.weight(1f))
    if (trailing != null) trailing() else Spacer(Modifier.width(TvSpace.s))
  }
}
