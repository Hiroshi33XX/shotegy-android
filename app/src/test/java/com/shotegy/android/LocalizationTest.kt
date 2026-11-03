// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalizationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun setup() { AppLanguage.save(context,AppLanguage.SIMPLIFIED) }
    @After fun resetLanguage() { AppLanguage.save(context,AppLanguage.SIMPLIFIED) }

    @Test fun languageChoicePersistsAndUsesStableTags() {
        AppLanguage.entries.forEach { language -> AppLanguage.save(context,language);assertEquals(language,AppLanguage.saved(context)) }
        assertEquals(AppLanguage.SIMPLIFIED,AppLanguage.fromTag("unknown"))
        assertEquals(listOf("zh-Hans","zh-Hant","en"),AppLanguage.entries.map{it.tag})
    }
    @Test fun labelsAndMessagesHaveCompleteEnglishAndTraditionalTranslations() {
        val english=AppStrings(context,AppLanguage.ENGLISH)
        val traditional=AppStrings(context,AppLanguage.TRADITIONAL)
        assertEquals("Projects",english.text("项目"))
        assertEquals("Library",english.text("资源库"))
        assertEquals("資源庫",traditional.text("资源库"))
        assertEquals("Full Data Backup",english.text("完整数据备份"))
        assertEquals("Shot 01",english.message("脚本 01"))
        assertEquals("Unable to save: Invalid image identifier",english.message("无法保存：无效的图片标识"))
        val catalog=JSONObject(context.assets.open("localization.json").bufferedReader().use{it.readText()})
        for(key in catalog.keys()) {
            val values=catalog.getJSONObject(key)
            assertTrue("Missing English: $key",values.getString("en").isNotBlank())
            assertTrue("Missing Traditional: $key",values.getString("zh-Hant").isNotBlank())
            assertFalse("Untranslated English: $key",Regex("[\\u3400-\\u9fff]").containsMatchIn(values.getString("en")))
        }
    }
    @Test fun formattingNeverReinterpretsUserTextOrModelKeys() {
        val english=AppStrings(context,AppLanguage.ENGLISH)
        val name="项目{1} / 近景 / 特写"
        assertEquals("“$name” and its shots will be deleted.",english.text("“{0}”及其镜头将被删除。",name))
        assertEquals("$name: 7",english.text("{0}: {1}",name,7))
        val model=Project(name="项目",scenes=listOf("中景"),cards=listOf(ShotCard(scene="中景",guidance="拍摄方法",status="未拍")))
        AppLanguage.save(context,AppLanguage.ENGLISH)
        assertEquals("项目",model.name);assertEquals("中景",model.cards.single().scene);assertEquals("拍摄方法",model.cards.single().guidance);assertEquals("未拍",model.cards.single().status)
        assertEquals("项目_Plan.pdf",english.text("{0}_策划案.pdf",model.name))
    }
    @Test fun originalFontLicenseAndNoticeAreBundled() {
        val notice=context.assets.open("fonts/NOTICE.txt").bufferedReader().use{it.readText()}
        val license=context.assets.open("fonts/OFL.txt").bufferedReader().use{it.readText()}
        assertTrue(notice.contains("Noto Serif SC Regular"))
        assertTrue(license.contains("SIL OPEN FONT LICENSE"))
        assertTrue(license.contains("Version 1.1"))
    }
    @Test fun localizedContextRetainsActivityForCameraDialogsAndShare() {
        val controller=org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        try {
            val activity=controller.get()
            val localized=localizedActivityContext(activity,AppLanguage.ENGLISH.locale)
            assertEquals("en",localized.resources.configuration.locales[0].language)
            assertSame(activity,(localized as android.content.ContextWrapper).baseContext)
            localized.startActivity(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain"))
            assertEquals(android.content.Intent.ACTION_SEND,org.robolectric.Shadows.shadowOf(activity).nextStartedActivity.action)
        } finally { controller.pause().stop().destroy() }
    }

}
