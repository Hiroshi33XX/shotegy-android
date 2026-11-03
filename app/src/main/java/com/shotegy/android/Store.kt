// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private fun newId() = UUID.randomUUID().toString()
data class LightPlan(val id: String = newId(), val lightType: String = "逆光", val planDate: String = "", val tolerance: Int = 20, val recommendedStart: Long? = null, val recommendedEnd: Long? = null, val sunAzimuth: Double? = null, val sunElevation: Double? = null)
data class Scout(val id: String = newId(), val imageId: String = "", val latitude: Double? = null, val longitude: Double? = null, val heading: Float? = null, val capturedAt: Long = System.currentTimeMillis(), val note: String = "", val headingAccuracy: Double? = null, val timeZoneIdentifier: String? = null, val lightPlans: List<LightPlan> = emptyList())
data class PhraseItem(val id: String = newId(), val text: String = "")
data class ShotCard(val id: String = newId(), val guidance: String = "", val technique: String = "", val scene: String = "", val status: String = "未拍", val startTime: String = "", val endTime: String = "", val timeNote: String = "", val shotScale: String = "", val referenceImages: List<String> = emptyList(), val scouts: List<Scout> = emptyList(), val guidanceItems: List<PhraseItem> = emptyList(), val techniqueItems: List<PhraseItem> = emptyList()) {
    // Earlier Android builds stored the entire text in one field; expose it without
    // rewriting or splitting the user's original text until it is explicitly edited.
    val guidanceEntries: List<PhraseItem> get() = guidanceItems.ifEmpty { if (guidance.isBlank()) emptyList() else listOf(PhraseItem("$id-guidance", guidance)) }
    val techniqueEntries: List<PhraseItem> get() = techniqueItems.ifEmpty { if (technique.isBlank()) emptyList() else listOf(PhraseItem("$id-technique", technique)) }
}
data class Project(val id: String = newId(), val name: String = "", val theme: String = "", val style: String = "", val clientName: String = "", val date: String = "", val location: String = "", val notes: String = "", val status: String = "草稿", val cards: List<ShotCard> = emptyList(), val contact: String = "", val wardrobe: String = "", val makeup: String = "", val hair: String = "", val props: String = "", val archived: Boolean = false, val lastShotCardId: String = "", val startTime: String = "", val endTime: String = "", val clothingStyle: String = "", val accessories: String = "", val shoes: String = "", val clothingColors: List<String> = emptyList(), val sceneColor: String = "", val clothingStyleIsCustom: Boolean = false, val scenes: List<String> = emptyList(), val stylingImages: Map<String, List<String>> = emptyMap(), val modelStyle: String = "", val desiredWeather: String = "", val equipment: String = "", val calendarLabelOverride: String? = null, val calendarColorHex: String? = null, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt, val iosMetadata: String = "")
data class LibraryItem(val id: String = newId(), val kind: String = "引导词库", val title: String = "", val content: String = "", val folderIds: List<String> = emptyList(), val template: Project? = null, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt, val lastUsedAt: Long? = null)
data class LibraryFolder(val id: String = newId(), val kind: String = "引导词库", val name: String = "", val sortOrder: Int = 0, val createdAt: Long = System.currentTimeMillis())

