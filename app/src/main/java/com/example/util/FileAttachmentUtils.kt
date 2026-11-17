package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object FileAttachmentUtils {
    fun createCameraImageUri(context: Context): Pair<Uri, String> {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file.absolutePath
    }

    suspend fun copyUriToInternalStorage(
        context: Context,
        sourceUri: Uri,
        extension: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "vault_files").apply { mkdirs() }
            val dest = File(dir, "vault_${UUID.randomUUID()}.$extension")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            Uri.fromFile(dest).toString()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun copyFilePathToInternalStorage(
        context: Context,
        sourcePath: String,
        extension: String = "jpg"
    ): String? = withContext(Dispatchers.IO) {
        try {
            val src = File(sourcePath)
            if (!src.exists()) return@withContext null
            val dir = File(context.filesDir, "vault_files").apply { mkdirs() }
            val dest = File(dir, "vault_${UUID.randomUUID()}.$extension")
            src.copyTo(dest, overwrite = true)
            Uri.fromFile(dest).toString()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadScaledBitmapFromUri(
        context: Context,
        uriString: String,
        maxDimension: Int = 1600
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            var sampleSize = 1
            val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxSide / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            val rotationDegrees = context.contentResolver.openInputStream(uri)?.use { exifStream ->
                when (ExifInterface(exifStream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            if (rotationDegrees == 0f) {
                decoded
            } else {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun renderPdfPagesToBitmaps(
        context: Context,
        uriString: String,
        maxPages: Int = 2
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            val uri = Uri.parse(uriString)
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext emptyList()
            renderer = PdfRenderer(pfd)
            val count = minOf(renderer.pageCount, maxPages)
            for (i in 0 until count) {
                renderer.openPage(i).use { page ->
                    val targetWidth = 1240
                    val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                    val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                renderer?.close()
            } catch (_: Exception) {
            }
            try {
                pfd?.close()
            } catch (_: Exception) {
            }
        }
        bitmaps
    }
}
