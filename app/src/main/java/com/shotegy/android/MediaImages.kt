// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

private fun imageOrientation(file: File) = runCatching {
    ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

internal fun referenceImageRatio(file: File): Float {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return .8f
    val ratio = bounds.outWidth.toFloat() / bounds.outHeight
    return if (imageOrientation(file) in 5..8) 1f / ratio else ratio
}

/** Leave the stored original intact for iOS export; orient only the display bitmap. */
internal fun decodeReferenceImage(file: File): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1000 || bounds.outHeight / sample > 1000) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
    val matrix = Matrix().apply {
        when (imageOrientation(file)) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }
    if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
}.getOrNull()
