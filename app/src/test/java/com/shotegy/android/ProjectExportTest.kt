// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProjectExportTest {
    private lateinit var store: Store
    private lateinit var project: Project
    private lateinit var renderer: ProjectExportService
    private val context get() = RuntimeEnvironment.getApplication()
    private fun image(name: String, width: Int, height: Int, color: Int): String {
        val bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { this.color = Color.WHITE; textSize = 22f }
        canvas.drawText(name,12f,36f,paint)
        val file = File(context.cacheDir,"$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        return store.saveReferenceImage(Uri.fromFile(file))
    }
    @After fun resetLanguage() { AppLanguage.save(context,AppLanguage.SIMPLIFIED) }
    @Before fun setup() {
        AppLanguage.save(context,AppLanguage.SIMPLIFIED)
        context.getSharedPreferences("shotegy",Context.MODE_PRIVATE).edit().clear().commit()
        store=Store(context)
        val a=image("PORTRAIT",180,300,Color.rgb(142,163,125))
        val b=image("LANDSCAPE",300,180,Color.rgb(188,144,105))
        val c=image("DETAIL",180,240,Color.rgb(111,142,166))
        project=Project(name="河畔黄昏人像",theme="初秋日落",style="自然、温暖的纪实人像",clientName="PRIVATE_CLIENT",location="PRIVATE_LOCATION",date="2026-11-02",startTime="16:00",endTime="18:00",modelStyle="自然舒展",desiredWeather="晴朗或薄云",equipment="全画幅相机 · 50mm / 85mm",notes="先观察岸边反光，再确认安全的行走路径。",scenes=listOf("河岸","桥下"),clothingStyle="日系 / 日常",wardrobe="米色长裙",makeup="低饱和暖色妆容",props="一束鲜花",clothingColors=listOf("#D9CBB7","#829475"),stylingImages=mapOf("clothing" to listOf(a)),cards=listOf(
            ShotCard(scene="河岸",shotScale="中景",guidanceItems=listOf(PhraseItem(text="拿花向前走，听到提示后回头看镜头。"),PhraseItem(text="停在光线边缘，自然整理头发。")),techniqueItems=listOf(PhraseItem(text="METHOD_MARKER · 逆光拍摄，注意面部曝光。")),referenceImages=listOf(a,b,c),scouts=listOf(Scout(imageId=c)),timeNote="日落前二十分钟",startTime="16:30",endTime="17:00"),
            ShotCard(scene="桥下",shotScale="远景",guidance="坐在岸边望向水面，保持动作舒展。",referenceImages=listOf(b)),
            ShotCard(scene="河岸",guidance="没有参考图的脚本也要完整导出。")))
        renderer=ProjectExportService(context,store)
    }
    @Test fun organizedExportsSuppressMethodsAndScoutsButRetainAllReferenceImages() {
        val options=ProjectExportOptions(showClientName=false,showLocation=false,showTechniques=true,showScouts=true,organization=ExportOrganization.SCENE)
        val blocks=renderer.layoutBlocks(project,options,true)
        val text=blocks.flatMap{it.elements}.joinToString("\n"){it.text}
        assertFalse(text.contains("PRIVATE_CLIENT"));assertFalse(text.contains("PRIVATE_LOCATION"));assertFalse(text.contains("METHOD_MARKER"))
        assertTrue(text.contains("没有参考图的脚本也要完整导出"))
        assertEquals(5,blocks.flatMap{it.elements}.count{it.kind==ExportElementKind.IMAGE}) // four references plus preparation photo
        blocks.forEach{block->block.elements.forEach{e->assertTrue("$e exceeds ${block.width}",e.x>=-.01f&&e.x+e.width<=block.width+.1f);assertTrue(e.y>=0&&e.y+e.height<=block.height+.1f)}}
    }
    @Test fun nativeOutputsUseIOSDimensionsNamesAndIncludePreparationSheets() {
        val root=File("build/export-proof").apply{mkdirs()}
        val options=ProjectExportOptions(showTechniques=true,showScouts=true)
        val images=renderer.export(project,options,ProjectExportKind.CARD_IMAGES,File(root,"cards"))
        assertEquals(4,images.size)
        assertTrue(images.first().name.contains("_项目准备_01.jpg"))
        assertTrue(images.last().name.contains("_脚本_03.jpg"))
        images.forEach{file->val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds);assertEquals(1170,bounds.outWidth);assertTrue(bounds.outHeight>0)}
        val pdf=renderer.export(project,options,ProjectExportKind.PDF,File(root,"pdf")).single()
        assertEquals("河畔黄昏人像_策划案.pdf",pdf.name)
        assertEquals("%PDF",pdf.inputStream().use{String(it.readNBytes(4))})
        val grouped=renderer.export(project,options.copy(organization=ExportOrganization.SCENE),ProjectExportKind.LONG_IMAGE,File(root,"scene"))
        assertTrue(grouped.isNotEmpty())
        grouped.forEach{file->val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds);assertEquals(1170,bounds.outWidth);assertTrue(bounds.outHeight<=12000)}
    }
    @Test fun projectFileRemainsTheInteroperableIOSArchive() {
        val output=renderer.export(project,ProjectExportOptions(showClientName=false),ProjectExportKind.PROJECT,File(context.cacheDir,"project-export")).single()
        ZipFile(output).use{zip->assertNotNull(zip.getEntry("project.json"));assertTrue(zip.size()>1)}
        output.inputStream().use{input->IOSBackup.prepare(input,context.cacheDir,store).use{prepared->assertEquals("PRIVATE_CLIENT",prepared.projects.single().clientName);assertEquals(3,prepared.projects.single().cards.size)}}
    }
    @Test fun pdfKeepsEveryReferenceWhileMobileSequenceUsesFirstTwoLikeIOS() {
        val options=ProjectExportOptions(showScouts=true)
        val mobile=renderer.layoutBlocks(project,options,true).flatMap{it.elements}.filter{it.kind==ExportElementKind.IMAGE}
        val pdf=renderer.layoutBlocks(project,options,false).flatMap{it.elements}.filter{it.kind==ExportElementKind.IMAGE}
        assertEquals(5,mobile.size)
        assertEquals(6,pdf.size)
        pdf.forEach{e->val ratio=store.referenceImage(e.imageId!!)?.let(::referenceImageRatio)!!;assertEquals(ratio,e.width/e.height,.01f)}
    }
    @Test fun fileNamesAndSlicePackingAreSafeAndDoNotLoseBlocks() {
        assertFalse(safeExportName("a/b:c?. ").contains('/'))
        assertTrue(safeExportName("摄".repeat(200)).toByteArray().size<=180)
        val blocks=listOf(ExportBlock(390f,2200f,emptyList()),ExportBlock(390f,1800f,emptyList()),ExportBlock(390f,90f,emptyList()))
        val packed=packExportSlices(blocks.map{ExportSlice(it)},4000f,8f)
        assertEquals(2,packed.size);assertEquals(blocks,packed.flatten().map{it.block})
    }
    @Test fun longImagesActuallySplitAtTheIOSMaximumHeight() {
        val many=project.copy(cards=List(6){project.cards.first().copy(id=java.util.UUID.randomUUID().toString(),scouts=emptyList())})
        val files=renderer.export(many,ProjectExportOptions(),ProjectExportKind.LONG_IMAGE,File("build/export-proof/long-split"))
        assertTrue(files.size>1)
        files.forEach { file ->
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(file.path,bounds)
            assertEquals(1170,bounds.outWidth)
            assertTrue(bounds.outHeight in 1..12000)
        }
    }
    @Test fun localizedExportsTranslateLabelsAndFileNamesWithoutChangingProjectText() {
        for (language in listOf(AppLanguage.ENGLISH,AppLanguage.TRADITIONAL)) {
            AppLanguage.save(context,language)
            val service=ProjectExportService(context,store)
            val texts=service.layoutBlocks(project,ProjectExportOptions(),true).flatMap{it.elements}.map{it.text}
            assertTrue(texts.contains(project.name))
            assertTrue(texts.any{it.contains(project.cards.first().guidanceItems.first().text)})
            assertTrue(texts.contains(if(language==AppLanguage.ENGLISH) "Shoot Date" else "拍攝日期"))
            assertFalse(texts.contains("拍摄日期"))
            val output=File("build/export-proof/${language.tag}")
            val file=service.export(project,ProjectExportOptions(),ProjectExportKind.PDF,output).single()
            assertEquals(if(language==AppLanguage.ENGLISH) "河畔黄昏人像_Plan.pdf" else "河畔黄昏人像_策劃案.pdf",file.name)
            val archive=service.export(project,ProjectExportOptions(),ProjectExportKind.PROJECT,output).single()
            archive.inputStream().use{input->IOSBackup.prepare(input,context.cacheDir,store).use{prepared->
                assertEquals(project.name,prepared.projects.single().name)
                assertEquals(project.cards.first().shotScale,prepared.projects.single().cards.first().shotScale)
            }}
        }
    }

}
