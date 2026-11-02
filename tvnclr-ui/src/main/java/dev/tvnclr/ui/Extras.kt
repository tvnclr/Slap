package dev.tvnclr.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ── Dialogs ──────────────────────────────────────────────────────────────────

/** Confirmation dialog. [destructive] colors the confirm action with the danger tone. */
@Composable
fun TvConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    icon: ImageVector? = if (destructive) Icons.Rounded.WarningAmber else null,
) {
  val c = Tv.colors
  AlertDialog(
      onDismissRequest = onDismiss,
      containerColor = c.card,
      shape = TvShape.card,
      icon = icon?.let { { Icon(it, null, tint = if (destructive) c.danger else c.accent) } },
      title = { Text(title, style = TvType.title, color = c.textPrimary) },
      text = { Text(text, style = TvType.body, color = c.textSecondary) },
      confirmButton = {
        TvTextButton(confirmText, onConfirm, color = if (destructive) c.danger else c.accent)
      },
      dismissButton = dismissText?.let { { TvTextButton(it, onDismiss, color = c.textSecondary) } },
  )
}

@Composable
fun TvTextButton(text: String, onClick: () -> Unit, color: Color = Tv.colors.accent, enabled: Boolean = true) {
  Surface(onClick = onClick, enabled = enabled, shape = TvShape.pill, color = Color.Transparent) {
    Text(
        text,
        style = TvType.item,
        color = color.copy(alpha = if (enabled) 1f else 0.4f),
        modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = TvSpace.l, vertical = 10.dp),
    )
  }
}

/** Single text input dialog with validation. */
@Composable
fun TvInputDialog(
    title: String,
    placeholder: String,
    confirmText: String,
    dismissText: String,
    isValid: (String) -> Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    monospace: Boolean = true,
) {
  val c = Tv.colors
  var value by remember { mutableStateOf("") }
  AlertDialog(
      onDismissRequest = onDismiss,
      containerColor = c.card,
      shape = TvShape.card,
      title = { Text(title, style = TvType.title, color = c.textPrimary) },
      text = {
        TvTextField(value, { value = it }, placeholder = placeholder, monospace = monospace, singleLine = false, minHeight = 96)
      },
      confirmButton = { TvTextButton(confirmText, { onConfirm(value) }, enabled = value.isNotBlank() && isValid(value)) },
      dismissButton = { TvTextButton(dismissText, onDismiss, color = c.textSecondary) },
  )
}

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    monospace: Boolean = false,
    singleLine: Boolean = true,
    minHeight: Int = 0,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
  val c = Tv.colors
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = modifier.fillMaxWidth().heightIn(min = minHeight.dp),
      enabled = enabled,
      label = label?.let { { Text(it) } },
      placeholder = placeholder?.let { { Text(it, color = c.textSecondary) } },
      leadingIcon = leadingIcon?.let { { Icon(it, null, tint = c.textSecondary) } },
      singleLine = singleLine,
      textStyle = if (monospace) TvType.mono.copy(color = c.textPrimary, fontSize = TvType.body.fontSize) else TvType.body.copy(color = c.textPrimary),
      shape = TvShape.inner,
      colors =
          OutlinedTextFieldDefaults.colors(
              focusedBorderColor = c.accent,
              unfocusedBorderColor = c.cardBorder,
              focusedLabelColor = c.accent,
              cursorColor = c.accent,
              focusedContainerColor = c.elevated.copy(alpha = 0.5f),
              unfocusedContainerColor = c.elevated.copy(alpha = 0.5f),
          ),
  )
}

@Composable
fun TvSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
  TvTextField(value, onValueChange, placeholder = placeholder, leadingIcon = Icons.Rounded.Search)
}

// ── Menus ────────────────────────────────────────────────────────────────────

data class TvMenuItem(
    val text: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val subtitle: String? = null,
    val onClick: () -> Unit,
)

/**
 * Menu as a dialog of card rows (icon, title, subtitle), used instead of the stock Android menus
 * for item actions and pickers. Scrolls when the list is long.
 */
@Composable
fun TvMenuDialog(title: String?, items: List<TvMenuItem>, onDismiss: () -> Unit) {
  val c = Tv.colors
  androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
    Surface(shape = TvShape.card, color = c.card, border = BorderStroke(1.dp, c.cardBorder)) {
      Column(
          Modifier.heightIn(max = 560.dp)
              .verticalScroll(androidx.compose.foundation.rememberScrollState())
              .padding(vertical = TvSpace.s)
      ) {
        if (title != null) {
          Text(title, style = TvType.title, color = c.textPrimary, modifier = Modifier.padding(horizontal = TvSpace.l, vertical = TvSpace.m))
        }
        items.forEachIndexed { i, item ->
          if (i > 0) TvDivider()
          TvRow(
              title = item.text,
              subtitle = item.subtitle,
              icon = item.icon,
              iconTint = if (item.destructive) c.danger else null,
              iconBackground = if (item.destructive) c.danger.copy(alpha = 0.14f) else null,
              onClick = {
                onDismiss()
                item.onClick()
              },
              showChevron = false,
          )
        }
      }
    }
  }
}

