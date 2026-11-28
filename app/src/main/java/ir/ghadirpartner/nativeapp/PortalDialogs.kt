package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Show a bounded, keyboard-aware page over the screen that opened it. */
@Composable
internal fun PortalPageDialog(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.padding(horizontal = 14.dp).widthIn(max = 680.dp).fillMaxWidth().fillMaxHeight(BuildConfig.PAGE_DIALOG_HEIGHT_FRACTION).imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = ApprovedPalette.surface,
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "بستن") }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    content()
                }
            }
        }
    }
}

/** Open a compact list of choice cards rather than a platform dropdown menu. */
@Composable
internal fun <T> ScrollableChoiceDialog(
    title: String,
    options: List<T>,
    display: (T) -> String,
    onClose: () -> Unit,
    onSelect: (T) -> Unit,
    icon: ImageVector = Icons.Default.Apps,
    emptyText: String = "گزینه‌ای در دسترس نیست.",
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = lerp(AppSurface, Orange, .08f),
        title = { Text(title, color = Ink) },
        text = {
            LazyColumn(Modifier.heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (options.isEmpty()) item { Text(emptyText, color = Muted) }
                items(options) { option ->
                    ChoiceOptionCard(display(option), icon) { onSelect(option); onClose() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("بستن") } },
    )
}

/** Show an icon and chevron for each option, including long names and addresses. */
@Composable
internal fun ChoiceOptionCard(label: String, icon: ImageVector, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        color = lerp(AppSurface, Orange, .12f),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, Orange.copy(alpha = .28f)),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SharpPortalIcon(icon, Orange, Modifier.size(30.dp))
            Text(label, Modifier.weight(1f), color = Ink)
            Icon(Icons.Default.ChevronLeft, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Show the same tinted trigger for lists, searchable banks and the calendar. */
@Composable
internal fun ChoiceTrigger(
    label: String,
    value: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        color = lerp(AppSurface, Orange, if (enabled) .09f else .04f),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, if (enabled) Orange.copy(alpha = .32f) else Border),
    ) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SharpPortalIcon(icon, Orange, Modifier.size(31.dp))
            Text(value, Modifier.weight(1f), color = if (enabled) Ink else Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ExpandMore, null, tint = Muted)
        }
    }
}

/** Select a value with the shared device-style picker. */
@Composable
internal fun <T> DropdownField(
    title: String,
    value: String,
    options: List<T>,
    display: (T) -> String,
    icon: ImageVector = Icons.Default.Apps,
    emptyText: String = "گزینه‌ای در دسترس نیست.",
    showLabel: Boolean = true,
    onSelect: (T) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (showLabel) Text(title, color = Muted, style = MaterialTheme.typography.labelMedium)
        ChoiceTrigger(title, value, icon) { open = true }
    }
    if (open) ScrollableChoiceDialog(title, options, display, { open = false }, onSelect, icon, emptyText)
}
