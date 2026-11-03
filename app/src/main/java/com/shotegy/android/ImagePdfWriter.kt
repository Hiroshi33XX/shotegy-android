// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import java.io.OutputStream
import java.nio.charset.StandardCharsets

/** A4 PDF with the same 2× rasterized editorial pages as the iOS ImageRenderer export. */
internal fun writeImagePdf(output: OutputStream, pageCount: Int, jpegPage: (Int) -> ByteArray) {
    require(pageCount > 0)
    var position=0L
    val offsets=LongArray(3+pageCount*3)
    fun bytes(data:ByteArray){output.write(data);position+=data.size}
    fun text(value:String)=bytes(value.toByteArray(StandardCharsets.ISO_8859_1))
    fun obj(number:Int,body:()->Unit){offsets[number]=position;text("$number 0 obj\n");body();text("\nendobj\n")}
    text("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n")
    obj(1){text("<< /Type /Catalog /Pages 2 0 R >>")}
    obj(2){text("<< /Type /Pages /Count $pageCount /Kids [");repeat(pageCount){text("${3+it*3} 0 R ")};text("] >>")}
    repeat(pageCount){index->
        val page=3+index*3;val content=page+1;val image=page+2
        obj(page){text("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /XObject << /Im0 $image 0 R >> >> /Contents $content 0 R >>")}
        val commands="q\n595 0 0 842 0 0 cm\n/Im0 Do\nQ\n".toByteArray(StandardCharsets.US_ASCII)
        obj(content){text("<< /Length ${commands.size} >>\nstream\n");bytes(commands);text("endstream")}
        val data=jpegPage(index)
        obj(image){text("<< /Type /XObject /Subtype /Image /Width 1190 /Height 1684 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${data.size} >>\nstream\n");bytes(data);text("\nendstream")}
    }
    val start=position;text("xref\n0 ${offsets.size}\n0000000000 65535 f \n")
    (1 until offsets.size).forEach{text("%010d 00000 n \n".format(java.util.Locale.US,offsets[it]))}
    text("trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$start\n%%EOF\n");output.flush()
}
