package ir.ghadirpartner.nativeapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decode photos and render the first page of a selected PDF outside the UI thread. */
internal class ProofPreviewReader(private val context: Context) {
    /** Open a preview while bounding the bitmap size on both document types. */
    suspend fun read(data: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = Base64.decode(data, Base64.DEFAULT)
            val pdf = bytes.take(5).toByteArray().decodeToString() == "%PDF-"
            if (pdf) readPdf(bytes) else readPhoto(bytes)
        }.getOrNull()
    }

    private fun readPhoto(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val dimension = maxOf(bounds.outWidth, bounds.outHeight)
        if (dimension < 1) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (dimension / inSampleSize > BuildConfig.PROOF_IMAGE_DIMENSION) inSampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun readPdf(bytes: ByteArray): Bitmap? {
        val file = java.io.File.createTempFile("proof-preview-", ".pdf", context.cacheDir)
        try {
            file.writeBytes(bytes)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) return null
                    renderer.openPage(0).use { page ->
                        val scale = BuildConfig.PROOF_IMAGE_DIMENSION.toFloat() / maxOf(page.width, page.height)
                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } finally {
            file.delete()
        }
    }
}

/** Show the actual selected document and open an enlarged preview on demand. */
@Composable
internal fun ProofThumbnail(data: String, label: String, onReadable: (() -> Unit)? = null) {
    val context = LocalContext.current
    val reader = remember(context) { ProofPreviewReader(context) }
    val result by produceState<Result<Bitmap?>?>(null, data) { value = Result.success(reader.read(data)) }
    var zoom by remember(data) { mutableStateOf(false) }
    val bitmap = result?.getOrNull()
    val onReady by rememberUpdatedState(onReadable)
    LaunchedEffect(bitmap) {
        if (bitmap != null) onReady?.invoke()
    }
    val image = bitmap?.asImageBitmap()
    if (image == null) {
        Text(if (result == null) "در حال آماده‌سازی پیش‌نمایش…" else "پیش‌نمایش در دسترس نیست", color = Muted)
    } else {
        Image(image, label, Modifier.fillMaxWidth().heightIn(max = 165.dp).clickable { zoom = true }, contentScale = ContentScale.Fit)
        if (zoom) AlertDialog(
            onDismissRequest = { zoom = false },
            title = { Text(label) },
            text = { Image(image, label, Modifier.fillMaxWidth().heightIn(max = 460.dp), contentScale = ContentScale.Fit) },
            confirmButton = { TextButton(onClick = { zoom = false }) { Text("بستن") } },
        )
    }
}

/** Display each selected file with its own editable explanation. */
@Composable
internal fun SelectedProofReview(
    label: String,
    files: List<SelectedProof>,
    onNote: (Int, String) -> Unit,
    onRemove: ((Int) -> Unit)? = null,
    enabled: Boolean = true,
    details: (@Composable (Int) -> Unit)? = null,
) {
    if (files.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        files.forEachIndexed { index, file ->
            key(file.data) {
                GlassSurface {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val title = "$label ${faNumber(index + 1)}"
                        Text(title, color = Ink)
                        ProofThumbnail(file.data, title)
                        details?.invoke(index)
                        DesignField(
                            "توضیح $title", file.note,
                            { onNote(index, it.take(BuildConfig.PROOF_MAX_NOTE_CHARS)) },
                            "اختیاری", singleLine = false, enabled = enabled,
                        )
                        if (onRemove != null) TextButton(
                            enabled = enabled,
                            onClick = { onRemove(index) },
                        ) { Text("حذف فایل") }
                    }
                }
            }
        }
    }
}
