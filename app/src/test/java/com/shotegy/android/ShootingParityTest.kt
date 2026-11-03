// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import org.junit.Assert.*
import org.junit.Test

class ShootingParityTest {
    private val a = ShotCard(id = "a", scene = "河岸", shotScale = "中景", referenceImages = listOf("one", "two"))
    private val b = ShotCard(id = "b", scene = "桥下", shotScale = "中景", status = "已完成")
    private val c = ShotCard(id = "c", scene = "河岸", shotScale = "远景")
    private val d = ShotCard(id = "d")
    private val project = Project(scenes = listOf("桥下", "河岸"), cards = listOf(a, b, c, d), lastShotCardId = "a")

    @Test fun scaleModeCrossGroupsScenesAndRetainsOriginalScriptIndices() {
        val sections = scriptGallerySections(project, project.cards, false)
        assertEquals(scriptScales + "", sections.map { it.name })
        val medium = sections.single { it.name == "中景" }
        assertEquals(listOf("桥下", "河岸"), medium.groups.map { it.name })
        assertEquals(listOf(b, a), medium.groups.flatMap { it.cards })
        assertEquals(0, project.cards.indexOf(medium.groups.last().cards.single()))
        assertEquals(listOf(d), sections.last().groups.single().cards)
    }

    @Test fun sceneModeCrossGroupsScalesAndKeepsEmptySectionsLikeIOS() {
        val sections = scriptGallerySections(project.copy(scenes = project.scenes + "草地"), project.cards, true)
        assertEquals(listOf("桥下", "河岸", "草地", ""), sections.map { it.name })
        assertEquals(listOf("中景", "远景"), sections[1].groups.map { it.name })
        assertTrue(sections[2].groups.isEmpty())
    }

    @Test fun pendingFilterDoesNotRenumberOrLoseCardsWithoutImages() {
        val pending = project.cards.filter { it.status == "未拍" }
        val groups = scriptGallerySections(project, pending, false).flatMap { it.groups }
        assertEquals(setOf("a", "c", "d"), groups.flatMap { it.cards }.map { it.id }.toSet())
        assertEquals(listOf("a", "c", "d"), groups.flatMap { it.cards }.sortedBy { project.cards.indexOf(it) }.map { it.id })
    }

    @Test fun tappingSecondImageStartsExecutionSheetOnThatImage() {
        assertEquals(1, shootingReferencePage(a, "two"))
        assertEquals(0, shootingReferencePage(a, null))
        assertEquals(0, shootingReferencePage(a, "missing"))
        assertEquals(0, shootingReferencePage(d, null))
    }

    @Test fun executionStatusChangesOnlyTheSelectedCardAndHonorsLock() {
        val updated = shootingStatusUpdate(project, "a", "已跳过", false)
        assertEquals("已跳过", updated.cards[0].status)
        assertEquals(project.cards.drop(1), updated.cards.drop(1))
        assertEquals(project.lastShotCardId, updated.lastShotCardId)
        assertEquals(project, shootingStatusUpdate(project, "a", "已完成", true))
        assertEquals(project, shootingStatusUpdate(project, "missing", "已完成", false))
        assertEquals(project, shootingStatusUpdate(project, "a", "invalid", false))
    }
}
