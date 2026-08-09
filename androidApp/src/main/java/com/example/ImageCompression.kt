package com.example

import android.content.Context
import android.net.Uri

/**
 * Shared image downscaling for anything uploaded to Firebase Storage — team chat
 * photos and feedback screenshots. Both storage rules cap an upload at 2 MB, and
 * a modern phone camera clears that several times over, so every upload path has
 * to compress first rather than discovering the limit at the network layer.
 */

private const val MAX_UPLOAD_DIMENSION = 800

/**
 * Downscale [uri] to a JPEG small enough to upload.
 *
 * Throws on hard failure (the caller reports the reason); returns null only when
 * the source can't be decoded into a bitmap (e.g. corrupt or non-image content).
 * Callers must run this off the main thread — a large bitmap decode will ANR.
 */
internal fun compressImageForUpload(
    context: Context,
    uri: Uri,
    maxDim: Int = MAX_UPLOAD_DIMENSION,
    quality: Int = 60
): ByteArray? {
    val decoded = decodeDownsampledBitmap(context, uri, maxDim) ?: return null

    val scale = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height, 1f)
    val w = maxOf(1, (decoded.width * scale).toInt())
    val h = maxOf(1, (decoded.height * scale).toInt())
    val scaled = android.graphics.Bitmap.createScaledBitmap(decoded, w, h, true)
    val out = java.io.ByteArrayOutputStream()
    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
    if (scaled !== decoded) scaled.recycle()
    decoded.recycle()
    return out.toByteArray()
}

/**
 * Decode a downsampled bitmap. On API 28+ ImageDecoder is used because it
 * handles modern camera formats (HEIC/HEIF, WebP) that BitmapFactory often
 * can't, which is the usual cause of "failed to process image". A software
 * allocator is required so the result can be re-compressed (hardware bitmaps
 * can't be read back). BitmapFactory's two-pass decode is the fallback.
 */
private fun decodeDownsampledBitmap(
    context: Context,
    uri: Uri,
    maxDim: Int
): android.graphics.Bitmap? {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        return android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            val srcW = info.size.width
            val srcH = info.size.height
            var sample = 1
            while (srcW / sample > maxDim * 2 || srcH / sample > maxDim * 2) sample *= 2
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }
    }

    // Pass 1: read only the bounds so we never load the full-res bitmap into memory.
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, bounds)
    } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // Pass 2: decode downsampled so memory usage stays bounded regardless of source size.
    var sample = 1
    while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) {
        sample *= 2
    }
    val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts)
    }
}
