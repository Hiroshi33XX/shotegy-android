// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import org.junit.Assert.*
import org.junit.Test

class StructuredMediaTest {
    @Test fun legacyProjectionIsStableAndDoesNotMutateSource() {
        val scout = Scout(imageId = "scout")
        val card = ShotCard(id = "card", scene = "海边", referenceImages = listOf("photo", "photo"), scouts = listOf(scout))
        val source = Project(id = "project", name = "计划", scenes = listOf("海边", "街道"), cards = listOf(card), stylingImages = mapOf("clothing" to listOf("coat")))
        val first = source.makeMigrationDraft()
        val second = source.copy(notes = "其他修改").makeMigrationDraft()
        assertEquals(first.scenes, second.scenes)
        assertEquals(first.cards, second.cards)
        assertEquals(first.projectAssets, second.projectAssets)
        assertEquals("海边", first.scenes.single { it.id == first.cards.single().sceneId }.name)
        assertEquals(listOf("photo", "photo"), first.cards.single().references.map { it.thumbnailId })
        assertNotEquals(first.cards.single().references[0].id, first.cards.single().references[1].id)
        assertEquals("scout", first.cards.single().scouts.single().thumbnailId)
        assertEquals(source, first.source)
    }

    @Test fun ambiguousOrInvalidLegacyDataIsRejected() {
        try { Project(scenes = listOf("同名", "同名")).makeMigrationDraft(); fail("duplicate scenes accepted") } catch (_: IllegalArgumentException) { }
        try { Project(cards = listOf(ShotCard(referenceImages = listOf("")))).makeMigrationDraft(); fail("empty media ID accepted") } catch (_: IllegalArgumentException) { }
    }
}