private fun array(items: Iterable<JSONObject>) = JSONArray().also { a -> items.forEach { a.put(it) } }
private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }
private fun LightPlan.json() = JSONObject().put("id", id).put("lightType", lightType).put("planDate", planDate).put("tolerance", tolerance).put("recommendedStart", recommendedStart ?: JSONObject.NULL).put("recommendedEnd", recommendedEnd ?: JSONObject.NULL).put("sunAzimuth", sunAzimuth ?: JSONObject.NULL).put("sunElevation", sunElevation ?: JSONObject.NULL)
private fun lightPlan(o: JSONObject) = LightPlan(o.getString("id"), o.optString("lightType", "逆光"), o.optString("planDate"), o.optInt("tolerance", 20), if (o.isNull("recommendedStart")) null else o.getLong("recommendedStart"), if (o.isNull("recommendedEnd")) null else o.getLong("recommendedEnd"), if (o.isNull("sunAzimuth")) null else o.getDouble("sunAzimuth"), if (o.isNull("sunElevation")) null else o.getDouble("sunElevation"))
private fun Scout.json() = JSONObject().put("id", id).put("imageId", imageId).put("latitude", latitude ?: JSONObject.NULL).put("longitude", longitude ?: JSONObject.NULL).put("heading", heading?.toDouble() ?: JSONObject.NULL).put("capturedAt", capturedAt).put("note", note).put("headingAccuracy", headingAccuracy ?: JSONObject.NULL).put("timeZoneIdentifier", timeZoneIdentifier ?: JSONObject.NULL).put("lightPlans", array(lightPlans.map { it.json() }))
private fun scout(o: JSONObject) = Scout(o.getString("id"), o.optString("imageId"), if (o.isNull("latitude")) null else o.getDouble("latitude"), if (o.isNull("longitude")) null else o.getDouble("longitude"), if (o.isNull("heading")) null else o.getDouble("heading").toFloat(), o.optLong("capturedAt", 0), o.optString("note"), if (o.isNull("headingAccuracy")) null else o.getDouble("headingAccuracy"), if (o.isNull("timeZoneIdentifier")) null else o.getString("timeZoneIdentifier"), o.optJSONArray("lightPlans")?.let { a -> (0 until a.length()).map { lightPlan(a.getJSONObject(it)) } } ?: emptyList())
private fun mapImages(value: Map<String, List<String>>) = JSONObject().also { o -> value.forEach { (key, ids) -> o.put(key, JSONArray(ids)) } }
private fun readImages(o: JSONObject?): Map<String, List<String>> = if (o == null) emptyMap() else o.keys().asSequence().associateWith { strings(o.getJSONArray(it)) }
private fun PhraseItem.json() = JSONObject().put("id", id).put("text", text)
private fun phrases(a: JSONArray?): List<PhraseItem> = if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it).let { o -> PhraseItem(o.getString("id"), o.getString("text")) } }
private fun ShotCard.json() = JSONObject().put("id", id).put("guidance", guidance).put("technique", technique).put("scene", scene).put("status", status).put("startTime", startTime).put("endTime", endTime).put("timeNote", timeNote).put("shotScale", shotScale).put("referenceImages", JSONArray(referenceImages)).put("scouts", array(scouts.map { it.json() })).put("guidanceItems", array(guidanceItems.map { it.json() })).put("techniqueItems", array(techniqueItems.map { it.json() }))
private fun card(o: JSONObject) = ShotCard(o.getString("id"), o.optString("guidance"), o.optString("technique"), o.optString("scene"), o.optString("status", "未拍"), o.optString("startTime"), o.optString("endTime"), o.optString("timeNote"), o.optString("shotScale"), strings(o.optJSONArray("referenceImages")), o.optJSONArray("scouts")?.let { a -> (0 until a.length()).map { scout(a.getJSONObject(it)) } } ?: emptyList(), phrases(o.optJSONArray("guidanceItems")), phrases(o.optJSONArray("techniqueItems")))
private fun Project.json() = JSONObject().put("id", id).put("name", name).put("theme", theme).put("style", style).put("clientName", clientName).put("date", date).put("location", location).put("notes", notes).put("status", status).put("cards", array(cards.map { it.json() })).put("contact", contact).put("wardrobe", wardrobe).put("makeup", makeup).put("hair", hair).put("props", props).put("archived", archived).put("lastShotCardId", lastShotCardId).put("startTime", startTime).put("endTime", endTime).put("clothingStyle", clothingStyle).put("accessories", accessories).put("shoes", shoes).put("clothingColors", JSONArray(clothingColors)).put("sceneColor", sceneColor).put("clothingStyleIsCustom", clothingStyleIsCustom).put("scenes", JSONArray(scenes)).put("stylingImages", mapImages(stylingImages)).put("modelStyle", modelStyle).put("desiredWeather", desiredWeather).put("equipment", equipment).put("calendarLabelOverride", calendarLabelOverride ?: JSONObject.NULL).put("calendarColorHex", calendarColorHex ?: JSONObject.NULL).put("createdAt", createdAt).put("updatedAt", updatedAt).put("iosMetadata", iosMetadata)
private fun project(o: JSONObject): Project {
    val cards = o.optJSONArray("cards") ?: JSONArray()
    return Project(o.getString("id"), o.optString("name"), o.optString("theme"), o.optString("style"), o.optString("clientName"), o.optString("date"), o.optString("location"), o.optString("notes"), o.optString("status", "草稿"), (0 until cards.length()).map { card(cards.getJSONObject(it)) }, o.optString("contact"), o.optString("wardrobe"), o.optString("makeup"), o.optString("hair"), o.optString("props"), o.optBoolean("archived"), o.optString("lastShotCardId"), o.optString("startTime"), o.optString("endTime"), o.optString("clothingStyle"), o.optString("accessories"), o.optString("shoes"), strings(o.optJSONArray("clothingColors")), o.optString("sceneColor"), o.optBoolean("clothingStyleIsCustom"), strings(o.optJSONArray("scenes")), readImages(o.optJSONObject("stylingImages")), o.optString("modelStyle"), o.optString("desiredWeather"), o.optString("equipment"), if (o.isNull("calendarLabelOverride")) null else o.getString("calendarLabelOverride"), if (o.isNull("calendarColorHex")) null else o.getString("calendarColorHex"), o.optLong("createdAt", 0L), o.optLong("updatedAt", 0L), o.optString("iosMetadata"))
}
private fun LibraryItem.json() = JSONObject().put("id", id).put("kind", kind).put("title", title).put("content", content).put("folderIds", JSONArray(folderIds)).put("template", template?.json() ?: JSONObject.NULL).put("createdAt", createdAt).put("updatedAt", updatedAt).put("lastUsedAt", lastUsedAt ?: JSONObject.NULL)
private fun resource(o: JSONObject) = LibraryItem(o.getString("id"), o.optString("kind", "引导词库"), o.optString("title"), o.optString("content"), strings(o.optJSONArray("folderIds")), o.optJSONObject("template")?.let { project(it) }, o.optLong("createdAt", 0L), o.optLong("updatedAt", 0L), if (o.isNull("lastUsedAt")) null else o.getLong("lastUsedAt"))
private fun LibraryFolder.json() = JSONObject().put("id", id).put("kind", kind).put("name", name).put("sortOrder", sortOrder).put("createdAt", createdAt)
private fun folder(o: JSONObject) = LibraryFolder(o.getString("id"), o.optString("kind", "引导词库"), o.optString("name"), o.optInt("sortOrder", 0), o.optLong("createdAt", 0L))

