// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import java.util.UUID

/** Detached staging model: deliberately not persisted until editors use IDs as their source of truth. */
data class MigrationScene(val id: String, val name: String, val sortOrder: Int)
data class MigrationAsset(val id: String, val imageId: String, val thumbnailId: String, val sortOrder: Int, val kind: String = "")
data class MigrationScout(val scout: Scout, val thumbnailId: String, val sortOrder: Int)
data class MigrationCard(val cardId: String, val sceneId: String?, val references: List<MigrationAsset>, val scouts: List<MigrationScout>)
data class ProjectMigrationDraft(val source: Project, val scenes: List<MigrationScene>, val cards: List<MigrationCard>, val projectAssets: List<MigrationAsset>)

private fun identity(vararg parts: String) = UUID.nameUUIDFromBytes(parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)).toString()

/**
 * Makes a detached deterministic projection of old name/list data without changing it.
 * Thumbnails use the original image when the legacy format had no independent thumbnail.
 * This is staging only: it does not support storing imported metadata or modifying the UI.
 */
fun Project.makeMigrationDraft(): ProjectMigrationDraft {
    require(scenes.none { it.isBlank() } && scenes.distinct().size == scenes.size) { "旧项目包含空场景或同名场景，无法安全转换" }
    require(cards.map { it.id }.distinct().size == cards.size && cards.all { it.id.isNotBlank() }) { "脚本标识重复或为空" }
    val names = (scenes + cards.map { it.scene }.filter { it.isNotBlank() }).distinct()
    val sceneRecords = names.mapIndexed { index, name -> MigrationScene(identity("scene", id, name), name, index) }
    val byName = sceneRecords.associateBy { it.name }
    val cardRecords = cards.map { card ->
        MigrationCard(card.id, byName[card.scene]?.id,
            card.referenceImages.mapIndexed { index, image ->
                require(image.isNotBlank()) { "参考图标识为空" }
                MigrationAsset(identity("reference", id, card.id, index.toString()), image, image, index)
            },
            card.scouts.mapIndexed { index, scout -> MigrationScout(scout, scout.imageId, index) })
    }
    val assets = stylingImages.toSortedMap().flatMap { (kind, images) ->
        require(kind.isNotBlank()) { "造型素材类型为空" }
        images.mapIndexed { index, image ->
            require(image.isNotBlank()) { "造型素材标识为空" }
            MigrationAsset(identity("asset", id, kind, index.toString()), image, image, index, kind)
        }
    }
    return ProjectMigrationDraft(this, sceneRecords, cardRecords, assets)
}
