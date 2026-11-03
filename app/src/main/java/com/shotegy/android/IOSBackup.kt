// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.zip.*

private fun uid() = UUID.randomUUID().toString()
private fun objects(a: JSONArray?): List<JSONObject> = if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }
private fun texts(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }
private fun jsonArray(values: List<Any>) = JSONArray(values)
private fun clone(o: JSONObject) = JSONObject(o.toString())
private fun millis(s: String): Long = runCatching { Instant.parse(s).toEpochMilli() }.getOrElse { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
private fun iso(value: Long) = Instant.ofEpochMilli(value).truncatedTo(ChronoUnit.SECONDS).toString()
private fun nullableTime(o: JSONObject, key: String): Long? = if (o.isNull(key)) null else millis(o.getString(key))
private fun clock(o: JSONObject, key: String): String { if (o.isNull(key)) return ""; val m = o.getInt(key); require(m in 0..1439) { "时间超出范围" }; return "%02d:%02d".format(java.util.Locale.US, m / 60, m % 60) }
private fun minutes(s: String): Any = if (s.isBlank()) JSONObject.NULL else LocalTime.parse(s).let { it.hour * 60 + it.minute }
private val projectStates = mapOf("draft" to "草稿", "upcoming" to "待拍摄", "shooting" to "拍摄中", "completed" to "已完成")
private val shotStates = mapOf("pending" to "未拍", "completed" to "已完成", "skipped" to "已跳过")
private val scales = mapOf("close" to "近景 / 特写", "medium" to "中景", "wide" to "远景")
private val lights = mapOf("front" to "顺光", "back" to "逆光", "left" to "左侧光", "right" to "右侧光")
private val kinds = mapOf("template" to "项目模板", "guidance" to "引导词库", "technique" to "方法库")
private fun raw(map: Map<String, String>, label: String, fallback: String) = if (label.isBlank()) fallback else map.entries.firstOrNull { it.value == label }?.key ?: error("无法转换为 iOS 类型：$label")

// Paths in this private sidecar are Android media IDs, never filesystem paths.
internal fun walkIOSMedia(value: Any, transform: (String) -> String) {
    when (value) {
        is JSONObject -> value.keys().asSequence().toList().forEach { key ->
            val child = value.get(key)
            if ((key == "imagePath" || key == "thumbnailPath") && child is String && child.isNotBlank()) value.put(key, transform(child))
            else if (child is JSONObject || child is JSONArray) walkIOSMedia(child, transform)
        }
        is JSONArray -> (0 until value.length()).forEach { walkIOSMedia(value.get(it), transform) }
    }
}
internal fun iosMetadataMedia(metadata: String): List<String> { if (metadata.isBlank()) return emptyList(); val ids = mutableSetOf<String>(); walkIOSMedia(JSONObject(metadata)) { ids.add(it); it }; return ids.toList() }
internal fun remapIOSMetadata(metadata: String, mapping: Map<String, String>): String { if (metadata.isBlank()) return ""; val o = JSONObject(metadata); walkIOSMedia(o) { mapping.getValue(it) }; return o.toString() }

internal class PreparedIOSImport(val projects: List<Project>, val resources: List<LibraryItem>, val folders: List<LibraryFolder>, private val store: Store, private val media: List<String>) : Closeable {
    private var committed = false
    fun commit() { store.appendInterchange(projects, resources, folders); committed = true }
    override fun close() { if (!committed && !store.isReadFailed) store.removeImportedMedia(media) }
}

internal object IOSBackup {
    const val MAX_TOTAL = 512L * 1024 * 1024
    private const val MAX_ENTRY = 128L * 1024 * 1024
    private const val MAX_MANIFEST = 16L * 1024 * 1024
    private fun mediaExtension(file: File): String {
        val bytes = ByteArray(16); val size = file.inputStream().use { it.read(bytes) }
        return when {
            size >= 8 && bytes[0] == 0x89.toByte() && String(bytes, 1, 3, Charsets.US_ASCII) == "PNG" -> "png"
            size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "jpg"
            size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
            size >= 12 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" && String(bytes, 8, 4, Charsets.US_ASCII) in listOf("heic", "heix", "hevc", "hevx", "mif1", "msf1", "avif", "avis") -> if (String(bytes, 8, 4, Charsets.US_ASCII).startsWith("avi")) "avif" else "heic"
            size >= 6 && String(bytes, 0, 3, Charsets.US_ASCII) == "GIF" -> "gif"
            size >= 4 && ((bytes[0] == 0x49.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 42.toByte()) || (bytes[0] == 0x4d.toByte() && bytes[1] == 0x4d.toByte() && bytes[3] == 42.toByte())) -> "tiff"
            size >= 2 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4d.toByte() -> "bmp"
            else -> error("备份媒体不是受支持的图片")
        }
    }
    private fun safeEntry(name: String): Boolean = name.isNotEmpty() && name.length < 1024 && !name.startsWith('/') && !name.contains('\\') && !name.contains(':') && name.none { it.code < 32 } && name.trimEnd('/').split('/').all { it.isNotEmpty() && it != "." && it != ".." }

    fun prepare(input: InputStream, cacheDir: File, store: Store): PreparedIOSImport {
        val root = File(cacheDir, "ios-import-${uid()}").apply { mkdirs() }
        require(root.canonicalFile.parentFile == cacheDir.canonicalFile)
        val entries = mutableMapOf<String, File>(); val copied = mutableListOf<String>()
        try {
            var total = 0L; var count = 0
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++count <= 10000 && safeEntry(entry.name)) { "压缩包路径或条目数量无效" }
                    if (entry.isDirectory) { require(entry.name == "media/") { "压缩包目录不受支持" }; continue }
                    require(entry.name in listOf("project.json", "workspace.json", "library.json") || (entry.name.startsWith("media/") && entry.name.count { it == '/' } == 1)) { "压缩包含非预期文件" }
                    require(!entries.containsKey(entry.name)) { "压缩包含重复文件" }
                    val file = File(root, uid()); var length = 0L
                    file.outputStream().use { out -> val buffer = ByteArray(32768); while (true) { val n = zip.read(buffer); if (n < 0) break; length += n; total += n; require(length <= (if (entry.name.endsWith(".json")) MAX_MANIFEST else MAX_ENTRY) && total <= MAX_TOTAL) { "备份超过允许大小" }; out.write(buffer, 0, n) } }
                    zip.closeEntry(); entries[entry.name] = file
                }
            }
            val manifests = entries.keys.filter { !it.startsWith("media/") }
            require(manifests.size == 1) { "备份必须含唯一的清单" }
            val manifest = JSONObject(entries.getValue(manifests.single()).readText(Charsets.UTF_8))
            val mediaCache = mutableMapOf<String, String>()
            fun normalize(source: JSONObject, map: JSONObject): JSONObject {
                val result = clone(source)
                walkIOSMedia(result) { original ->
                    val relative = if (map.has(original)) map.getString(original) else "media/${original.substringAfterLast('/')}"
                    require(safeEntry(relative) && relative.startsWith("media/") && relative.count { it == '/' } == 1) { "图片映射路径无效" }
                    mediaCache.getOrPut(relative) {
                        val file = entries[relative] ?: error("备份图片缺失：$relative")
                        require(file.length() > 0) { "备份含空图片" }
                        mediaExtension(file)
                        store.importMediaFile(file).also { copied.add(it) }
                    }
                }
                return result
            }
            val ps = mutableListOf<Project>(); val rs = mutableListOf<LibraryItem>(); val fs = mutableListOf<LibraryFolder>()
            fun readLibrary(library: JSONObject, workspaceMap: JSONObject) {
                require(library.getString("format") == "shotegy-library" && library.getInt("version") == 1) { "不支持的资源库版本" }
                listOf("resources", "folders", "links").forEach { require(library.get(it) is JSONArray) { "资源库数组无效" } }
                val map = library.optJSONObject("mediaMap")?.takeIf { it.length() > 0 } ?: workspaceMap
                val folderMap = mutableMapOf<String, LibraryFolder>(); val resourceMap = mutableMapOf<String, LibraryItem>()
                objects(library.optJSONArray("folders")).forEach { f ->
                    val old = f.getString("id"); UUID.fromString(old); require(!folderMap.containsKey(old))
                    val kind = kinds[f.getString("kind")] ?: error("未知资源类型")
                    folderMap[old] = LibraryFolder(kind = kind, name = f.getString("name"), sortOrder = f.optInt("sortOrder"), createdAt = nullableTime(f, "createdAt") ?: 0)
                }
                objects(library.optJSONArray("resources")).forEach { r ->
                    val old = r.getString("id"); UUID.fromString(old); require(!resourceMap.containsKey(old))
                    val kind = kinds[r.getString("kind")] ?: error("未知资源类型")
                    val content = r.getString("content")
                    val template = if (kind == "项目模板") {
                        val payload = JSONObject(content); require(payload.optInt("version", 1) in 1..2) { "不支持的模板版本" }
                        decodeProject(normalize(payload, map), true).copy(name = r.optString("title"))
                    } else null
                    resourceMap[old] = LibraryItem(kind = kind, title = r.optString("title"), content = if (template == null) content else "", template = template, createdAt = nullableTime(r, "createdAt") ?: 0, updatedAt = nullableTime(r, "updatedAt") ?: 0, lastUsedAt = nullableTime(r, "lastUsedAt"))
                }
                objects(library.optJSONArray("links")).forEach { link ->
                    val f = folderMap[link.getString("folderId")] ?: error("收藏夹关联缺失")
                    val old = link.getString("resourceId"); val r = resourceMap[old] ?: error("资源关联缺失")
                    require(f.kind == r.kind) { "收藏夹类型不匹配" }
                    resourceMap[old] = r.copy(folderIds = (r.folderIds + f.id).distinct())
                }
                fs.addAll(folderMap.values); rs.addAll(resourceMap.values)
            }
            when (manifest.getString("format")) {
                "shotegy-project" -> { require(manifests.single() == "project.json"); ps.add(decodeProject(normalize(manifest, JSONObject()), false)) }
                "shotegy-library" -> { require(manifests.single() == "library.json"); readLibrary(manifest, JSONObject()) }
                "shotegy-workspace" -> {
                    require(manifests.single() == "workspace.json" && manifest.getInt("version") == 1) { "不支持的完整备份版本" }
                    val map = manifest.optJSONObject("mediaMap") ?: JSONObject()
                    objects(manifest.getJSONArray("projects")).forEach { ps.add(decodeProject(normalize(it, map), false)) }
                    manifest.optJSONObject("library")?.let { readLibrary(it, map) }
                }
                else -> error("不是受支持的拍序备份")
            }
            return PreparedIOSImport(ps, rs, fs, store, copied)
        } catch (e: Exception) { store.removeImportedMedia(copied); throw e }
        finally { root.deleteRecursively() }
    }

    private fun decodeProject(o: JSONObject, template: Boolean): Project {
        if (!template) require(o.getString("format") == "shotegy-project" && o.getInt("version") in 2..3) { "不支持的项目版本" }
        listOf("cards", "scenes", "projectAssets").forEach { key -> require(!o.has(key) || o.get(key) is JSONArray) { "$key 不是有效数组" }; require((o.optJSONArray(key)?.length() ?: 0) <= 10000) { "清单条目过多" } }
        val p = o.getJSONObject("project")
        val sceneMap = mutableMapOf<String, String>(); val used = mutableSetOf<String>()
        val sceneValues = objects(o.optJSONArray("scenes")).sortedBy { it.optInt("sortOrder") }
        sceneValues.forEach { scene ->
            val id = scene.getString("id"); UUID.fromString(id); require(!sceneMap.containsKey(id))
            val name = scene.getString("name"); var alias = name.ifBlank { "未命名场景" }; var suffix = 2
            while (!used.add(alias)) { alias = "$name ($suffix)"; suffix++ }
            scene.put("_androidName", alias); sceneMap[id] = alias
        }
        fun parsePlan(o: JSONObject) = LightPlan(lightType = lights[o.getString("lightType")] ?: error("未知光线类型"), planDate = o.getString("planDate").also { millis(it) }, tolerance = o.optInt("tolerance", 20).also { require(it in 0..180) }, recommendedStart = nullableTime(o, "recommendedStart"), recommendedEnd = nullableTime(o, "recommendedEnd"), sunAzimuth = if(o.isNull("sunAzimuth")) null else o.getDouble("sunAzimuth"), sunElevation = if(o.isNull("sunElevation")) null else o.getDouble("sunElevation"))
        if (template) { val sourceIDs = objects(o.optJSONArray("cards")).mapNotNull { if (it.isNull("id")) null else it.getString("id").also(UUID::fromString) }; require(sourceIDs.distinct().size == sourceIDs.size) { "模板脚本标识重复" } }
        val cards = objects(o.optJSONArray("cards")).sortedBy { it.optInt("sortOrder") }.map { c ->
            listOf("guidance", "techniques", "references", "scouts").forEach { require(!c.has(it) || c.get(it) is JSONArray) { "脚本数组无效" } }
            val id = uid(); c.put("_androidId", id)
            val g = texts(c.optJSONArray("guidance")).map { PhraseItem(text = it) }; val t = texts(c.optJSONArray("techniques")).map { PhraseItem(text = it) }
            val scouts = objects(c.optJSONArray("scouts")).map { scout ->
                val sid = uid(); scout.put("_androidId", sid)
                val lat = scout.getDouble("latitude"); val lon = scout.getDouble("longitude"); val heading = scout.getDouble("heading")
                require(lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0 && heading.isFinite()) { "踩点坐标或方向无效" }
                val zone = if (scout.isNull("timeZoneIdentifier")) null else scout.getString("timeZoneIdentifier").also { ZoneId.of(it) }
                Scout(id = sid, imageId = scout.getString("imagePath"), latitude = lat, longitude = lon, heading = heading.toFloat(), capturedAt = nullableTime(scout, "capturedAt") ?: 0, note = scout.optString("note"), headingAccuracy = if (scout.isNull("headingAccuracy")) null else scout.getDouble("headingAccuracy"), timeZoneIdentifier = zone, lightPlans = scout.optJSONObject("lightPlan")?.let { listOf(parsePlan(it)) } ?: emptyList())
            }
            val scene = if (c.isNull("sceneID")) "" else sceneMap[c.getString("sceneID")] ?: error("脚本引用的场景不存在")
            val scale = if (c.isNull("shotScaleRawValue")) "" else scales[c.getString("shotScaleRawValue")] ?: error("未知景别")
            ShotCard(id = id, guidance = g.joinToString("\n") { it.text }, technique = t.joinToString("\n") { it.text }, guidanceItems = g, techniqueItems = t, scene = scene, shotScale = scale, startTime = clock(c, "startMinutes"), endTime = clock(c, "endMinutes"), timeNote = c.optString("timeNote"), status = if (template) "未拍" else shotStates[c.optString("status", "pending")] ?: error("未知脚本状态"), referenceImages = objects(c.optJSONArray("references")).map { it.getString("imagePath") }, scouts = scouts)
        }
        val assets = objects(o.optJSONArray("projectAssets")).sortedBy { it.optInt("sortOrder") }.groupBy { it.getString("kindRawValue") }.mapValues { (_, values) -> values.map { it.getString("imagePath") } }
        val date = if (p.isNull("shootDate")) "" else Instant.ofEpochMilli(millis(p.getString("shootDate"))).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val result = Project(name = p.optString("name", "未命名项目"), theme = p.optString("theme"), style = p.optString("style"), clientName = p.optString("clientName"), contact = p.optString("contact"), location = p.optString("location"), notes = p.optString("notes"), wardrobe = p.optString("wardrobe"), makeup = p.optString("makeup"), hair = p.optString("hair"), props = p.optString("props"), modelStyle = p.optString("modelStyle"), desiredWeather = p.optString("desiredWeather"), equipment = p.optString("equipment"), clothingStyle = p.optString("clothingStyle"), accessories = p.optString("accessories"), shoes = p.optString("shoes"), clothingColors = texts(p.optJSONArray("clothingColorHexes")), sceneColor = p.optString("sceneColorHex", "").let { if (it == "null") "" else it }, clothingStyleIsCustom = p.optBoolean("clothingStyleIsCustom"), calendarLabelOverride = if (p.isNull("calendarLabelOverride")) null else p.getString("calendarLabelOverride"), calendarColorHex = if (p.isNull("calendarColorHex")) null else p.getString("calendarColorHex"), archived = p.optBoolean("archived"), status = if (template) "草稿" else projectStates[p.optString("status", "draft")] ?: error("未知项目状态"), date = date, startTime = clock(p, "startMinutes"), endTime = clock(p, "endMinutes"), cards = cards, scenes = sceneMap.values.toList(), stylingImages = assets)
        o.put("_androidDate", date)
        return result.copy(iosMetadata = o.toString())
    }

    internal fun projectManifest(project: Project, template: Boolean = false): JSONObject {
        val base = if (project.iosMetadata.isBlank()) JSONObject() else JSONObject(project.iosMetadata)
        val info = base.optJSONObject("project")?.let(::clone) ?: JSONObject()
        mapOf("name" to project.name, "theme" to project.theme, "style" to project.style, "clientName" to project.clientName, "contact" to project.contact, "location" to project.location, "notes" to project.notes, "wardrobe" to project.wardrobe, "makeup" to project.makeup, "hair" to project.hair, "props" to project.props, "modelStyle" to project.modelStyle, "desiredWeather" to project.desiredWeather, "equipment" to project.equipment, "clothingStyle" to project.clothingStyle, "accessories" to project.accessories, "shoes" to project.shoes).forEach { (k, v) -> info.put(k, v) }
        info.put("clothingColorHexes", JSONArray(project.clothingColors)).put("sceneColorHex", project.sceneColor.ifBlank { null }).put("clothingStyleIsCustom", project.clothingStyleIsCustom)
        info.put("calendarLabelOverride", project.calendarLabelOverride).put("calendarColorHex", project.calendarColorHex).put("archived", project.archived).put("status", raw(projectStates, project.status, "draft")).put("startMinutes", minutes(project.startTime)).put("endMinutes", minutes(project.endTime))
        val shootDate = if (project.date.isBlank()) null else if (base.optString("_androidDate") == project.date && !info.isNull("shootDate")) info.getString("shootDate") else iso(LocalDate.parse(project.date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        info.put("shootDate", shootDate)
        val oldScenes = objects(base.optJSONArray("scenes"))
        val sceneMap = mutableMapOf<String, JSONObject>()
        (project.scenes + project.cards.map { it.scene }.filter { it.isNotBlank() }).distinct().forEachIndexed { index, name ->
            val old = oldScenes.firstOrNull { it.optString("_androidName", it.optString("name")) == name }
            sceneMap[name] = JSONObject().put("id", old?.getString("id") ?: uid()).put("name", old?.getString("name") ?: name).put("sortOrder", index)
        }
        val oldCards = objects(base.optJSONArray("cards"))
        val oldReferences = oldCards.flatMap { objects(it.optJSONArray("references")) }
        fun reference(image: String, candidates: List<JSONObject>): JSONObject = (candidates.firstOrNull { it.optString("imagePath") == image }?.let(::clone) ?: JSONObject().put("imagePath", image).put("thumbnailPath", image))
        val cards = project.cards.mapIndexed { index, card ->
            val old = oldCards.firstOrNull { it.optString("_androidId") == card.id } ?: JSONObject()
            val c = clone(old).put("id", card.id).put("sortOrder", index).put("sceneID", sceneMap[card.scene]?.getString("id")).put("shotScaleRawValue", if (card.shotScale.isBlank()) null else raw(scales, card.shotScale, "medium"))
                .put("startMinutes", minutes(card.startTime)).put("endMinutes", minutes(card.endTime)).put("timeNote", card.timeNote).put("status", raw(shotStates, card.status, "pending"))
                .put("guidance", JSONArray(card.guidanceEntries.map { it.text })).put("techniques", JSONArray(card.techniqueEntries.map { it.text })).put("references", jsonArray(card.referenceImages.map { reference(it, objects(old.optJSONArray("references")) + oldReferences) }))
            val oldScouts = objects(old.optJSONArray("scouts"))
            c.put("scouts", jsonArray(card.scouts.map { scout ->
                val oldScout = oldScouts.firstOrNull { it.optString("_androidId") == scout.id }
                val preciseHeading = oldScout?.optDouble("heading")?.takeIf { it.isFinite() && it.toFloat() == scout.heading } ?: scout.heading?.toDouble() ?: 0.0
                val value = reference(scout.imageId, listOfNotNull(oldScout)).put("latitude", scout.latitude ?: 0.0).put("longitude", scout.longitude ?: 0.0).put("heading", preciseHeading).put("headingAccuracy", scout.headingAccuracy).put("timeZoneIdentifier", scout.timeZoneIdentifier).put("capturedAt", iso(scout.capturedAt)).put("note", scout.note)
                val plan = scout.lightPlans.firstOrNull()
                value.put("lightPlan", plan?.let { JSONObject().put("lightType", raw(lights, it.lightType, "back")).put("planDate", if (it.planDate.length == 10) iso(LocalDate.parse(it.planDate).atStartOfDay(scout.timeZoneIdentifier?.let(ZoneId::of) ?: ZoneId.systemDefault()).toInstant().toEpochMilli()) else iso(millis(it.planDate))).put("tolerance", it.tolerance).put("recommendedStart", it.recommendedStart?.let(::iso)).put("recommendedEnd", it.recommendedEnd?.let(::iso)).put("sunAzimuth", it.sunAzimuth).put("sunElevation", it.sunElevation) })
                value
            }))
            c
        }
        val oldAssets = objects(base.optJSONArray("projectAssets"))
        val assets = project.stylingImages.flatMap { (kind, ids) -> ids.mapIndexed { index, image -> reference(image, oldAssets).put("id", oldAssets.firstOrNull { it.optString("imagePath") == image && it.optString("kindRawValue") == kind }?.optString("id")?.takeIf { it.isNotBlank() } ?: uid()).put("kindRawValue", kind).put("sortOrder", index) } }
        if (template) listOf("name", "clientName", "contact", "shootDate", "startMinutes", "endMinutes", "location", "status", "archived", "calendarLabelOverride", "calendarColorHex").forEach(info::remove)
        // Match the iOS writers exactly: no Android-only or cross-schema fields.
        val creativeKeys = setOf("theme", "style", "wardrobe", "makeup", "hair", "props", "notes", "modelStyle", "desiredWeather", "equipment", "clothingStyle", "accessories", "shoes", "clothingColorHexes", "sceneColorHex", "clothingStyleIsCustom")
        keepKeys(info, creativeKeys + if (template) emptySet() else setOf("name", "clientName", "shootDate", "startMinutes", "endMinutes", "location", "contact", "status", "archived", "calendarLabelOverride", "calendarColorHex"))
        cards.forEach { c ->
            keepKeys(c, setOf("sortOrder", "shotScaleRawValue", "sceneID", "startMinutes", "endMinutes", "timeNote", "guidance", "techniques", "references") + if (template) setOf("id") else setOf("status", "scouts"))
            objects(c.optJSONArray("references")).forEach { keepKeys(it, setOf("imagePath", "thumbnailPath")) }
            objects(c.optJSONArray("scouts")).forEach { scout ->
                keepKeys(scout, setOf("imagePath", "thumbnailPath", "latitude", "longitude", "heading", "headingAccuracy", "timeZoneIdentifier", "capturedAt", "note", "lightPlan"))
                scout.optJSONObject("lightPlan")?.let { keepKeys(it, setOf("lightType", "planDate", "tolerance", "recommendedStart", "recommendedEnd", "sunAzimuth", "sunElevation")) }
            }
        }
        assets.forEach { keepKeys(it, setOf("imagePath", "thumbnailPath", "kindRawValue", "sortOrder") + if (template) setOf("id") else emptySet()) }
        val result = JSONObject().put("version", if (template) 2 else 3).put("project", info).put("cards", jsonArray(cards)).put("scenes", jsonArray(sceneMap.values.toList())).put("projectAssets", jsonArray(assets))
        if (!template) result.put("format", "shotegy-project")
        stripPrivate(result)
        return result
    }

    private fun keepKeys(o: JSONObject, keys: Set<String>) { o.keys().asSequence().toList().filter { it !in keys }.forEach { o.remove(it) } }
    private fun stripPrivate(value: Any) { when(value) {
        is JSONObject -> value.keys().asSequence().toList().forEach { key -> if (key.startsWith("_android") || value.isNull(key)) value.remove(key) else stripPrivate(value.get(key)) }
        is JSONArray -> (0 until value.length()).forEach { stripPrivate(value.get(it)) }
    } }

    fun write(output: OutputStream, store: Store, projects: List<Project>, resources: List<LibraryItem>, folders: List<LibraryFolder>, kind: String) {
        val media = linkedMapOf<String, String>()
        fun paths(o: JSONObject): JSONObject { walkIOSMedia(o) { id -> val file = store.referenceImage(id) ?: error("备份图片缺失"); require(file.length() <= MAX_ENTRY) { "单张图片超过 128 MB" }; media.getOrPut(id) { "media/${uid()}.${mediaExtension(file)}" } }; return o }
        fun library(): JSONObject {
            val rs = resources.map { r -> JSONObject().put("id", r.id).put("kind", raw(kinds, r.kind, "guidance")).put("title", r.title).put("content", if (r.kind == "项目模板") paths(projectManifest(r.template ?: error("模板内容缺失"), true)).toString() else r.content).put("createdAt", iso(r.createdAt)).put("updatedAt", iso(r.updatedAt)).put("lastUsedAt", r.lastUsedAt?.let(::iso)) }
            val fs = folders.map { f -> JSONObject().put("id", f.id).put("kind", raw(kinds, f.kind, "guidance")).put("name", f.name).put("sortOrder", f.sortOrder).put("createdAt", iso(f.createdAt)) }
            val folderIds = folders.map { it.id }.toSet()
            val links = resources.flatMap { r -> r.folderIds.filter { it in folderIds }.map { JSONObject().put("folderId", it).put("resourceId", r.id) } }
            return JSONObject().put("format", "shotegy-library").put("version", 1).put("resources", jsonArray(rs)).put("folders", jsonArray(fs)).put("links", jsonArray(links)).put("mediaMap", JSONObject())
        }
        val name: String; val manifest: JSONObject
        when(kind) {
            "project" -> { require(projects.size == 1); name = "project.json"; manifest = paths(projectManifest(projects.single())) }
            "library" -> { name = "library.json"; manifest = library() }
            "workspace" -> { name = "workspace.json"; val ps = projects.map { paths(projectManifest(it)) }; manifest = JSONObject().put("format", "shotegy-workspace").put("version", 1).put("createdAt", iso(System.currentTimeMillis())).put("projects", jsonArray(ps)).put("library", library()).put("mediaMap", JSONObject()) }
            else -> error("未知备份类型")
        }
        // Every JSON path is already a media/... path; a self-map is understood by iOS.
        val map = JSONObject(); media.values.forEach { map.put(it, it) }
        if (kind == "library") manifest.put("mediaMap", map)
        if (kind == "workspace") { manifest.put("mediaMap", map); manifest.getJSONObject("library").put("mediaMap", map) }
        val json = manifest.toString(2).toByteArray(Charsets.UTF_8)
        require(json.size <= MAX_MANIFEST && media.size < 10000 && media.keys.sumOf { store.referenceImage(it)!!.length() } + json.size <= MAX_TOTAL) { "备份超过允许大小" }
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry(name)); zip.write(json); zip.closeEntry()
            media.forEach { (id, path) -> zip.putNextEntry(ZipEntry(path)); store.referenceImage(id)!!.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
        }
    }
}
