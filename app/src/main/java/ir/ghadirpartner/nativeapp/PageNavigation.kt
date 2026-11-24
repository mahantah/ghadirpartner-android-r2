package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/** Give long lists explicit page controls while keeping list position and item keys. */
@Composable
internal fun ButtonPagedList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val overlap = with(LocalDensity.current) { BuildConfig.PAGE_OVERLAP_DP.dp.roundToPx() }
    fun turnPage(direction: Int) {
        val distance = pageDistance(state.layoutInfo.viewportSize.height, overlap)
        scope.launch { state.scrollBy((direction * distance).toFloat()) }
    }
    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            userScrollEnabled = false,
            content = content,
        )
        PageNavigation(state.canScrollBackward, state.canScrollForward, ::turnPage)
    }
}

/** Keep every form field composed as the user moves between its visible pages. */
@Composable
internal fun ButtonPagedColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val overlap = with(LocalDensity.current) { BuildConfig.PAGE_OVERLAP_DP.dp.roundToPx() }
    fun turnPage(direction: Int) {
        val distance = pageDistance(state.viewportSize, overlap)
        scope.launch { state.scrollBy((direction * distance).toFloat()) }
    }
    Column(modifier) {
        Column(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(state, enabled = false),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
        if (state.viewportSize > 0) PageNavigation(state.canScrollBackward, state.canScrollForward, ::turnPage)
    }
}

private fun pageDistance(viewport: Int, overlap: Int): Int =
    (viewport - overlap.coerceAtMost(viewport / 2)).coerceAtLeast(1)

@Composable
private fun PageNavigation(previous: Boolean, next: Boolean, onTurn: (Int) -> Unit) {
    if (!previous && !next) return
    Surface(color = AppSurface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { onTurn(-1) }, enabled = previous) { Text("صفحهٔ قبل") }
            TextButton(onClick = { onTurn(1) }, enabled = next) { Text("صفحهٔ بعد") }
        }
    }
}

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

/** Present long choice lists with the same button navigation as other dialogs. */
@Composable
internal fun <T> PagedChoiceDialog(
    title: String,
    options: List<T>,
    display: (T) -> String,
    onClose: () -> Unit,
    onSelect: (T) -> Unit,
) {
    PortalPageDialog(title, onClose) {
        ButtonPagedList(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(options) { option ->
                DropdownMenuItem(text = { Text(display(option)) }, onClick = { onSelect(option); onClose() })
            }
        }
    }
}
