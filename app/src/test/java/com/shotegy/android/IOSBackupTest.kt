// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.*
import java.util.UUID
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IOSBackupTest {
    private lateinit var context: Context
    private lateinit var store: Store
    private val sceneA = "10000000-0000-0000-0000-000000000001"
    private val sceneB = "10000000-0000-0000-0000-000000000002"
    private val resourceId = "20000000-0000-0000-0000-000000000001"
    private val folderId = "30000000-0000-0000-0000-000000000001"
    private val png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("shotegy", 0).edit().clear().commit()
        store = Store(context)
    }
    private fun arr(vararg values: Any) = JSONArray(values.toList())
    // Field names and versions match iOS BackupService.projectManifest and ProjectTemplatePayload.
    private fun manifest(): JSONObject {
        val plan = JSONObject().put("lightType", "back").put("planDate", "2026-11-02T00:00:00Z").put("tolerance", 20).put("recommendedStart", "2026-11-02T08:00:00Z").put("recommendedEnd", "2026-11-02T08:30:00Z").put("sunAzimuth", 240.5).put("sunElevation", 17.2)
        val scout = JSONObject().put("imagePath", "old/scout.png").put("thumbnailPath", "old/thumb.png").put("latitude", 31.2).put("longitude", 121.4).put("heading", 183.123456789).put("headingAccuracy", 4.5).put("timeZoneIdentifier", "Asia/Shanghai").put("capturedAt", "2026-11-01T10:00:00Z").put("note", "踩点备注").put("lightPlan", plan)
        val card = JSONObject().put("sortOrder", 0).put("sceneID", sceneB).put("shotScaleRawValue", "close").put("startMinutes", 900).put("endMinutes", 930).put("timeNote", "黄昏").put("status", "skipped").put("guidance", arr("引导一", "引导二")).put("techniques", arr("方法一")).put("references", arr(JSONObject().put("imagePath", "old/full.png").put("thumbnailPath", "old/thumb.png"))).put("scouts", arr(scout))
        val project = JSONObject().put("name", "互通策划").put("theme", "夜景").put("style", "自然").put("clientName", "客户").put("shootDate", "2026-11-02T12:34:56Z").put("startMinutes", 900).put("endMinutes", 1020).put("location", "公园").put("contact", "123").put("wardrobe", "白衣").put("makeup", "自然").put("hair", "长发").put("props", "花").put("notes", "备注").put("modelStyle", "松弛").put("desiredWeather", "阴天").put("equipment", "相机").put("clothingStyle", "文艺").put("accessories", "耳环").put("shoes", "白鞋").put("clothingColorHexes", arr("#FFFFFF")).put("sceneColorHex", "#123456").put("clothingStyleIsCustom", true).put("calendarLabelOverride", "夜景").put("calendarColorHex", "#abcdef").put("status", "shooting").put("archived", false)
        return JSONObject().put("format", "shotegy-project").put("version", 3).put("project", project).put("cards", arr(card)).put("scenes", arr(JSONObject().put("id", sceneA).put("name", "同名场景").put("sortOrder", 0), JSONObject().put("id", sceneB).put("name", "同名场景").put("sortOrder", 1))).put("projectAssets", arr(JSONObject().put("kindRawValue", "clothing").put("sortOrder", 0).put("imagePath", "old/style.png").put("thumbnailPath", "old/thumb.png")))
    }
    private fun zip(name: String, manifest: JSONObject, media: Boolean = true): ByteArray = ByteArrayOutputStream().also { bytes -> ZipOutputStream(bytes).use { z ->
        z.putNextEntry(ZipEntry(name)); z.write(manifest.toString().toByteArray()); z.closeEntry()
        if (media) listOf("full.png", "thumb.png", "scout.png", "style.png").forEachIndexed { index, file -> z.putNextEntry(ZipEntry("media/$file")); z.write(png + byteArrayOf(index.toByte())); z.closeEntry() }
    } }.toByteArray()
    private fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>(); ZipInputStream(bytes.inputStream()).use { z -> while (true) { val entry = z.nextEntry ?: break; result[entry.name] = z.readBytes(); z.closeEntry() } }; return result
    }
    private fun export(kind: String): ByteArray = ByteArrayOutputStream().also { IOSBackup.write(it, store, store.projects.toList(), store.resources.toList(), store.folders.toList(), kind) }.toByteArray()
    private fun prepare(bytes: ByteArray) = IOSBackup.prepare(bytes.inputStream(), context.cacheDir, store)

    @Test fun iosProjectCanBeEditedAndExportedWithImagesThumbnailsSceneIdentityAndPlans() {
        val original = manifest()
        prepare(zip("project.json", original)).use { it.commit() }
        val p = store.projects.single()
        assertEquals(listOf("同名场景", "同名场景 (2)"), p.scenes)
        assertEquals("同名场景 (2)", p.cards.single().scene)
        assertEquals(2, p.cards.single().guidanceEntries.size)
        assertEquals("Asia/Shanghai", p.cards.single().scouts.single().timeZoneIdentifier)
        store.saveProject(p.copy(name = "安卓修改", cards = p.cards.map { it.copy(status = "已完成", timeNote = "已修改") }))
        val files = entries(export("project")); val result = JSONObject(files.getValue("project.json").toString(Charsets.UTF_8))
        assertEquals("安卓修改", result.getJSONObject("project").getString("name"))
        assertEquals("2026-11-02T12:34:56Z", result.getJSONObject("project").getString("shootDate"))
        assertEquals(sceneB, result.getJSONArray("cards").getJSONObject(0).getString("sceneID"))
        assertEquals("同名场景", result.getJSONArray("scenes").getJSONObject(1).getString("name"))
        val card = result.getJSONArray("cards").getJSONObject(0)
        assertEquals("completed", card.getString("status")); assertEquals("已修改", card.getString("timeNote"))
        val ref = card.getJSONArray("references").getJSONObject(0)
        assertNotEquals(ref.getString("imagePath"), ref.getString("thumbnailPath"))
        assertArrayEquals(png + byteArrayOf(0), files.getValue(ref.getString("imagePath")))
        assertArrayEquals(png + byteArrayOf(1), files.getValue(ref.getString("thumbnailPath")))
        assertEquals(240.5, card.getJSONArray("scouts").getJSONObject(0).getJSONObject("lightPlan").getDouble("sunAzimuth"), 0.0)
        assertEquals(183.123456789, card.getJSONArray("scouts").getJSONObject(0).getDouble("heading"), 0.0)
        assertFalse(result.toString().contains("_android"))
        prepare(export("project")).use { it.commit() }
        assertEquals(2, store.projects.size)
    }

    private fun library(): JSONObject {
        val template = manifest().apply { remove("format"); put("version", 2); getJSONArray("cards").getJSONObject(0).put("id", UUID.randomUUID().toString()) }
        return JSONObject().put("format", "shotegy-library").put("version", 1).put("resources", arr(JSONObject().put("id", resourceId).put("kind", "template").put("title", "模板").put("content", template.toString()).put("createdAt", "2026-01-01T00:00:00Z").put("updatedAt", "2026-02-01T00:00:00Z"), JSONObject().put("id", UUID.randomUUID().toString()).put("kind", "guidance").put("title", "引导").put("content", "走向镜头"))).put("folders", arr(JSONObject().put("id", folderId).put("kind", "template").put("name", "收藏夹").put("sortOrder", 3))).put("links", arr(JSONObject().put("folderId", folderId).put("resourceId", resourceId))).put("mediaMap", JSONObject())
    }
    @Test fun workspaceAndLibraryTemplatesRemainValidIOSCodablePayloads() {
        val workspace = JSONObject().put("format", "shotegy-workspace").put("version", 1).put("projects", arr(manifest())).put("library", library()).put("mediaMap", JSONObject())
        prepare(zip("workspace.json", workspace)).use { it.commit() }
        assertEquals(1, store.projects.size); assertEquals(2, store.resources.size)
        assertEquals(store.folders.single().id, store.resources.first { it.template != null }.folderIds.single())
        val bytes = export("workspace"); val result = JSONObject(entries(bytes).getValue("workspace.json").toString(Charsets.UTF_8))
        val lib = result.getJSONObject("library"); val payload = JSONObject(lib.getJSONArray("resources").getJSONObject(0).getString("content"))
        assertEquals(2, payload.getInt("version")); assertEquals(2, payload.getJSONArray("cards").getJSONObject(0).getJSONArray("guidance").length())
        UUID.fromString(payload.getJSONArray("cards").getJSONObject(0).getString("id"))
        UUID.fromString(payload.getJSONArray("projectAssets").getJSONObject(0).getString("id"))
        prepare(bytes).use { it.commit() }; assertEquals(2, store.projects.size)
        val libBytes = export("library"); prepare(libBytes).use { it.commit() }; assertEquals(8, store.resources.size)
    }
    @Test fun cancelledOrMalformedImportDoesNotChangeExistingDataOrLeakMedia() {
        store.saveProject(Project(name = "保留"))
        val mediaDir = File(context.filesDir, "media"); val before = mediaDir.list()?.toSet().orEmpty()
        prepare(zip("project.json", manifest())).close()
        assertEquals(before, mediaDir.list()?.toSet().orEmpty())
        listOf(zip("project.json", manifest(), false), zip("project.json", manifest().put("version", 99)), zip("../project.json", manifest()), zip("project.json", manifest().apply { getJSONArray("cards").getJSONObject(0).put("sceneID", UUID.randomUUID().toString()) })).forEach { bytes ->
            try { prepare(bytes).close(); fail("应该拒绝损坏备份") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
            assertEquals("保留", store.projects.single().name)
            assertEquals(before, mediaDir.list()?.toSet().orEmpty())
        }
    }
    @Test fun oldAndroidBackupKeepsIOSMetadataAndIndependentThumbnailReferences() {
        prepare(zip("project.json", manifest())).use { it.commit() }
        val backup = store.exportBackup(); store.restoreBackup(backup)
        val p = store.projects.single()
        assertTrue(iosMetadataMedia(p.iosMetadata).all { store.referenceImage(it) != null })
        val snapshot = templateSnapshot(p)
        val template = IOSBackup.projectManifest(snapshot, true)
        assertFalse(template.getJSONObject("project").has("clientName"))
        val reference = template.getJSONArray("cards").getJSONObject(0).getJSONArray("references").getJSONObject(0)
        assertNotEquals(reference.getString("imagePath"), reference.getString("thumbnailPath"))
    }
    @Test fun workspaceUsesDeclaredMediaMapInsteadOfOriginalBasename() {
        val map = JSONObject().put("old/full.png", "media/style.png").put("old/style.png", "media/full.png")
        val workspace = JSONObject().put("format", "shotegy-workspace").put("version", 1).put("projects", arr(manifest())).put("mediaMap", map)
        prepare(zip("workspace.json", workspace)).use { it.commit() }
        assertArrayEquals(png + byteArrayOf(3), store.referenceImage(store.projects.single().cards.single().referenceImages.single())!!.readBytes())
    }
    @Test fun failedWorkspaceCommitRollsBackAllCollectionsAndNewMedia() {
        val original = Project(name = "原项目"); store.saveProject(original)
        val underlying = context.getSharedPreferences("shotegy", 0); var failOnce = true
        val failing = object : android.content.SharedPreferences by underlying {
            override fun edit(): android.content.SharedPreferences.Editor {
                val editor = underlying.edit()
                return object : android.content.SharedPreferences.Editor by editor {
                    override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor { editor.putString(key, value); return this }
                    override fun commit(): Boolean { val written = editor.commit(); return if (failOnce) { failOnce = false; false } else written }
                }
            }
        }
        val subject = Store(context, failing)
        val before = File(context.filesDir, "media").list()?.toSet().orEmpty()
        val workspace = JSONObject().put("format", "shotegy-workspace").put("version", 1).put("projects", arr(manifest())).put("library", library())
        IOSBackup.prepare(zip("workspace.json", workspace).inputStream(), context.cacheDir, subject).use { prepared ->
            try { prepared.commit(); fail("应报告保存失败") } catch (_: IllegalStateException) { }
        }
        assertEquals(listOf(original), Store(context).projects.toList())
        assertTrue(Store(context).resources.isEmpty()); assertTrue(Store(context).folders.isEmpty())
        assertEquals(before, File(context.filesDir, "media").list()?.toSet().orEmpty())
    }
    @Test fun zipCRCFailureIsRejectedBeforePublishingData() {
        val data = "{\"format\":\"shotegy-project\"}".toByteArray()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { z -> val e = ZipEntry("project.json").apply { method = ZipEntry.STORED; size = data.size.toLong(); compressedSize = size; crc = CRC32().apply { update(data) }.value }; z.putNextEntry(e); z.write(data); z.closeEntry() }
        val bytes = output.toByteArray(); val position = 30 + "project.json".length
        bytes[position] = 'x'.code.toByte()
        try { prepare(bytes); fail("应拒绝 CRC 错误") } catch (_: ZipException) { }
        assertTrue(store.projects.isEmpty())
    }
    @Test fun exportUsesOnlyTheExactIOSProjectAndTemplateSchemaKeys() {
        prepare(zip("project.json", manifest())).use { it.commit() }
        val project = IOSBackup.projectManifest(store.projects.single())
        fun keys(o: JSONObject) = o.keys().asSequence().toSet()
        assertEquals(setOf("format", "version", "project", "cards", "scenes", "projectAssets"), keys(project))
        val card = project.getJSONArray("cards").getJSONObject(0)
        assertEquals(setOf("sortOrder", "shotScaleRawValue", "sceneID", "startMinutes", "endMinutes", "timeNote", "status", "guidance", "techniques", "references", "scouts"), keys(card))
        assertEquals(setOf("imagePath", "thumbnailPath", "kindRawValue", "sortOrder"), keys(project.getJSONArray("projectAssets").getJSONObject(0)))
        val template = IOSBackup.projectManifest(templateSnapshot(store.projects.single()), true)
        assertEquals(setOf("version", "project", "cards", "scenes", "projectAssets"), keys(template))
        assertEquals(setOf("id", "sortOrder", "shotScaleRawValue", "sceneID", "startMinutes", "endMinutes", "timeNote", "guidance", "techniques", "references"), keys(template.getJSONArray("cards").getJSONObject(0)))
        assertFalse(template.toString().contains("_android"))
        assertFalse(template.getJSONObject("project").has("name"))
        val empty = IOSBackup.projectManifest(Project(name = "空项目", cards = listOf(ShotCard())))
        assertFalse(empty.getJSONObject("project").has("shootDate"))
        assertFalse(empty.getJSONArray("cards").getJSONObject(0).has("sceneID"))
        assertFalse(empty.getJSONArray("cards").getJSONObject(0).has("startMinutes"))
    }
}
