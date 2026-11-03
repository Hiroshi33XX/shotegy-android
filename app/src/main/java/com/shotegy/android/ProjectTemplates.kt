// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import java.util.UUID

private fun templateId() = UUID.randomUUID().toString()

/** iOS templates contain creative planning information, not client or shoot-specific data. */
internal fun templateSnapshot(project: Project): Project {
    val result = Project(
    name = project.name, theme = project.theme, style = project.style,
    wardrobe = project.wardrobe, makeup = project.makeup, hair = project.hair,
    props = project.props, notes = project.notes, modelStyle = project.modelStyle,
    desiredWeather = project.desiredWeather, equipment = project.equipment,
    clothingStyle = project.clothingStyle, accessories = project.accessories,
    shoes = project.shoes, clothingColors = project.clothingColors.toList(),
    sceneColor = project.sceneColor, clothingStyleIsCustom = project.clothingStyleIsCustom,
    scenes = project.scenes.toList(), stylingImages = project.stylingImages.mapValues { it.value.toList() },
    cards = project.cards.map { card ->
        card.copy(id = templateId(), status = "未拍", scouts = emptyList(),
            referenceImages = card.referenceImages.toList(),
            guidanceItems = card.guidanceEntries.map { it.copy(id = templateId()) },
            techniqueItems = card.techniqueEntries.map { it.copy(id = templateId()) })
    }
)

    if (project.iosMetadata.isBlank()) return result
    val metadata = org.json.JSONObject(project.iosMetadata)
    val cards = metadata.optJSONArray("cards")
    if (cards != null) for (i in 0 until cards.length()) {
        val c = cards.getJSONObject(i)
        val index = project.cards.indexOfFirst { it.id == c.optString("_androidId") }
        if (index >= 0) c.put("_androidId", result.cards[index].id)
        c.put("scouts", org.json.JSONArray())
    }
    metadata.optJSONObject("project")?.let { info -> listOf("clientName", "contact", "shootDate", "startMinutes", "endMinutes", "location", "calendarLabelOverride", "calendarColorHex").forEach { info.remove(it) } }
    metadata.remove("_androidDate")
    return result.copy(iosMetadata = metadata.toString())
}

/** New identities keep future edits to a created project separate from its template. */
internal fun projectFromTemplate(template: Project, title: String): Project =
    templateSnapshot(template).copy(name = title.ifBlank { "未命名项目" })
