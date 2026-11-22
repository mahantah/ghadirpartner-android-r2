package ir.ghadirpartner.nativeapp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Offer camera capture and the system picker through one scoped upload action. */
@Composable
internal fun ImageUploadButton(
    label: String = "بارگذاری تصویر",
    enabled: Boolean = true,
    mimeTypes: Array<String> = arrayOf("image/*"),
    singleSelection: Boolean = false,
    onSelected: (List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    val deliver by rememberUpdatedState(onSelected)
    var choosing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val photo = remember { File(context.filesDir, "image-capture/${UUID.randomUUID()}.jpg") }
    val uri = remember(photo) { FileProvider.getUriForFile(context, context.packageName + ".files", photo) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        if (captured) deliver(listOf(uri)) else photo.delete()
    }
    val receiveGallery: (List<Uri>) -> Unit = { selected ->
        selected.forEach { item ->
            try { context.contentResolver.takePersistableUriPermission(item, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* Some providers grant only temporary access. */ }
        }
        if (selected.isNotEmpty()) deliver(selected)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), receiveGallery)
    val singlePhoto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        if (selected != null) receiveGallery(listOf(selected))
    }
    GhadirButton(label, { choosing = true; error = "" }, enabled = enabled, secondary = true)
    if (choosing) AlertDialog(
        onDismissRequest = { choosing = false },
        title = { Text("بارگذاری تصویر") },
        text = { Text("گرفتن عکس جدید یا انتخاب از گالری و فایل‌ها") },
        confirmButton = { TextButton(onClick = {
            choosing = false
            if (photo.parentFile?.let { it.isDirectory || it.mkdirs() } != true) {
                error = "آماده‌سازی دوربین ممکن نشد."
            } else try { camera.launch(uri) }
            catch (_: ActivityNotFoundException) { error = "برنامه دوربین در دسترس نیست؛ از گالری انتخاب کنید." }
        }) { Text("دوربین") } },
        dismissButton = { TextButton(onClick = {
            choosing = false
            if (singleSelection) singlePhoto.launch(mimeTypes) else gallery.launch(mimeTypes)
        }) { Text("گالری و فایل‌ها") } },
    )
    if (error.isNotBlank()) ErrorBanner(error) { error = "" }
}
