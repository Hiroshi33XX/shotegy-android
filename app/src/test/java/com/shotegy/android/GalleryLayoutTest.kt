// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import org.junit.Assert.*
import org.junit.Test

class GalleryLayoutTest {
    @Test fun imagesFromSameScriptUseNarrowerGapsAndStayOnOneRow() {
        assertEquals(listOf(0..2), galleryRowRanges(listOf(1f, 1f, 1f), listOf("a", "a", "a"), 308f, 100f))
        assertEquals(listOf(0..1, 2..2), galleryRowRanges(listOf(1f, 1f, 1f), listOf("a", "b", "c"), 308f, 100f))
    }
    @Test fun portraitAndLandscapeImagesKeepOrderAndAreNeverDropped() {
        val ratios = listOf(.6f, 1.8f, .7f, 2f, .8f)
        val rows = galleryRowRanges(ratios, List(ratios.size) { "$it" }, 340f, 85f)
        assertEquals(ratios.indices.toList(), rows.flatMap { it.toList() })
        rows.forEach { range -> assertTrue(range.sumOf { ratios[it].toDouble() } * 85 + (range.count() - 1) * 12 <= 340 || range.count() == 1) }
        assertTrue(galleryRowRanges(emptyList(), emptyList(), 340f, 85f).isEmpty())
    }
}