/** Overflow (⋮) button with a card-styled menu. */
@Composable
fun TvOverflowMenu(items: List<TvMenuItem>, contentDescription: String = androidx.compose.ui.res.stringResource(R.string.tv_options)) {
  val c = Tv.colors
  var open by remember { mutableStateOf(false) }
  Box {
    TvIconButton(Icons.Rounded.MoreVert, contentDescription, { open = true })
    DropdownMenu(
        expanded = open,
        onDismissRequest = { open = false },
        containerColor = c.card,
        shape = TvShape.inner,
        border = BorderStroke(1.dp, c.cardBorder),
    ) {
      items.forEach { item ->
        val color = if (item.destructive) c.danger else c.textPrimary
        DropdownMenuItem(
            text = { Text(item.text, style = TvType.body, color = color) },
            leadingIcon = item.icon?.let { { Icon(it, null, tint = if (item.destructive) c.danger else c.accent) } },
            onClick = {
              open = false
              item.onClick()
            },
        )
      }
    }
  }
}

// ── Choice & empty states ────────────────────────────────────────────────────

/** Selectable card (radio semantics) with icon, title and description. */
@Composable
fun TvChoiceCard(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
  val c = Tv.colors
  val alpha = if (enabled) 1f else 0.45f
  Surface(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.fillMaxWidth(),
      shape = TvShape.card,
      color = if (selected) c.accentSoft else c.card,
      border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.cardBorder),
  ) {
    Row(
        Modifier.padding(TvSpace.l),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(TvSpace.m),
    ) {
      TvIconBadge(icon, tint = c.accent.copy(alpha = alpha), background = if (selected) c.accent.copy(alpha = 0.2f) else c.accentSoft)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TvSpace.xs)) {
        Text(title, style = TvType.title, color = c.textPrimary.copy(alpha = alpha))
        Text(description, style = TvType.caption, color = c.textSecondary.copy(alpha = alpha))
      }
      Box(
          Modifier.size(22.dp)
              .clip(CircleShape)
              .border(2.dp, if (selected) c.accent else c.textSecondary.copy(alpha = alpha), CircleShape),
          contentAlignment = Alignment.Center,
      ) {
        if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(c.accent))
      }
    }
  }
}

/** Empty / error state inside a card. */
@Composable
fun TvEmptyState(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
  val c = Tv.colors
  Column(
      modifier.fillMaxWidth().padding(vertical = TvSpace.xxl, horizontal = TvSpace.l),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(TvSpace.m),
  ) {
    TvIconBadge(icon, tint = c.textSecondary, background = c.elevated, size = 56.dp)
    Text(text, style = TvType.body, color = c.textSecondary, textAlign = TextAlign.Center)
  }
}

/** Inline callout for warnings / tips inside or outside cards. */
@Composable
fun TvCallout(text: String, status: TvStatus = TvStatus.WARN, icon: ImageVector = Icons.Rounded.WarningAmber) {
  val color = tvStatusColor(status)
  Row(
      Modifier.fillMaxWidth().clip(TvShape.inner).background(color.copy(alpha = 0.10f)).padding(TvSpace.m),
      horizontalArrangement = Arrangement.spacedBy(TvSpace.m),
      verticalAlignment = Alignment.Top,
  ) {
    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
    Text(text, style = TvType.caption, color = Tv.colors.textPrimary, modifier = Modifier.weight(1f))
  }
}

/** Single-choice chips that wrap onto several lines (for option sets too big for TvSegmented). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TvChoiceChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  val c = Tv.colors
  androidx.compose.foundation.layout.FlowRow(
      modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(TvSpace.s),
      verticalArrangement = Arrangement.spacedBy(TvSpace.s),
  ) {
    options.forEachIndexed { i, label ->
      val sel = i == selected
      Surface(
          onClick = { onSelect(i) },
          shape = TvShape.pill,
          color = if (sel) c.accent else c.elevated,
          border = BorderStroke(1.dp, if (sel) c.accent else c.cardBorder),
      ) {
        Text(
            label,
            style = TvType.label,
            color = if (sel) c.onAccent else c.textSecondary,
            modifier = Modifier.heightIn(min = 36.dp).padding(horizontal = TvSpace.l, vertical = 9.dp),
        )
      }
    }
  }
}
