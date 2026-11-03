// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.robolectric.RuntimeEnvironment
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoreTest {
    private lateinit var context: Context
    private lateinit var store: Store

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("shotegy", Context.MODE_PRIVATE).edit().clear().commit()
        store = Store(context)
    }

    private fun image(): String {
        val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
        val file = File(context.cacheDir, "fixture.png")
        file.writeBytes(png)
        return store.saveReferenceImage(Uri.fromFile(file))
    }

    @Test fun persistenceAndBackupRoundTripRetainsSharedMediaAndModelFields() {
        val imageId = image()
        val shot = ShotCard(guidance = "侧身\n抬头", technique = "逆光", scene = "海边", status = "已完成", startTime = "10:00", shotScale = "中景", referenceImages = listOf(imageId), scouts = listOf(Scout(imageId = imageId, latitude = 31.2, longitude = 121.4, heading = 90f, capturedAt = 1234L, note = "晨光", headingAccuracy = 3.0, timeZoneIdentifier = "Asia/Shanghai", lightPlans = listOf(LightPlan(planDate = "2026-11-02", recommendedStart = 123L, sunAzimuth = 120.0)))), guidanceItems = listOf(PhraseItem(text = "侧身"), PhraseItem(text = "抬头")), techniqueItems = listOf(PhraseItem(text = "逆光")))
        val project = Project(name = "海边拍摄", date = "2026-11-02", contact = "小王", scenes = listOf("海边"), cards = listOf(shot), clothingStyle = "简约", clothingColors = listOf("#123456"), stylingImages = mapOf("clothing" to listOf(imageId)), modelStyle = "自然", desiredWeather = "晴", equipment = "相机", calendarLabelOverride = "海边", calendarColorHex = "#123456")
        val folder = LibraryFolder(kind = "项目模板", name = "收藏")
        store.saveProject(project); store.saveFolder(folder)
        store.saveResource(LibraryItem(kind = "项目模板", title = "模板", folderIds = listOf(folder.id), template = project))
        val reloaded = Store(context)
        assertEquals(project, reloaded.projects.single())
        val backup = reloaded.exportBackup()
        reloaded.deleteProject(project.id)
        reloaded.restoreBackup(backup)
        val restored = Store(context)
        val p = restored.projects.single()
        assertEquals(project.name, p.name); assertEquals(project.contact, p.contact); assertEquals(project.scenes, p.scenes); assertEquals(project.clothingColors, p.clothingColors)
        val restoredImage = p.cards.single().referenceImages.single()
        assertNotEquals(imageId, restoredImage)
        assertTrue(restored.referenceImage(restoredImage)!!.isFile)
        assertEquals(restoredImage, p.cards.single().scouts.single().imageId)
        assertEquals(31.2, p.cards.single().scouts.single().latitude!!, 0.0)
        assertEquals("自然", p.modelStyle); assertEquals("晴", p.desiredWeather); assertEquals("相机", p.equipment)
        assertEquals("海边", p.calendarLabelOverride); assertEquals("#123456", p.calendarColorHex)
        assertEquals("Asia/Shanghai", p.cards.single().scouts.single().timeZoneIdentifier)
        assertEquals(shot.scouts.single().lightPlans, p.cards.single().scouts.single().lightPlans)
        assertEquals(shot.guidanceItems, p.cards.single().guidanceEntries)
        assertEquals(shot.techniqueItems, p.cards.single().techniqueEntries)
        assertEquals(listOf(restoredImage), p.stylingImages["clothing"])
        assertEquals(restoredImage, restored.resources.single().template!!.cards.single().referenceImages.single())
        assertEquals(listOf(folder.id), restored.resources.single().folderIds)
    }

    @Test fun legacyPhraseTextIsPreservedWithoutChangingOriginal() {
        val legacy = ShotCard(guidance = "一行\n另一行", technique = "顺光")
        assertEquals(listOf("一行\n另一行"), legacy.guidanceEntries.map { it.text })
        assertEquals(legacy.guidanceEntries.single().id, legacy.guidanceEntries.single().id)
        store.saveProject(Project(name = "旧项目", cards = listOf(legacy)))
        assertEquals(legacy, Store(context).projects.single().cards.single())
    }

    @Test fun malformedBackupDoesNotReplaceExistingData() {
        val project = Project(name = "原始项目")
        store.saveProject(project)
        val invalid = JSONObject(store.exportBackup()).put("projects", "not-an-array").toString()
        try { store.restoreBackup(invalid); fail("invalid backup accepted") } catch (_: Exception) { }
        assertEquals(listOf(project), store.projects.toList())
        assertEquals(listOf(project), Store(context).projects.toList())
    }

    @Test fun missingMediaInBackupDoesNotReplaceExistingData() {
        val imageId = image()
        val project = Project(name = "原始项目", cards = listOf(ShotCard(referenceImages = listOf(imageId))))
        store.saveProject(project)
        val invalid = JSONObject(store.exportBackup()).put("media", JSONObject()).toString()
        try { store.restoreBackup(invalid); fail("missing media accepted") } catch (_: Exception) { }
        assertEquals(project, store.projects.single())
        assertTrue(store.referenceImage(imageId)!!.exists())
        assertEquals(project, Store(context).projects.single())
    }

    @Test fun deletingFolderUnlinksResourcesWithoutDeletingThem() {
        val folder = LibraryFolder(name = "常用")
        val item = LibraryItem(content = "向左看", folderIds = listOf(folder.id))
        store.saveFolder(folder); store.saveResource(item); store.deleteFolder(folder.id)
        val reloaded = Store(context)
        assertTrue(reloaded.folders.isEmpty())
        assertEquals(item.copy(folderIds = emptyList()), reloaded.resources.single())
    }

    @Test fun corruptStoredDataDisablesWritesUntilRestored() {
        val goodBackup = store.exportBackup()
        context.getSharedPreferences("shotegy", Context.MODE_PRIVATE).edit().putString("projects", "broken").commit()
        val broken = Store(context)
        assertTrue(broken.isReadFailed)
        try { broken.saveProject(Project(name = "不应写入")); fail("corrupt store writable") } catch (_: IllegalStateException) { }
        assertEquals("broken", context.getSharedPreferences("shotegy", Context.MODE_PRIVATE).getString("projects", null))
        broken.restoreBackup(goodBackup)
        assertFalse(broken.isReadFailed)
        broken.saveProject(Project(name = "恢复后"))
        assertEquals("恢复后", Store(context).projects.single().name)
    }

    @Test fun atomicAppendRejectsDuplicateProjectIdsWithoutChangingState() {
        val original = Project(name = "原项目")
        store.saveProject(original)
        try { store.appendProjectsAtomically(listOf(Project(id = original.id, name = "重复"))); fail("duplicate accepted") } catch (_: IllegalArgumentException) { }
        try { val duplicate = Project(name = "重复"); store.appendProjectsAtomically(listOf(duplicate, duplicate)); fail("duplicate batch accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(listOf(original), store.projects.toList())
        assertEquals(listOf(original), Store(context).projects.toList())
    }

    @Test fun atomicAppendDoesNotPublishWhenCommitFailsAndRollsBack() {
        val original = Project(name = "原项目")
        store.saveProject(original)
        val underlying = context.getSharedPreferences("shotegy", Context.MODE_PRIVATE)
        var failOnce = true
        val failing = object : SharedPreferences by underlying {
            override fun edit(): SharedPreferences.Editor {
                val editor = underlying.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                    override fun commit(): Boolean {
                        val written = editor.commit()
                        return if (failOnce) { failOnce = false; false } else written
                    }
                }
            }
        }
        val subject = Store(context, failing)
        try { subject.appendProjectsAtomically(listOf(Project(name = "导入"))); fail("failed write accepted") } catch (_: IllegalStateException) { }
        assertEquals(listOf(original), subject.projects.toList())
        assertEquals(listOf(original), Store(context).projects.toList())
        assertFalse(subject.isReadFailed)
    }

    @Test fun atomicAppendPreservesExistingMediaReferences() {
        val imageId = image()
        val original = Project(name = "原项目", cards = listOf(ShotCard(referenceImages = listOf(imageId))))
        store.saveProject(original)
        val incoming = Project(name = "导入")
        store.appendProjectsAtomically(listOf(incoming))
        assertEquals(listOf(original, incoming), store.projects.toList())
        val reloaded = Store(context)
        assertEquals(listOf(original, incoming), reloaded.projects.toList())
        assertEquals(imageId, reloaded.projects.first().cards.single().referenceImages.single())
        assertTrue(reloaded.referenceImage(imageId)!!.exists())
    }
}
