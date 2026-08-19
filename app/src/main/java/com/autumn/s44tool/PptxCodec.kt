package com.autumn.s44tool

import android.content.Context
import androidx.annotation.DrawableRes
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

object PptxCodec {
    private const val SLIDE_W = 12192000L
    private const val SLIDE_H = 6858000L

    fun export(context: Context, c: AppController): ByteArray {
        val verified = c.isPptVerified()
        val exact = c.exactResult
        val peak = c.peakResult
        val matches = c.matchResult
        val avatar = context.resources.openRawResource(R.drawable.author_avatar).use { it.readBytes() }

        val slide1 = mutableListOf<Shape>()
        slide1 += title("S44 英雄战力工具", "作者 秋天  ·  原生 Android 计算报告")
        slide1 += metric("目标英雄战力", c.targetPower, "E34858", 0.65,1.55,3.0)
        slide1 += metric("当前表现分", c.performance, "8157D8", 3.55,1.55,3.0)
        slide1 += metric("表现分上限", c.performanceCap, "8157D8", 6.45,1.55,3.0)
        slide1 += metric("巅峰系数", c.coefficientInput, "D89A00", 9.35,1.55,3.0)
        slide1 += metric("公式战力", if (exact.valid) exact.power.roundToInt().toString() else "—", "E34858", .65,3.0,3.8)
        slide1 += metric("目标所需表现分", if (exact.valid) exact.needPerf.roundToInt().toString() else "—", "8157D8", 4.55,3.0,3.8)
        slide1 += metric("表现分打满后战力", if (exact.valid) exact.capPower.roundToInt().toString() else "—", "E34858", 8.45,3.0,3.8)
        slide1 += paragraph("校验  ${exact.selfCheck}  ${exact.selfCheckSub}", .7,4.5,11.7,.65, "6F6276", 12, false)
        slide1 += paragraph(exact.decision, .7,5.25,11.7,.8, "6F6276", 11, false)
        slide1 += field("targetPower", c.targetPower)
        slide1 += field("gamePower", c.gamePower)
        slide1 += field("performance", c.performance)
        slide1 += field("performanceCap", c.performanceCap)

        val slide2 = mutableListOf<Shape>()
        slide2 += title("巅峰反推与 45 场", "蓝色为巅峰分  紫色为表现分与星数")
        slide2 += metric("当前巅峰分", c.peakNow, "3478E5", .65,1.55,3.0)
        slide2 += metric("英雄已解锁巅峰分", c.peakUnlocked, "3478E5", 3.55,1.55,3.0)
        slide2 += metric("所需巅峰系数", "%.2f%%".format(Locale.US, peak.needCoeffPct), "D89A00", 6.45,1.55,3.0)
        slide2 += metric("反推巅峰分", peak.needPeakScore?.toString() ?: "—", "3478E5", 9.35,1.55,3.0)
        slide2 += metric("王者星数", c.rankStars, "8157D8", .65,3.0,3.0)
        slide2 += metric("45 场累计", matches.performance.roundToInt().toString(), "8157D8", 3.55,3.0,3.0)
        slide2 += metric("输入场数", matches.values.size.toString(), "3A9DA6", 6.45,3.0,3.0)
        slide2 += metric("周回收场数", matches.recycleGames.toString(), "3A9DA6", 9.35,3.0,3.0)
        slide2 += paragraph("巅峰反推精度  ${peak.precision}", .7,4.5,11.7,.55,"6F6276",12,false)
        slide2 += paragraph("45 场直接输入游戏结算后的单局表现分可按明细计算  评分×倍率属于辅助估算", .7,5.15,11.7,.75,"6F6276",11,false)
        slide2 += field("rankStars", c.rankStars)
        slide2 += field("peakNow", c.peakNow)
        slide2 += field("peakUnlocked", c.peakUnlocked)
        slide2 += field("coefficientInput", c.coefficientInput)
        slide2 += field("rankTier", c.rankTier)

        val slide3 = mutableListOf<Shape>()
        slide3 += title("规则与说明", "所有公式结果最终以游戏内显示为准")
        slide3 += paragraph("S40 英雄战力  表现分 × 巅峰系数", .75,1.6,11.5,.55,"392F43",18,true)
        slide3 += paragraph("表现分采用赛季内高分记录累计  最多 45 场  达到表现分上限后战力按上限计入", .75,2.3,11.5,.7,"6F6276",12,false)
        slide3 += paragraph("巅峰系数映射并非完整公开表  导入游戏内实测映射时可按表反推  参考曲线只标估算", .75,3.15,11.5,.7,"6F6276",12,false)
        slide3 += paragraph("S41+ 英雄巅峰系数解锁受每 100 分阶段净胜场影响  当前工具不会伪造未公开完整映射", .75,4.0,11.5,.7,"6F6276",12,false)
        slide3 += paragraph("作者  秋天", .75,5.15,4.0,.5,"C89B25",16,true)

        val slides = listOf(slide1, slide2, slide3).map { shapes ->
            if (verified) shapes else shapes + watermarkShapes()
        }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name:String, bytes:ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            fun putText(name:String, text:String) = put(name, text.toByteArray(Charsets.UTF_8))

            putText("[Content_Types].xml", contentTypes())
            putText("_rels/.rels", rootRels())
            putText("docProps/core.xml", coreProps())
            putText("docProps/app.xml", appProps())
            putText("ppt/presentation.xml", presentation())
            putText("ppt/_rels/presentation.xml.rels", presentationRels())
            putText("ppt/theme/theme1.xml", theme())
            putText("ppt/slideMasters/slideMaster1.xml", slideMaster())
            putText("ppt/slideMasters/_rels/slideMaster1.xml.rels", slideMasterRels())
            putText("ppt/slideLayouts/slideLayout1.xml", slideLayout())
            putText("ppt/slideLayouts/_rels/slideLayout1.xml.rels", slideLayoutRels())
            slides.forEachIndexed { idx, shapes ->
                putText("ppt/slides/slide${idx+1}.xml", slideXml(shapes, idx+1))
                putText("ppt/slides/_rels/slide${idx+1}.xml.rels", slideRels())
            }
            put("ppt/media/image1.jpg", avatar)
        }
        return out.toByteArray()
    }

    fun importFields(bytes: ByteArray): Map<String,String> {
        val fields = linkedMapOf<String,String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                if (Regex("""ppt/slides/slide\d+\.xml""").matches(e.name)) {
                    val xml = zin.readBytes().toString(Charsets.UTF_8)
                    parseFields(xml, fields)
                }
            }
        }
        if (fields.isEmpty()) error("PPTX 中没有 S44FIELD 字段")
        return fields
    }

    private fun parseFields(xml:String, out:MutableMap<String,String>) {
        val shapeRegex=Regex("""<p:sp[\s\S]*?</p:sp>""")
        var waiting:String?=null
        shapeRegex.findAll(xml).forEach { block ->
            val texts=Regex("""<a:t>([\s\S]*?)</a:t>""").findAll(block.value)
                .map { xmlUnescape(it.groupValues[1]) }.toList()
            val joined=texts.joinToString("")
            if(joined.startsWith("S44FIELD:")){
                waiting=joined.removePrefix("S44FIELD:").trim()
            }else if(waiting!=null){
                out[waiting!!]=joined.trim()
                waiting=null
            }
        }
    }

    private fun title(t:String, sub:String): List<Shape> = listOf(
        paragraph(t,.55,.35,9.7,.5,"392F43",24,true),
        paragraph(sub,.58,.92,10.5,.35,"83758E",9,false)
    )

    private fun metric(label:String,value:String,color:String,x:Double,y:Double,w:Double): List<Shape> =
        listOf(
            paragraph(label,x,y,w,.32,"83758E",10,false),
            paragraph(value,x,y+.36,w,.56,color,22,true)
        )

    private fun field(key:String,value:String): List<Shape> = listOf(
        paragraph("S44FIELD:$key",12.95,7.2,.25,.08,"FFF6FA",1,false),
        paragraph(value.ifBlank { "0" },12.95,7.3,.25,.08,"FFF6FA",1,false)
    )

    private fun watermarkShapes(): List<Shape> {
        val out=mutableListOf<Shape>()
        val xs=listOf(.35,2.8,5.25,7.7,10.15)
        val ys=listOf(1.25,2.55,3.85,5.15,6.3)
        ys.forEachIndexed { row,y ->
            xs.forEachIndexed { col,x ->
                out += paragraph("秋天",x+(if(row%2==1) 1.0 else 0.0),y,1.9,.4,"B8B4B7",17,true,330)
            }
        }
        out += paragraph("秋天  未验证水印版  去水印请联系作者",3.7,3.35,6.0,.5,"9F999D",14,true,330)
        return out
    }

    private fun paragraph(
        text:String,x:Double,y:Double,w:Double,h:Double,color:String,size:Int,bold:Boolean,rotate:Int=0
    ): Shape = Shape(text,x,y,w,h,color,size,bold,rotate)

    private data class Shape(
        val text:String,val x:Double,val y:Double,val w:Double,val h:Double,
        val color:String,val size:Int,val bold:Boolean,val rotate:Int
    )

    private fun slideXml(shapes:List<Shape>, slideNo:Int):String {
        var id=2
        val sb=StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
 <p:cSld name="S44-$slideNo"><p:spTree>
  <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
  <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
""")
        shapes.forEach { s -> sb.append(shapeXml(id++,s)) }
        sb.append(picXml(id++))
        sb.append("""</p:spTree></p:cSld>
 <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sld>""")
        return sb.toString()
    }

    private fun shapeXml(id:Int,s:Shape):String {
        fun emu(v:Double)= (v*914400.0).toLong()
        val rot = if(s.rotate==0) "" else " rot=\"${s.rotate*60000}\""
        return """
<p:sp>
 <p:nvSpPr><p:cNvPr id="$id" name="Text $id"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr>
 <p:spPr><a:xfrm$rot><a:off x="${emu(s.x)}" y="${emu(s.y)}"/><a:ext cx="${emu(s.w)}" cy="${emu(s.h)}"/></a:xfrm>
  <a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/><a:ln><a:noFill/></a:ln>
 </p:spPr>
 <p:txBody><a:bodyPr wrap="square" anchor="ctr"/><a:lstStyle/>
  <a:p><a:r><a:rPr lang="zh-CN" sz="${s.size*100}" b="${if(s.bold) 1 else 0}">
   <a:solidFill><a:srgbClr val="${s.color}"/></a:solidFill>
  </a:rPr><a:t>${xmlEscape(s.text)}</a:t></a:r><a:endParaRPr lang="zh-CN"/></a:p>
 </p:txBody>
</p:sp>
""".trimIndent()
    }

    private fun picXml(id:Int):String = """
<p:pic>
 <p:nvPicPr><p:cNvPr id="$id" name="作者头像"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
 <p:blipFill><a:blip r:embed="rId2"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
 <p:spPr><a:xfrm><a:off x="11064240" y="274320"/><a:ext cx="731520" cy="731520"/></a:xfrm>
 <a:prstGeom prst="ellipse"><a:avLst/></a:prstGeom></p:spPr>
</p:pic>
""".trimIndent()

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
 <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
 <Default Extension="xml" ContentType="application/xml"/>
 <Default Extension="jpg" ContentType="image/jpeg"/>
 <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
 <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
 <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
 <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
 <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
 <Override PartName="/ppt/slides/slide2.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
 <Override PartName="/ppt/slides/slide3.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
 <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
 <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
 <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
 <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
 <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""

    private fun presentation() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
 <p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>
 <p:sldIdLst>
  <p:sldId id="256" r:id="rId2"/><p:sldId id="257" r:id="rId3"/><p:sldId id="258" r:id="rId4"/>
 </p:sldIdLst>
 <p:sldSz cx="$SLIDE_W" cy="$SLIDE_H" type="screen16x9"/>
 <p:notesSz cx="6858000" cy="9144000"/>
</p:presentation>"""

    private fun presentationRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
 <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
 <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
 <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/>
 <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide3.xml"/>
</Relationships>"""

    private fun slideMaster() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
 <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
 <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
 </p:spTree></p:cSld>
 <p:clrMap accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6"
 bg1="lt1" bg2="lt2" folHlink="folHlink" hlink="hlink" tx1="dk1" tx2="dk2"/>
 <p:sldLayoutIdLst><p:sldLayoutId id="1" r:id="rId1"/></p:sldLayoutIdLst>
 <p:txStyles><p:titleStyle/><p:bodyStyle/><p:otherStyle/></p:txStyles>
</p:sldMaster>"""

    private fun slideMasterRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
 <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
 <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
</Relationships>"""

    private fun slideLayout() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1">
 <p:cSld name="Blank"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
 <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
 </p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sldLayout>"""

    private fun slideLayoutRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
 <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>"""

    private fun slideRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
 <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
 <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image1.jpg"/>
</Relationships>"""

    private fun theme() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="S44">
 <a:themeElements>
  <a:clrScheme name="S44"><a:dk1><a:srgbClr val="392F43"/></a:dk1><a:lt1><a:srgbClr val="FFF6FA"/></a:lt1>
   <a:dk2><a:srgbClr val="392F43"/></a:dk2><a:lt2><a:srgbClr val="FFF6FA"/></a:lt2>
   <a:accent1><a:srgbClr val="E865A2"/></a:accent1><a:accent2><a:srgbClr val="8565E7"/></a:accent2>
   <a:accent3><a:srgbClr val="3478E5"/></a:accent3><a:accent4><a:srgbClr val="D89A00"/></a:accent4>
   <a:accent5><a:srgbClr val="8157D8"/></a:accent5><a:accent6><a:srgbClr val="3A9DA6"/></a:accent6>
   <a:hlink><a:srgbClr val="3478E5"/></a:hlink><a:folHlink><a:srgbClr val="8157D8"/></a:folHlink>
  </a:clrScheme>
  <a:fontScheme name="S44"><a:majorFont><a:latin typeface="Microsoft YaHei"/><a:ea typeface="Microsoft YaHei"/><a:cs typeface="Arial"/></a:majorFont>
   <a:minorFont><a:latin typeface="Microsoft YaHei"/><a:ea typeface="Microsoft YaHei"/><a:cs typeface="Arial"/></a:minorFont></a:fontScheme>
  <a:fmtScheme name="S44"><a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst>
   <a:lnStyleLst><a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst>
   <a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>
   <a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst>
  </a:fmtScheme>
 </a:themeElements><a:objectDefaults/><a:extraClrSchemeLst/>
</a:theme>"""

    private fun coreProps() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
 xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/"
 xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
 <dc:title>S44英雄战力工具</dc:title><dc:creator>秋天</dc:creator><cp:lastModifiedBy>秋天</cp:lastModifiedBy>
</cp:coreProperties>"""

    private fun appProps() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
 xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
 <Application>S44 Native Android</Application><Slides>3</Slides><Company>秋天</Company>
</Properties>"""

    private fun xmlEscape(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private fun xmlUnescape(s:String)=s.replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&amp;","&")
}
