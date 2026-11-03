// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import kotlin.math.*

internal enum class ExportOrganization(val label: String) { SEQUENCE("按卡片"), SCALE("按景别"), SCENE("按小场景") }
internal enum class ProjectExportKind(val title: String) { LONG_IMAGE("导出策划长图"), CARD_IMAGES("逐条导出脚本图"), PDF("导出 PDF"), PROJECT("导出项目文件") }
internal data class ProjectExportOptions(val showClientName: Boolean = true, val showLocation: Boolean = true, val showTechniques: Boolean = false, val showScouts: Boolean = false, val showWatermark: Boolean = true, val organization: ExportOrganization = ExportOrganization.SEQUENCE) {
    fun organized() = if (organization == ExportOrganization.SEQUENCE) this else copy(showTechniques = false, showScouts = false)
}
internal fun safeExportName(value: String): String {
    val cleaned = value.replace(Regex("[\\\\/:*?\"<>|\u0000-\u001F]"), "_").trimEnd('.', ' ').trim()
    val out = StringBuilder(); var bytes = 0
    cleaned.codePoints().forEachOrdered { point -> val part = String(Character.toChars(point)); val size = part.toByteArray(Charsets.UTF_8).size; if (bytes + size <= 180) { out.append(part); bytes += size } else bytes = 181 }
    return out.toString().ifBlank { "Shotegy" }
}

internal enum class ExportElementKind { TEXT, IMAGE, RULE, DISC, SWATCH, BADGE }
internal data class ExportElement(val kind: ExportElementKind, val x: Float, val y: Float, val width: Float, val height: Float, val text: String = "", val imageId: String? = null, val size: Float = 15f, val color: Int = Color.rgb(23,23,23), val tracking: Float = 0f)
internal data class ExportBlock(val width: Float, val height: Float, val elements: List<ExportElement>)
internal data class ExportSlice(val block: ExportBlock, val top: Float = 0f, val height: Float = block.height)
internal fun packExportSlices(slices: List<ExportSlice>, maximumHeight: Float, gap: Float): List<List<ExportSlice>> {
    val result = mutableListOf<MutableList<ExportSlice>>(); var height = 0f
    slices.forEach { slice ->
        if (result.isEmpty() || (result.last().isNotEmpty() && height + gap + slice.height > maximumHeight)) { result.add(mutableListOf()); height = 0f }
        if (result.last().isNotEmpty()) height += gap
        result.last().add(slice); height += slice.height
    }
    return result
}

