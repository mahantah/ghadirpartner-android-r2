package ir.ghadirpartner.nativeapp

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Prepare one payment image for either checkout or an existing order. */
internal class PaymentProofEncoder(private val resolver: ContentResolver) {
    /** Downsample and encode the selected image without loading full-resolution pixels. */
    suspend fun encode(uri: Uri): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "تصویر معتبر نیست" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (bounds.outWidth / inSampleSize > 1600 || bounds.outHeight / inSampleSize > 1600) inSampleSize *= 2
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalArgumentException("تصویر خوانده نشد")
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            require(stream.size() < 1_000_000) { "حجم تصویر زیاد است؛ تصویر کوچک‌تری انتخاب کنید" }
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }
}
