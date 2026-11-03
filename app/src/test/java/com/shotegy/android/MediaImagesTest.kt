// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.graphics.Bitmap
import android.media.ExifInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class MediaImagesTest {
    @Test fun exifPortraitUsesOrientedDimensionsWithoutChangingTheBackupOriginal() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "portrait.jpg")
        val source = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 95, it) }; source.recycle()
        ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, "6"); saveAttributes() }
        val before = file.readBytes()
        assertEquals(.5f, referenceImageRatio(file), .001f)
        val image = decodeReferenceImage(file)!!
        assertEquals(60, image.width)
        assertEquals(120, image.height)
        image.recycle()
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun landscapeAndBrokenImageHaveSafeDisplayBounds() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "landscape.jpg")
        val source = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 95, it) }; source.recycle()
        assertEquals(2f, referenceImageRatio(file), .001f)
        assertEquals(120, decodeReferenceImage(file)!!.width)
        org.robolectric.shadows.ShadowBitmapFactory.setAllowInvalidImageData(false)
        file.writeText("invalid image")
        assertEquals(.8f, referenceImageRatio(file), .001f)
        assertNull(decodeReferenceImage(file))
    }
}