class Store(private val context: Context, private val prefs: android.content.SharedPreferences = context.getSharedPreferences("shotegy", Context.MODE_PRIVATE)) {
    private val mediaDir = java.io.File(context.filesDir, "media").apply { mkdirs() }
    val projects = mutableStateListOf<Project>()
    val resources = mutableStateListOf<LibraryItem>()
    val folders = mutableStateListOf<LibraryFolder>()
    var isReadFailed by mutableStateOf(false)
        private set
    init {
        try {
            val p = JSONArray(prefs.getString("projects", "[]")); val r = JSONArray(prefs.getString("resources", "[]")); val f = JSONArray(prefs.getString("folders", "[]"))
            val ps = (0 until p.length()).map { project(p.getJSONObject(it)) }; val rs = (0 until r.length()).map { resource(r.getJSONObject(it)) }; val fs = (0 until f.length()).map { folder(f.getJSONObject(it)) }
            projects.addAll(ps); resources.addAll(rs); folders.addAll(fs)
        } catch (e: Exception) { isReadFailed = true; android.util.Log.e("Shotegy", "Unable to read saved data; writes disabled", e) }
    }
    fun saveProject(value: Project) { checkWritable(); val i = projects.indexOfFirst { it.id == value.id }; val saved = if (i < 0) value else value.copy(updatedAt = System.currentTimeMillis()); if (i < 0) projects.add(saved) else projects[i] = saved; persist() }
    /** Atomically append validated imports without replacing projects or rewriting existing media. */
    @Synchronized
    fun appendProjectsAtomically(incoming: List<Project>) {
        checkWritable()
        if (incoming.isEmpty()) return
        val ids = incoming.map { it.id }
        require(ids.all { it.isNotBlank() } && ids.toSet().size == ids.size) { "导入项目标识重复或为空" }
        require(projects.none { it.id in ids }) { "导入项目与现有项目标识重复" }
        val media = incoming.flatMap { p -> iosMetadataMedia(p.iosMetadata) + p.stylingImages.values.flatten() + p.cards.flatMap { c -> c.referenceImages + c.scouts.mapNotNull { it.imageId.takeIf(String::isNotBlank) } } }.distinct()
        require(media.all { referenceImage(it) != null }) { "导入项目引用的图片不存在" }
        val oldProjects = prefs.getString("projects", "[]")
        val newProjects = array((projects.toList() + incoming).map { it.json() }).toString()
        try {
            check(prefs.edit().putString("projects", newProjects).commit()) { "无法保存导入项目" }
        } catch (e: Exception) {
            // Android may update the preferences' in-memory map even when commit fails.
            val restored = runCatching { prefs.edit().putString("projects", oldProjects).commit() }.getOrDefault(false)
            if (!restored) isReadFailed = true
            throw e
        }
        projects.addAll(incoming)
    }
    internal fun importMediaFile(source: java.io.File): String { val id = newId(); source.copyTo(mediaFile(id)); return id }
    internal fun removeImportedMedia(ids: List<String>) { ids.forEach { mediaFile(it).delete() } }
    @Synchronized
    internal fun appendInterchange(ps: List<Project>, rs: List<LibraryItem>, fs: List<LibraryFolder>) {
        checkWritable()
        require((projects.map { it.id } + ps.map { it.id }).distinct().size == projects.size + ps.size)
        require((resources.map { it.id } + rs.map { it.id }).distinct().size == resources.size + rs.size)
        require((folders.map { it.id } + fs.map { it.id }).distinct().size == folders.size + fs.size)
        val allMedia = (ps + rs.mapNotNull { it.template }).flatMap { p -> iosMetadataMedia(p.iosMetadata) + p.stylingImages.values.flatten() + p.cards.flatMap { it.referenceImages + it.scouts.map { scout -> scout.imageId }.filter { it.isNotBlank() } } }
        require(allMedia.all { referenceImage(it) != null }) { "导入图片缺失" }
        val old = listOf("projects", "resources", "folders").associateWith { prefs.getString(it, "[]") }
        try {
            check(prefs.edit().putString("projects", array((projects.toList() + ps).map { it.json() }).toString()).putString("resources", array((resources.toList() + rs).map { it.json() }).toString()).putString("folders", array((folders.toList() + fs).map { it.json() }).toString()).commit()) { "无法保存导入数据" }
        } catch (e: Exception) {
            val restored = runCatching { val editor = prefs.edit(); old.forEach { (key, value) -> editor.putString(key, value) }; editor.commit() }.getOrDefault(false)
            if (!restored) isReadFailed = true
            throw e
        }
        projects.addAll(ps); resources.addAll(rs); folders.addAll(fs)
    }
    fun deleteProject(id: String) { checkWritable(); projects.removeAll { it.id == id }; persist() }
    fun saveResource(value: LibraryItem) { checkWritable(); val i = resources.indexOfFirst { it.id == value.id }; val saved = if (i < 0) value else value.copy(updatedAt = System.currentTimeMillis()); if (i < 0) resources.add(saved) else resources[i] = saved; persist() }
    fun deleteResource(id: String) { checkWritable(); resources.removeAll { it.id == id }; persist() }
    fun saveFolder(value: LibraryFolder) { checkWritable(); val i = folders.indexOfFirst { it.id == value.id }; if (i < 0) folders.add(value) else folders[i] = value; persist() }
    fun deleteFolder(id: String) { checkWritable(); folders.removeAll { it.id == id }; resources.indices.forEach { i -> resources[i] = resources[i].copy(folderIds = resources[i].folderIds.filterNot { it == id }) }; persist() }
    private fun checkWritable() { check(!isReadFailed) { "已保存数据无法读取，操作已取消以保护原始数据" } }
    private fun persist() { prefs.edit().putString("projects", array(projects.map { it.json() }).toString()).putString("resources", array(resources.map { it.json() }).toString()).putString("folders", array(folders.map { it.json() }).toString()).apply() }
    fun saveReferenceImage(uri: android.net.Uri): String {
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取图片")
        val bytes = input.use { source ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = source.read(buffer); if (n < 0) break; out.write(buffer, 0, n); require(out.size() <= 15 * 1024 * 1024) { "图片不能超过 15 MB" } }; out.toByteArray()
        }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法识别图片" }
        val id = newId(); mediaFile(id).writeBytes(bytes); return id
    }
    private fun mediaFile(id: String): java.io.File { require(Regex("[0-9a-fA-F-]{36}").matches(id)) { "无效的图片标识" }; return java.io.File(mediaDir, id) }
    fun referenceImage(id: String): java.io.File? = runCatching { mediaFile(id).takeIf { it.isFile } }.getOrNull()
    internal fun exportBackup(): String {
        checkWritable()
        val all = projects.toList() + resources.mapNotNull { it.template }
        val ids = all.flatMap { p -> iosMetadataMedia(p.iosMetadata) + p.stylingImages.values.flatten() + p.cards.flatMap { c -> c.referenceImages + c.scouts.mapNotNull { it.imageId.takeIf(String::isNotBlank) } } }.distinct()
        require(ids.sumOf { id -> referenceImage(id)?.length() ?: error("备份失败：参考图片文件缺失") } <= 22L * 1024 * 1024) { "图片总量过大，暂时无法导出备份" }
        val media = JSONObject()
        ids.forEach { id -> val file = referenceImage(id) ?: error("备份失败：参考图片文件缺失"); media.put(id, android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)) }
        return JSONObject().put("format", "shotegy-android-backup").put("version", 1).put("projects", array(projects.map { it.json() })).put("resources", array(resources.map { it.json() })).put("folders", array(folders.map { it.json() })).put("media", media).toString(2)
    }
    fun restoreBackup(text: String) {
        val root = JSONObject(text)
        require(root.optString("format") == "shotegy-android-backup" && root.optInt("version") == 1) { "不是受支持的 Shotegy Android 备份文件" }
        val p = root.getJSONArray("projects"); val r = root.getJSONArray("resources"); val f = root.getJSONArray("folders")
        var ps = (0 until p.length()).map { project(p.getJSONObject(it)) }; var rs = (0 until r.length()).map { resource(r.getJSONObject(it)) }; val fs = (0 until f.length()).map { folder(f.getJSONObject(it)) }
        require(ps.map { it.id }.toSet().size == ps.size && rs.map { it.id }.toSet().size == rs.size && fs.map { it.id }.toSet().size == fs.size) { "备份包含重复的标识" }
        val ids = (ps + rs.mapNotNull { it.template }).flatMap { p -> iosMetadataMedia(p.iosMetadata) + p.stylingImages.values.flatten() + p.cards.flatMap { c -> c.referenceImages + c.scouts.mapNotNull { it.imageId.takeIf(String::isNotBlank) } } }.distinct()
        val media = root.optJSONObject("media") ?: JSONObject()
        val created = mutableListOf<java.io.File>()
        var commitAttempted = false
        val oldProjects = prefs.getString("projects", "[]")
        val oldResources = prefs.getString("resources", "[]")
        val oldFolders = prefs.getString("folders", "[]")
        try {
            val mapping = ids.associateWith { old ->
                val encoded = media.getString(old)
                require(encoded.length <= 21 * 1024 * 1024) { "备份图片过大" }
                val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                require(bytes.size <= 15 * 1024 * 1024) { "备份图片过大" }
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "备份图片无效" }
                val id = newId(); val file = mediaFile(id); created.add(file); file.writeBytes(bytes); id
            }
            fun rewrite(p: Project) = p.copy(iosMetadata = remapIOSMetadata(p.iosMetadata, mapping), stylingImages = p.stylingImages.mapValues { (_, ids) -> ids.map { mapping.getValue(it) } }, cards = p.cards.map { c -> c.copy(referenceImages = c.referenceImages.map { mapping.getValue(it) }, scouts = c.scouts.map { scout -> scout.copy(imageId = scout.imageId.takeIf(String::isNotBlank)?.let { mapping.getValue(it) } ?: "") }) })
            ps = ps.map(::rewrite); rs = rs.map { it.copy(template = it.template?.let(::rewrite)) }
            commitAttempted = true
            check(prefs.edit().putString("projects", array(ps.map { it.json() }).toString()).putString("resources", array(rs.map { it.json() }).toString()).putString("folders", array(fs.map { it.json() }).toString()).commit()) { "无法保存恢复的数据" }
        } catch (e: Exception) {
            if (commitAttempted) {
                // A failed commit may still have changed the in-memory preferences. Roll back;
                // retain the new media because a disk write may have partially succeeded.
                val restored = runCatching { prefs.edit().putString("projects", oldProjects).putString("resources", oldResources).putString("folders", oldFolders).commit() }.getOrDefault(false)
                if (!restored) isReadFailed = true
            } else created.forEach { it.delete() }
            throw e
        }
        projects.clear(); projects.addAll(ps); resources.clear(); resources.addAll(rs); folders.clear(); folders.addAll(fs); isReadFailed = false
    }
}
