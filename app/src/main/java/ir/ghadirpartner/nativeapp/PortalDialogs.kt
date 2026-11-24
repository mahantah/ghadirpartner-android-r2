package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Present long choice lists with native touch scrolling. */
@Composable
internal fun <T> ScrollableChoiceDialog(
    title: String,
    options: List<T>,
    display: (T) -> String,
    onClose: () -> Unit,
    onSelect: (T) -> Unit,
) {
    PortalPageDialog(title, onClose) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(options) { option ->
                DropdownMenuItem(text = { Text(display(option)) }, onClick = { onSelect(option); onClose() })
            }
        }
    }
}
