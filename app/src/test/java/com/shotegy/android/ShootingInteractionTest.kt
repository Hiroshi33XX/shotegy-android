// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

/** Drives the actual Compose screen, including the separate execution-sheet window. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h890dp-xhdpi")
class ShootingInteractionTest {
    private lateinit var frameClock: BroadcastFrameClock
    private lateinit var recomposer: Recomposer
    private lateinit var compositionScope: CoroutineScope
    private var frameTime = 0L
    @Before fun useRealisticFrameTiming() {
        AppLanguage.save(RuntimeEnvironment.getApplication(),AppLanguage.SIMPLIFIED)
        // Popups poll their anchor every frame. A zero-duration synthetic vsync
        // loops forever instead of advancing the test clock.
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        frameClock = BroadcastFrameClock()
        compositionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + frameClock)
        recomposer = Recomposer(compositionScope.coroutineContext)
        compositionScope.launch { recomposer.runRecomposeAndApplyChanges() }
    }
    @After fun closeCompositionClock() { recomposer.cancel(); compositionScope.cancel();AppLanguage.save(RuntimeEnvironment.getApplication(),AppLanguage.SIMPLIFIED) }
    private fun compose(activity: ComponentActivity, content: @Composable () -> Unit) {
        activity.setContent(parent = recomposer, content = content)
    }
    private fun roots(): List<View> {
        val manager = RuntimeEnvironment.getApplication().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Popup roots are tracked by WindowManagerImpl in Robolectric and are
        // not all returned by the framework's WindowInspector.
        return (WindowInspector.getGlobalWindowViews() + Shadow.extract<ShadowWindowManagerImpl>(manager).views).distinct()
    }
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun nodes(): List<SemanticsNode> {
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        return roots().flatMap(::views).mapNotNull { view ->
            view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }?.invoke(view) as? SemanticsOwner
        }.flatMap { descendants(it.rootSemanticsNode) }
    }
    private fun settle() {
        repeat(8) {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
            frameTime += 200_000_000L
            frameClock.sendFrame(frameTime)
            shadowOf(Looper.getMainLooper()).idle()
            roots().forEach { view ->
                view.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1780, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, 800, 1780)
            }
        }
    }
    private fun hasText(node: SemanticsNode, text: String) = node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
    private fun click(text: String) {
        val node = nodes().first { hasText(it, text) && it.config.getOrNull(SemanticsActions.OnClick) != null }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
        settle()
    }

    @Test fun galleryTapOpensTheTappedReferenceAndClosingPreservesSceneMode() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            val activity = controller.get()
            activity.getSharedPreferences("shotegy", Context.MODE_PRIVATE).edit().clear().commit()
            val store = Store(activity)
            val file = File(activity.cacheDir, "reference.png")
            val bitmap = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            val one = store.saveReferenceImage(Uri.fromFile(file)); val two = store.saveReferenceImage(Uri.fromFile(file))
            val card = ShotCard(scene = "河岸", shotScale = "中景", guidance = "拿花向前走", referenceImages = listOf(one, two))
            val project = Project(name = "拍摄测试", scenes = listOf("河岸"), cards = listOf(card))
            store.saveProject(project)
            compose(activity) { MaterialTheme { Surface { ShootingScreen(store.projects.single(), store, {}) } } }
            settle()
            click("按小场景")
            assertTrue(nodes().any { hasText(it, "河岸") })
            assertFalse(nodes().any { hasText(it, "拿花向前走") })
            val image = nodes().single { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("拍摄单元 01，参考图 2") == true }
            assertTrue(image.boundsInRoot.height < 240)
            image.config[SemanticsActions.OnClick].action!!.invoke()
            settle()
            assertTrue(nodes().any { hasText(it, "拍摄单元") })
            assertTrue("Sheet text: " + nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.joinToString { it.text }, nodes().any { hasText(it, "2 / 2") })
            assertTrue(nodes().any { hasText(it, "1. 拿花向前走") })
            click("已完成")
            assertEquals("已完成", store.projects.single().cards.single().status)
            // The sheet and underlying screen both have 完成; close the topmost one.
            val close = nodes().last { hasText(it, "完成") && it.config.getOrNull(SemanticsActions.OnClick) != null }
            close.config[SemanticsActions.OnClick].action!!.invoke()
            settle()
            assertFalse(nodes().any { hasText(it, "拍摄单元") })
            val selectedMode = nodes().single { hasText(it, "按小场景") }
            assertEquals(true, selectedMode.config.getOrNull(SemanticsProperties.Selected))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun referenceEditorUsesFullSizePortraitTilesAndOverlayMenu() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            val activity = controller.get()
            val store = Store(activity)
            val file = File(activity.cacheDir, "portrait-tile.png")
            val bitmap = Bitmap.createBitmap(120, 300, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            val id = store.saveReferenceImage(Uri.fromFile(file))
            compose(activity) { MaterialTheme { Surface { ReferenceImagesEditor(store, listOf(id)) {} } } }
            settle()
            val image = nodes().single { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("参考图") == true }
            // At density 2, the image fills the entire 150 × 190dp tile.
            assertEquals(300f, image.boundsInRoot.width, .1f)
            assertEquals(380f, image.boundsInRoot.height, .1f)
            assertFalse(nodes().any { hasText(it, "图片操作 ···") })
            assertTrue(nodes().any { hasText(it, "封面") })
            val menu = nodes().single { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("图片操作") == true }
            assertTrue(menu.boundsInRoot.top >= image.boundsInRoot.top)
            assertTrue(menu.boundsInRoot.right <= image.boundsInRoot.right)
            assertTrue(menu.boundsInRoot.bottom < image.boundsInRoot.bottom)
            assertTrue(menu.config.getOrNull(SemanticsActions.OnClick) != null)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun templateInfoOnlyExposesReusablePlanningFields() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            val activity = controller.get(); val store = Store(activity)
            val item = LibraryItem(kind = "项目模板", title = "模板", template = Project(clientName = "PRIVATE_CLIENT", location = "PRIVATE_LOCATION"))
            compose(activity) { MaterialTheme { TemplateInfoEditor(item, store, {}, {}) } }
            settle()
            assertTrue(nodes().any { hasText(it, "项目策划") })
            assertTrue(nodes().any { hasText(it, "模特风格") })
            listOf("客户", "联系方式", "拍摄日期", "地点", "PRIVATE_CLIENT", "PRIVATE_LOCATION").forEach { text -> assertFalse(nodes().any { hasText(it, text) }) }
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun exportPageOffersAllFourDeliverablesAndGroupingDisablesCardOnlyOptions() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            val activity = controller.get(); val store = Store(activity)
            compose(activity) { MaterialTheme { ProjectExport(Project(name = "样例"), store, {}) } }
            settle()
            listOf("导出策划长图", "逐条导出脚本图", "导出 PDF", "导出项目文件").forEach { title -> assertTrue(nodes().any { hasText(it, title) }) }
            click("按景别")
            val switches = nodes().filter { it.config.getOrNull(SemanticsProperties.ToggleableState) != null }
            assertEquals(5, switches.size)
            assertEquals(2, switches.count { it.config.contains(SemanticsProperties.Disabled) })
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun profileDataSectionHasIOSActionsWithoutLegacyEntry() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            val activity = controller.get(); val store = Store(activity)
            compose(activity) { MaterialTheme { ProfileScreen(store, "system", {}) } }
            settle()
            assertTrue(nodes().any { hasText(it, "完整数据备份") })
            assertTrue(nodes().any { hasText(it, "导入完整备份") })
            assertFalse(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text.contains("旧版") || t.text.contains("JSON") || t.text.contains("旧备份") } == true })
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun mainPageTitlesShareTheSameBaselineAndLeadingInset() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            val activity=controller.get();val store=Store(activity)
            activity.getSharedPreferences("shotegy",Context.MODE_PRIVATE).edit().clear().commit()
            val bounds=mutableListOf<androidx.compose.ui.geometry.Rect>()
            listOf("项目","资源库","我的").forEachIndexed { index,title ->
                compose(activity) { MaterialTheme { Surface { when(index){0->ProjectsScreen(store);1->LibraryScreen(store);else->ProfileScreen(store,"system",{})} } } }
                settle()
                bounds+=nodes().first { hasText(it,title) }.boundsInRoot
            }
            bounds.forEach { assertEquals(32f,it.left,1f);assertEquals(bounds.first().top,it.top,1f);assertEquals(bounds.first().height,it.height,1f) }
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun languagePickerUpdatesTheInterfaceAndKeepsProjectContentUntouched() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            val activity=controller.get();val store=Store(activity)
            activity.getSharedPreferences("shotegy",Context.MODE_PRIVATE).edit().clear().commit()
            val project=Project(name="项目",cards=listOf(ShotCard(guidance="拍摄方法",scene="中景")))
            store.saveProject(project)
            val language=androidx.compose.runtime.mutableStateOf(AppLanguage.SIMPLIFIED)
            compose(activity) { androidx.compose.runtime.CompositionLocalProvider(LocalAppLanguage provides language.value,LocalSetAppLanguage provides { value->AppLanguage.save(activity,value);language.value=value }) { MaterialTheme { ProfileScreen(store,"system",{}) } } }
            settle();click("语言");click("English")
            assertTrue(nodes().any { hasText(it,"Language") })
            assertEquals(AppLanguage.ENGLISH,AppLanguage.saved(activity))
            click("‹ Back")
            assertTrue(nodes().any { hasText(it,"Me") })
            assertTrue(nodes().any { hasText(it,"Full Data Backup") })
            click("Language");click("繁體中文");click("‹ 返回")
            assertTrue(nodes().any { hasText(it,"完整數據備份") })
            assertEquals(AppLanguage.TRADITIONAL,AppLanguage.saved(activity))
            assertEquals(project,store.projects.single())
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun fontLicenseCanBeOpenedFromAboutAndPreservesTheOriginalText() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            val activity=controller.get();val store=Store(activity)
            compose(activity) { MaterialTheme { ProfileScreen(store,"system",{}) } }
            settle();click("关于拍序");click("第三方许可");click("Noto Serif SC")
            val original=listOf("fonts/NOTICE.txt","fonts/OFL.txt").joinToString("\n\n"){path->activity.assets.open(path).bufferedReader().use{it.readText()}}
            assertTrue(nodes().any { hasText(it,"Noto 字体许可") })
            assertTrue(nodes().any { hasText(it,original) })
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun englishScreensDoNotLeakSystemLabelsOrChangeTemplateNames() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            val activity=controller.get();val store=Store(activity)
            activity.getSharedPreferences("shotegy",Context.MODE_PRIVATE).edit().clear().commit()
            repeat(9){index->
                compose(activity) { androidx.compose.runtime.CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) { MaterialTheme { Surface { when(index){0->ProjectsScreen(store);1->LibraryScreen(store);2->ProfileScreen(store,"system",{});3->ProjectExport(Project(name="User title"),store,{});4->ShotEditor(store,Project(name="User title"),null,{},{});5->TemplateInfoEditor(LibraryItem(kind="项目模板",title="User template",template=Project(name="User title")),store,{},{});6->StylingEditor(Project(name="User title"),store,{},{});7->ScriptReorderEditor(Project(name="User title"),store,{});else->CalendarScreen(store)} } } } }
                settle()
                val texts=nodes().flatMap{it.config.getOrNull(SemanticsProperties.Text).orEmpty()}.map{it.text}
                assertFalse("Chinese system text on screen $index: $texts",texts.any{Regex("[\\u3400-\\u9fff]").containsMatchIn(it)})
            }
        } finally { controller.pause().stop().destroy() }
    }

}
