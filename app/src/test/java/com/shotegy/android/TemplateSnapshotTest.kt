// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.Base64
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TemplateSnapshotTest {
    @Test fun templateEditorKeepsIdentitiesAndReusableInformationWhileRemovingExecutionData() {
        val card=ShotCard(status="已完成",startTime="16:00",endTime="17:00",scouts=listOf(Scout(imageId="example")))
        val original=Project(clientName="客户",contact="电话",date="2026-11-02",startTime="09:00",endTime="18:00",location="地址",lastShotCardId=card.id,calendarLabelOverride="日程",calendarColorHex="#123456",modelStyle="自然",equipment="相机",cards=listOf(card))
        val cleaned=templateCreativeInfo(original)
        assertEquals(original.id,cleaned.id)
        assertEquals(card.id,cleaned.cards.single().id)
        assertEquals("",cleaned.clientName);assertEquals("",cleaned.contact);assertEquals("",cleaned.date);assertEquals("",cleaned.location)
        assertNull(cleaned.calendarLabelOverride);assertNull(cleaned.calendarColorHex)
        assertEquals("自然",cleaned.modelStyle);assertEquals("相机",cleaned.equipment)
        assertEquals("未拍",cleaned.cards.single().status);assertTrue(cleaned.cards.single().scouts.isEmpty())
        assertEquals("16:00",cleaned.cards.single().startTime)
    }
    private lateinit var context: Context
    private lateinit var store: Store

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("shotegy", Context.MODE_PRIVATE).edit().clear().commit()
        store = Store(context)
    }

    private fun image(): String {
        val file = File(context.cacheDir, "template-fixture.png")
        file.writeBytes(Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="))
        return store.saveReferenceImage(Uri.fromFile(file))
    }

    @Test fun snapshotAndCreatedProjectHaveIndependentIdentitiesAndKeepOrderedContent() {
        val image = image()
        val guidance = listOf(PhraseItem(text = "看左边"), PhraseItem(text = "轻抬头"))
        val methods = listOf(PhraseItem(text = "逆光"), PhraseItem(text = "低机位"))
        val original = Project(name = "原项目", clientName = "客户", contact = "私人电话", date = "2026-11-02", location = "具体地址", calendarLabelOverride = "私人日程", scenes = listOf("室外", "室内"), stylingImages = mapOf("clothing" to listOf(image)), cards = listOf(ShotCard(scene = "室内", status = "已完成", guidanceItems = guidance, techniqueItems = methods, referenceImages = listOf(image), scouts = listOf(Scout(imageId = image)))))
        store.saveProject(original)

        val snapshot = templateSnapshot(original)
        assertNotEquals(original.id, snapshot.id)
        assertEquals("", snapshot.clientName); assertEquals("", snapshot.contact); assertEquals("", snapshot.date); assertEquals("", snapshot.location)
        assertNull(snapshot.calendarLabelOverride)
        assertEquals(original.scenes, snapshot.scenes)
        val shot = snapshot.cards.single()
        assertNotEquals(original.cards.single().id, shot.id)
        assertEquals("未拍", shot.status); assertTrue(shot.scouts.isEmpty())
        assertEquals(guidance.map { it.text }, shot.guidanceEntries.map { it.text })
        assertEquals(methods.map { it.text }, shot.techniqueEntries.map { it.text })
        assertTrue(shot.guidanceEntries.zip(guidance).all { (a, b) -> a.id != b.id })
        assertEquals(listOf(image), shot.referenceImages)

        val template = LibraryItem(kind = "项目模板", title = "模板", template = snapshot)
        store.saveResource(template)
        val edited = snapshot.copy(scenes = snapshot.scenes.reversed(), cards = listOf(shot.copy(guidanceItems = shot.guidanceEntries.reversed())))
        store.saveResource(template.copy(template = edited))
        assertEquals(original, Store(context).projects.single())

        val created = projectFromTemplate(edited, "新项目")
        assertNotEquals(edited.id, created.id)
        assertNotEquals(edited.cards.single().id, created.cards.single().id)
        assertEquals(listOf("室内", "室外"), created.scenes)
        assertEquals(listOf("轻抬头", "看左边"), created.cards.single().guidanceEntries.map { it.text })
        assertEquals(methods.map { it.text }, created.cards.single().techniqueEntries.map { it.text })
        assertTrue(created.cards.single().guidanceEntries.zip(edited.cards.single().guidanceEntries).all { (a, b) -> a.id != b.id })
        assertEquals(listOf(image), created.cards.single().referenceImages)
        assertEquals(listOf(image), created.stylingImages["clothing"])

        store.saveProject(created)
        val backup = store.exportBackup()
        store.restoreBackup(backup)
        val restored = Store(context)
        val restoredTemplate = restored.resources.single().template!!
        val restoredCreated = restored.projects.first { it.id == created.id }
        assertEquals(edited.scenes, restoredTemplate.scenes)
        assertEquals(created.cards.single().guidanceEntries, restoredCreated.cards.single().guidanceEntries)
        assertEquals(methods.map { it.text }, restoredTemplate.cards.single().techniqueEntries.map { it.text })
        val restoredImage = restoredCreated.cards.single().referenceImages.single()
        assertTrue(restored.referenceImage(restoredImage)!!.isFile)
        assertEquals(restoredImage, restoredTemplate.cards.single().referenceImages.single())
        assertEquals(listOf(restoredImage), restoredCreated.stylingImages["clothing"])
    }

    @Test fun legacyMultilinePhrasesRemainIntactAcrossTemplateCreation() {
        val original = Project(name = "旧项目", cards = listOf(ShotCard(guidance = "一行\n另一行", technique = "顺光")))
        val template = templateSnapshot(original)
        val created = projectFromTemplate(template, "新项目")
        assertEquals(listOf("一行\n另一行"), created.cards.single().guidanceEntries.map { it.text })
        assertEquals(listOf("顺光"), created.cards.single().techniqueEntries.map { it.text })
    }
}