/** Native Canvas export, using the iOS editorial font and measured layout blocks. */
internal class ProjectExportService(private val context: Context, private val store: Store) {
    private val strings = AppStrings(context, AppLanguage.saved(context))
    private fun t(key: String, vararg arguments: Any?) = strings.text(key, *arguments)
    private val font = Typeface.createFromAsset(context.assets, "fonts/NotoSerifSC-Regular.ttf")
    private val ink = Color.rgb(23,23,23); private val muted = Color.rgb(120,120,115); private val rule = Color.rgb(214,214,209)
    private fun layout(text: String, size: Float, width: Float, color: Int = ink, tracking: Float = 0f): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = size; this.color = color; letterSpacing = tracking / size }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1,width.roundToInt())).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(2f, 1f).build()
    }
    private inner class Block(val width: Float) {
        val elements = mutableListOf<ExportElement>(); var bottom = 0f
        fun text(value: String, x: Float, y: Float, w: Float, size: Float, color: Int = ink, tracking: Float = 0f): Float {
            if (value.isEmpty()) return 0f
            val h = layout(value,size,w,color,tracking).height.toFloat()
            elements += ExportElement(ExportElementKind.TEXT,x,y,w,h,value,size=size,color=color,tracking=tracking); bottom=max(bottom,y+h); return h
        }
        fun rightText(value: String, right: Float, y: Float, maximumWidth: Float, size: Float, color: Int = ink, tracking: Float = 0f) {
            val measured = layout(value,size,maximumWidth,color,tracking)
            val w = min(maximumWidth,measured.getLineWidth(0)+1f)
            text(value,right-w,y,w,size,color,tracking)
        }
        fun line(x: Float, y: Float, w: Float, h: Float = .7f) { elements += ExportElement(ExportElementKind.RULE,x,y,w,h,color=rule); bottom=max(bottom,y+h) }
        fun photo(id: String, x: Float, y: Float, w: Float, maxHeight: Float, fixedHeight: Float? = null, centered: Boolean = true): Float {
            val ratio = store.referenceImage(id)?.let(::referenceImageRatio) ?: .8f
            val frameHeight = fixedHeight ?: min(maxHeight,w / ratio)
            val h = min(frameHeight,w / ratio); val drawnWidth = h*ratio
            elements += ExportElement(ExportElementKind.IMAGE,x+(if(centered)(w-drawnWidth)/2 else 0f),y+(frameHeight-h)/2,drawnWidth,h,imageId=id); bottom=max(bottom,y+frameHeight); return frameHeight
        }
        fun place(block: ExportBlock, x: Float, y: Float) { elements += block.elements.map { it.copy(x=it.x+x,y=it.y+y) }; bottom=max(bottom,y+block.height) }
        fun build(extra: Float = 0f) = ExportBlock(width,max(1f,bottom+extra),elements.toList())
    }
    private fun chunks(text: String): List<String> {
        val values = text.codePoints().toArray().toList()
        return values.chunked(320).map { points -> String(points.toIntArray(),0,points.size) }
    }
    private fun field(label: String, text: String, width: Float, mobile: Boolean): ExportBlock {
        val b=Block(width); val h=b.text(t(label),0f,0f,width,if(mobile)12f else 9f,muted)
        b.text(text,0f,if(h>0)h+4 else 0f,width,if(mobile)15f else 10f); return b.build()
    }
    private fun preparation(project: Project, options: ProjectExportOptions, mobile: Boolean): List<ExportBlock> {
        val width=if(mobile)390f else 519f; val inset=if(mobile)16f else 0f; val inner=width-2*inset; val gap=if(mobile)18f else 20f
        val blocks=mutableListOf<ExportBlock>()
        val title=Block(width); var y=if(mobile)28f else 0f
        y+=title.text("SHOTEGY / SHOOTING PLAN",inset,y,inner,if(mobile)9.5f else 7.5f,muted,if(mobile)1.2f else 1f)+10f
        y+=title.text(project.name,inset,y,inner,if(mobile)31f else 27f)+10f; title.line(inset,y,inner); blocks+=title.build(8f)
        val groups=listOf(listOf("拍摄日期" to formattedDate(project.date),"拍摄时间" to time(project.startTime,project.endTime)),listOf("客户" to if(options.showClientName)project.clientName else "","拍摄地点" to if(options.showLocation)project.location else ""), listOf("主题" to project.theme,"风格" to project.style),listOf("模特风格" to project.modelStyle,"希望天气" to project.desiredWeather),listOf("器材" to project.equipment),listOf("项目备注" to project.notes))
        fun addFields(values: List<Pair<String,String>>) {
            val b=Block(width); val w=(inner-gap*(values.size-1))/values.size; val pad=if(mobile)5f else 3f
            values.forEachIndexed { i,p -> b.place(field(p.first,p.second,w,mobile),inset+i*(w+gap),pad) }; blocks+=b.build(pad)
        }
        groups.forEach { group ->
            val pending=mutableListOf<Pair<String,String>>()
            group.filter { it.second.isNotBlank() }.forEach { pair ->
                val fits=pair.first!="项目备注" && !pair.second.contains('\n') && layout(pair.second,if(mobile)15f else 10f,(inner-gap)/2).lineCount<=2
                if(fits) { pending+=pair; if(pending.size==2) { addFields(pending.toList()); pending.clear() } }
                else { if(pending.isNotEmpty()) { addFields(pending.toList()); pending.clear() }; chunks(pair.second).forEachIndexed { i,s -> addFields(listOf((if(i==0)pair.first else "") to s)) } }
            }
            if(pending.isNotEmpty()) addFields(pending.toList())
        }
        val kinds=listOf(Triple("服装","clothing",listOf(if(project.clothingStyleIsCustom) project.clothingStyle else t(project.clothingStyle),project.wardrobe).filter{it.isNotBlank()}.joinToString(" · ")),Triple("妆容","makeup",project.makeup),Triple("发型","hair",project.hair),Triple("配饰","accessories",project.accessories),Triple("鞋履","shoes",project.shoes),Triple("道具","props",project.props))
        val hasStyling=kinds.any{it.third.isNotBlank()} || project.clothingColors.isNotEmpty() || project.stylingImages.values.any{it.isNotEmpty()}
        if(hasStyling) {
            val heading=Block(width); heading.text(t("造型与道具"),inset,if(mobile)8f else 4f,inner,if(mobile)20f else 16f); blocks+=heading.build(8f)
            val pending=mutableListOf<Triple<String,String,List<String>>>()
            fun appendStyling(values: List<Triple<String,String,List<String>>>) {
                val b=Block(width); val w=(inner-gap*(values.size-1))/values.size
                values.forEachIndexed { i,v ->
                    val x=inset+i*(w+gap); val f=field(v.first,v.second,w,mobile); b.place(f,x,if(mobile)8f else 4f)
                    var rowY=f.height+12f; var col=0; val cols=max(1,(w/55).toInt())
                    v.third.forEach { hex ->
                        if(col>=cols){col=0;rowY+=40}
                        val color=runCatching{Color.parseColor(hex)}.getOrDefault(Color.LTGRAY)
                        b.elements+=ExportElement(ExportElementKind.SWATCH,x+col*55,rowY,18f,18f,color=color)
                        b.text(hex,x+col*55,rowY+22,54f,6.5f,muted); b.bottom=max(b.bottom,rowY+36); col++
                    }
                }; blocks+=b.build(if(mobile)8f else 4f)
            }
            kinds.forEach { kind ->
                val colors=if(kind.second=="clothing")project.clothingColors else emptyList()
                if(kind.third.isNotBlank()||colors.isNotEmpty()) {
                    val fits=!kind.third.contains('\n')&&layout(kind.third,if(mobile)15f else 10f,(inner-gap)/2).lineCount<=2
                    if(fits){pending+=Triple(kind.first,kind.third,colors);if(pending.size==2){appendStyling(pending.toList());pending.clear()}}
                    else {if(pending.isNotEmpty()){appendStyling(pending.toList());pending.clear()};val parts=chunks(kind.third);parts.forEachIndexed{i,s->appendStyling(listOf(Triple(if(i==0)kind.first else "",s,if(i==parts.lastIndex)colors else emptyList())))}}
                }
            }
            if(pending.isNotEmpty())appendStyling(pending.toList())
            val photos=kinds.flatMap{kind->project.stylingImages[kind.second].orEmpty().map{kind.first to it}}
            val maxW=if(mobile)82f else 78f;val maxH=if(mobile)104f else 96f;val columns=max(1,((inner+6)/(maxW+6)).toInt())
            photos.chunked(columns).forEach { row -> val b=Block(width);row.forEachIndexed{i,p->val x=inset+i*(maxW+6);val h=b.photo(p.second,x,0f,maxW,maxH,centered=false);b.text(t(p.first),x,h+4,maxW,if(mobile)9f else 7f,muted)};blocks+=b.build() }
        }
        return blocks
    }
    private fun shotHeader(card: ShotCard,index: Int,width: Float,mobile: Boolean): ExportBlock {
        val b=Block(width);val nSize=if(mobile)27f else 18f; val detail=listOf(t(card.shotScale),card.scene,time(card.startTime,card.endTime),card.timeNote.trim()).filter{it.isNotBlank()}.joinToString(" · ")
        b.text("%02d".format(index+1),0f,0f,if(mobile)40f else 28f,nSize)
        b.text(detail,if(mobile)52f else 37f,if(mobile)10f else 5f,width-(if(mobile)52f else 37f),if(mobile)12.5f else 9.5f,muted);return b.build()
    }
    private fun textParts(card: ShotCard,options: ProjectExportOptions,mobile: Boolean,width: Float): List<ExportBlock> {
        val parts=mutableListOf<ExportBlock>(); val categories=listOf("动作与引导" to card.guidanceEntries.map{it.text},"拍摄方法" to if(options.showTechniques)card.techniqueEntries.map{it.text} else emptyList())
        categories.forEach { cat -> var first=true;cat.second.filter{it.isNotBlank()}.forEach { value -> chunks(value).forEach { chunk ->
            val b=Block(width);var y=0f
            if(first){y+=b.text(t(cat.first),0f,y,width,if(mobile)17f else 11.5f)+if(mobile)7f else 5f;first=false}
            val size=if(mobile)15f else 10.5f;val indent=size*.7f
            b.elements+=ExportElement(ExportElementKind.DISC,0f,y+size*.55f,size*.25f,size*.25f)
            b.text(chunk,indent,y,width-indent,size);parts+=b.build(if(mobile)6f else 8f)
        } } }
        if(parts.isEmpty()){val b=Block(width);b.text(t("尚未添加动作与拍摄提示"),0f,0f,width,if(mobile)15f else 9.8f,muted);parts+=b.build()}
        return parts
    }
    private fun mobileCard(project: Project,card: ShotCard,index: Int,options: ProjectExportOptions,chapter: Boolean): ExportBlock {
        val b=Block(390f);val inset=16f;val inner=358f;var y=26f
        if(chapter)y+=b.text(t("拍摄脚本"),inset,y,inner,20f)+24f
        b.text(project.name.uppercase(),inset,y,inner-80,9.5f,muted,1.2f);b.rightText("SCRIPT %02d".format(index+1),374f,y,80f,9.5f,muted,1.2f)
        y=max(b.bottom,y+12)+10;b.line(inset,y,inner,.8f);y+=16
        val header=shotHeader(card,index,inner,true);b.place(header,inset,y);y+=header.height+18
        val refs=card.referenceImages.take(2);val textW=if(refs.isEmpty())inner else 121f;var textY=y
        textParts(card,options,true,textW).forEach{b.place(it,inset,textY);textY+=it.height}
        var photoY=y
        if(refs.isNotEmpty()){val imageX=inset+121+12+14;val imageW=inner-121-12-14;refs.forEach{photoY+=b.photo(it,imageX,photoY,imageW,430f)+12};b.line(inset+121+12,y,.7f,max(textY,photoY-12)-y)}
        y=max(textY,if(refs.isEmpty())y else photoY-12)
        if(options.showScouts&&card.scouts.isNotEmpty()){y+=18;b.line(inset,y,inner,.6f);y+=10;y+=b.text(t("踩点图"),inset,y,inner,17f)+8;card.scouts.take(2).forEachIndexed{i,s->b.photo(s.imageId,inset+i*183,y,175f,126f,126f)};y+=126}
        if(options.showWatermark){y+=16;b.rightText(t("SHOTEGY · 由拍序生成"),inset+inner,y,180f,9.5f,muted,1.2f)}
        return b.build(26f)
    }
    private data class ShotFragment(val text: List<ExportBlock>,val photos: List<Pair<String,Boolean>>)
    private fun pdfFragments(card: ShotCard,options: ProjectExportOptions): List<ShotFragment> {
        val groups=mutableListOf<MutableList<ExportBlock>>();var h=0f
        textParts(card,options,false,131.88f).forEach{part->if(groups.isEmpty()||h+part.height>520){groups+=mutableListOf<ExportBlock>();h=0f};groups.last()+=part;h+=part.height}
        val photos=card.referenceImages.map{it to false}+if(options.showScouts)card.scouts.map{it.imageId to true} else emptyList()
        return List(max(groups.size,(photos.size+1)/2)){i->ShotFragment(groups.getOrNull(i).orEmpty(),photos.drop(i*2).take(2))}
    }
    private fun pdfShot(card: ShotCard,index: Int,fragment: ShotFragment): ExportBlock {
        val b=Block(245.5f);val header=shotHeader(card,index,245.5f,false);b.place(header,0f,0f);val top=header.height+9;var y=top
        if(fragment.photos.isEmpty()) {
            // Reflow a text-only fragment to the full column, preserving all text.
            fragment.text.forEach{part->val by=Block(245.5f);var innerY=0f;part.elements.filter{it.kind==ExportElementKind.TEXT}.forEach{e->if(e.x>0)by.elements+=ExportElement(ExportElementKind.DISC,0f,innerY+e.size*.55f,e.size*.25f,e.size*.25f);innerY+=by.text(e.text,e.x,innerY,245.5f-e.x,e.size,e.color)+5};val block=by.build();b.place(block,0f,y);y+=block.height+8}
        } else {
            fragment.text.forEach{b.place(it,0f,y);y+=it.height}
            var photoY=top
            fragment.photos.forEach{p->if(p.second)photoY+=b.text(t("踩点图"),141.88f,photoY,103.62f,11.5f)+4;photoY+=b.photo(p.first,141.88f,photoY,103.62f,132f)+8}
        }
        return b.build()
    }
    private fun notebook(project: Project,options: ProjectExportOptions,mobile: Boolean): List<ExportBlock> {
        val width=if(mobile)390f else 519f;val inset=if(mobile)16f else 0f;val inner=width-inset*2;val result=mutableListOf<ExportBlock>();var chapter=true
        scriptGallerySections(project,project.cards,options.organization==ExportOrganization.SCENE).filter{it.groups.isNotEmpty()}.forEach{section->var sectionStart=true
            section.groups.forEach{group->var groupStart=true
                fun block(content: ExportBlock){val b=Block(width);var y=0f
                    if(chapter){y+=b.text(t("拍摄脚本"),inset,if(mobile)18f else 8f,inner,if(mobile)20f else 16f)+(if(mobile)26 else 16);chapter=false}
                    if(sectionStart){y+=if(mobile)16 else 10;y+=b.text(if(section.name.isBlank())t("未分类") else if(options.organization==ExportOrganization.SCALE)t(section.name) else section.name,inset,y,inner,if(mobile)18f else 13f)+8;b.line(inset,y,inner,.6f);y+=8;sectionStart=false}
                    if(groupStart){y+=4;y+=b.text(if(group.name.isBlank())t("未分类") else if(options.organization==ExportOrganization.SCENE)t(group.name) else group.name,inset,y,inner,if(mobile)13f else 10f,muted)+8;groupStart=false}
                    b.place(content,inset,y);result+=b.build(2f)
                }
                val images=group.cards.flatMap{c->c.referenceImages.map{Triple(c,it,store.referenceImage(it)?.let(::referenceImageRatio)?:.8f)}}
                val ranges=galleryRowRanges(images.map{it.third},images.map{it.first.id},inner,if(mobile)96f else 80f)
                ranges.forEach{range->val target=if(mobile)96f else 80f;val gaps=range.drop(1).map{if(images[it].first.id==images[it-1].first.id)4f else 12f}.sum();val h=min(target,(inner-gaps)/range.sumOf{images[it].third.toDouble()}.toFloat());val row=Block(inner);var x=0f
                    range.forEach{i->if(i>range.first)x+=if(images[i].first.id==images[i-1].first.id)4 else 12;val tile=images[i];val w=h*tile.third;row.photo(tile.second,x,0f,w,h);val badge="%02d".format(project.cards.indexOfFirst{it.id==tile.first.id}+1);row.elements+=ExportElement(ExportElementKind.BADGE,x+3,h-19,24f,16f,color=Color.argb(153,0,0,0));row.text(badge,x+7,h-18,20f,10f,Color.WHITE);x+=w};block(row.build())}
                group.cards.filter{it.referenceImages.isEmpty()}.forEach{c->val guidance=c.guidanceEntries.joinToString("\n"){it.text}.ifBlank{t("尚未添加动作提示")};chunks(guidance).forEachIndexed{i,s->val row=Block(inner);row.text(if(i==0)"%02d".format(project.cards.indexOfFirst{it.id==c.id}+1) else "",0f,5f,24f,if(mobile)12f else 9f,muted);row.text(s,32f,5f,inner-32,if(mobile)14f else 10.5f);block(row.build(5f))}}
            }
        }
        if(chapter){val b=Block(width);b.text(t("拍摄脚本"),inset,8f,inner,if(mobile)20f else 16f);result+=b.build(8f)}
        if(mobile&&options.showWatermark){val b=Block(width);b.rightText(t("SHOTEGY · 由拍序生成"),374f,16f,180f,9.5f,muted,1.2f);result+=b.build(16f)}
        return result
    }
    internal fun layoutBlocks(project: Project,options: ProjectExportOptions,mobile: Boolean): List<ExportBlock> {
        val effective=options.organized();val result=preparation(project,effective,mobile).toMutableList()
        if(options.organization!=ExportOrganization.SEQUENCE) result+=notebook(project,effective,mobile)
        else if(mobile) project.cards.forEachIndexed{i,c->result+=mobileCard(project,c,i,effective,i==0)}
        else {var chapter=true;project.cards.indices.chunked(2).forEach{indices->val fragments=indices.map{pdfFragments(project.cards[it],effective)};repeat(fragments.maxOf{it.size}){fragmentIndex->val row=Block(519f);var top=0f;if(chapter){top=row.text(t("拍摄脚本"),0f,8f,519f,16f)+16;chapter=false};indices.forEachIndexed{column,index->fragments[column].getOrNull(fragmentIndex)?.let{row.place(pdfShot(project.cards[index],index,it),column*273.5f,top)}};row.line(259.2f,top,.6f,max(1f,row.bottom-top));row.line(0f,row.bottom+7,519f,.5f);result+=row.build()}}}
        if(project.cards.isEmpty()&&options.organization==ExportOrganization.SEQUENCE){val b=Block(if(mobile)390f else 519f);b.text(t("拍摄脚本"),if(mobile)16f else 0f,8f,350f,if(mobile)20f else 16f);result+=b.build(8f)}
        return result
    }
    private fun slices(block: ExportBlock,limit: Float): List<ExportSlice> {
        val result=mutableListOf<ExportSlice>();var top=0f
        while(top<block.height){var end=min(block.height,top+limit)
            if(end<block.height){
                // Move breaks above a complete image or text block, never through a photo.
                val crossing=block.elements.filter{it.y<end&&it.y+it.height>end&&it.y>top+1}
                if(crossing.isNotEmpty())end=crossing.minOf{it.y}
            }
            if(end<=top+1)end=min(block.height,top+limit)
            result+=ExportSlice(block,top,end-top);top=end
        };return result
    }
    private fun draw(canvas: Canvas,slice: ExportSlice) {
        canvas.save();canvas.clipRect(0f,0f,slice.block.width,slice.height);canvas.translate(0f,-slice.top)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        slice.block.elements.filter{it.y+it.height>slice.top&&it.y<slice.top+slice.height}.forEach{e->
            paint.color=e.color;val rect=RectF(e.x,e.y,e.x+e.width,e.y+e.height)
            when(e.kind){
                ExportElementKind.TEXT->{canvas.save();canvas.translate(e.x,e.y);layout(e.text,e.size,e.width,e.color,e.tracking).draw(canvas);canvas.restore()}
                ExportElementKind.IMAGE->{val bitmap=e.imageId?.let(store::referenceImage)?.let(::decodeReferenceImage);if(bitmap!=null)try{canvas.drawBitmap(bitmap,null,rect,paint)}finally{bitmap.recycle()}}
                ExportElementKind.DISC->canvas.drawOval(rect,paint)
                ExportElementKind.SWATCH->canvas.drawCircle(e.x+e.width/2,e.y+e.height/2,e.width/2,paint)
                ExportElementKind.BADGE->canvas.drawRoundRect(rect,3f,3f,paint)
                ExportElementKind.RULE->canvas.drawRect(rect,paint)
            }
        };canvas.restore()
    }
    private fun jpeg(group: List<ExportSlice>,file: File,gap: Float=8f) {
        val h=group.sumOf{it.height.toDouble()}.toFloat()+max(0,group.size-1)*gap
        require(h*3<=32000&&1170.0*h*3<=48_000_000){"内容过长，请改用策划长图或 PDF"}
        val bitmap=Bitmap.createBitmap(1170,max(1,ceil(h*3).toInt()),Bitmap.Config.ARGB_8888)
        try{val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE);canvas.scale(3f,3f);var y=0f;group.forEach{canvas.save();canvas.translate(0f,y);draw(canvas,it);canvas.restore();y+=it.height+gap};file.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.JPEG,96,it)){"无法保存导出图片"}}}finally{bitmap.recycle()}
    }
    fun export(project: Project,options: ProjectExportOptions,kind: ProjectExportKind,directory: File): List<File> {
        directory.mkdirs();val name=safeExportName(project.name)
        try {
            return when(kind){
                ProjectExportKind.PROJECT->listOf(File(directory,"$name.shotegy").also{file->file.outputStream().use{IOSBackup.write(it,store,listOf(project),emptyList(),emptyList(),"project")}})
                ProjectExportKind.PDF -> {
                    val file=File(directory,t("{0}_策划案.pdf",name))
                    val pages=packExportSlices(layoutBlocks(project,options,false).flatMap{slices(it,772f)},772f,5f)
                    file.outputStream().use { output -> writeImagePdf(output,pages.size) { index ->
                        val bitmap=Bitmap.createBitmap(1190,1684,Bitmap.Config.ARGB_8888)
                        try {
                            val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE);canvas.scale(2f,2f);var y=27f
                            pages[index].forEach { part -> canvas.save();canvas.translate(38f,y);draw(canvas,part);canvas.restore();y+=part.height+5f }
                            val footer=if(options.showWatermark)t("SHOTEGY · 由拍序生成     {0}", index+1) else "${index+1}"
                            canvas.save();canvas.translate(38f,814f);layout(footer,8f,519f,Color.GRAY).draw(canvas);canvas.restore()
                            java.io.ByteArrayOutputStream().use { buffer -> check(bitmap.compress(Bitmap.CompressFormat.JPEG,96,buffer));buffer.toByteArray() }
                        } finally { bitmap.recycle() }
                    } }
                    listOf(file)
                }
                ProjectExportKind.LONG_IMAGE->{val groups=packExportSlices(layoutBlocks(project,options,true).flatMap{slices(it,4000f)},4000f,8f);groups.mapIndexed{i,group->File(directory,t("{0}_长图_{1}.jpg",name,"%02d".format(i+1))).also{jpeg(group,it)}}}
                ProjectExportKind.CARD_IMAGES->{require(project.cards.isNotEmpty()){ "尚未添加拍摄脚本" };val prep=packExportSlices(preparation(project,options,true).flatMap{slices(it,1200f)},4000f,8f);val files=prep.mapIndexed{i,group->File(directory,t("{0}_项目准备_{1}.jpg",name,"%02d".format(i+1))).also{jpeg(group,it)}}.toMutableList();project.cards.forEachIndexed{i,c->val block=mobileCard(project,c,i,options,false);val file=File(directory,t("{0}_脚本_{1}.jpg",name,"%02d".format(i+1)));jpeg(listOf(ExportSlice(block)),file);files+=file};files}
            }
        } catch(e:Exception){directory.listFiles()?.forEach{it.delete()};throw e}
    }
    private fun time(start: String,end: String)=listOf(start,end).filter{it.isNotBlank()}.joinToString(" – ")
    private fun formattedDate(value: String)=runCatching{java.time.LocalDate.parse(value).format(java.time.format.DateTimeFormatter.ofPattern(if(strings.language==AppLanguage.ENGLISH) "MMM d, yyyy" else "yyyy年M月d日", strings.language.locale))}.getOrDefault(value)
}
