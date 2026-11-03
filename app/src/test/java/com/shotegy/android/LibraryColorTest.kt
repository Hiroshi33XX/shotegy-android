// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import org.junit.Assert.*
import org.junit.Test

class LibraryColorTest {
    @Test fun hslRoundTripsPrimaryAndNeutralColors() {
        listOf("#FF0000", "#00FF00", "#0000FF", "#FFFFFF", "#000000", "#808080", "#677D52").forEach { hex ->
            assertEquals(hex, hslToHex(hslFromHex(hex)!!))
        }
        assertNull(hslFromHex("#123"))
        assertNull(hslFromHex("#GG1122"))
        assertEquals("#FF0000", hslToHex(HslColor(360f, 1f, .5f)))
    }

    @Test fun addingOrRemovingFolderPreservesResourceAndOtherMemberships() {
        val template = Project(name = "模板内容", cards = listOf(ShotCard(guidanceItems = listOf(PhraseItem(text = "抬头")))))
        val original = LibraryItem(kind = "项目模板", title = "测试", folderIds = listOf("A", "B"), template = template)
        val added = addToFolder(original, "C")
        assertEquals(listOf("A", "B", "C"), added.folderIds)
        assertEquals(added, addToFolder(added, "C"))
        val removed = removeFromFolder(added, "A")
        assertEquals(listOf("B", "C"), removed.folderIds)
        assertEquals(template, removed.template)
        assertEquals(listOf("A", "B"), original.folderIds)
    }
}
