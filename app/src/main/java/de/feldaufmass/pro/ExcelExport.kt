package de.feldaufmass.pro

import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

internal fun excelReport(sheets: List<Pair<String,List<List<Any?>>>>): ByteArray {
    fun esc(s: String) = s.filter { it >= ' ' || it == '\n' || it == '\t' || it == '\r' }.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    val pkg = "http://schemas.openxmlformats.org/package/2006/relationships"
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        fun entry(name: String, xml: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + xml).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" + sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } + "</Types>")
        entry("_rels/.rels", "<Relationships xmlns=\"$pkg\"><Relationship Id=\"rId1\" Type=\"$rel/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
        entry("xl/workbook.xml", "<workbook xmlns=\"$ns\" xmlns:r=\"$rel\"><sheets>" + sheets.mapIndexed { i,s -> "<sheet name=\"${esc(s.first)}\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>" }.joinToString("") + "</sheets></workbook>")
        entry("xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"$pkg\">" + sheets.indices.joinToString("") { "<Relationship Id=\"rId${it+1}\" Type=\"$rel/worksheet\" Target=\"worksheets/sheet${it+1}.xml\"/>" } + "</Relationships>")
        sheets.forEachIndexed { i,s ->
            val rows = s.second.mapIndexed { j,row ->
                "<row r=\"${j+1}\">" + row.mapIndexed { k,v ->
                    val ref = ('A'+k).toString() + (j+1)
                    when(v) {
                        null -> "<c r=\"$ref\"/>"
                        is Number -> "<c r=\"$ref\"><v>$v</v></c>"
                        else -> "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v.toString())}</t></is></c>"
                    }
                }.joinToString("") + "</row>"
            }.joinToString("")
            entry("xl/worksheets/sheet${i+1}.xml", "<worksheet xmlns=\"$ns\"><cols><col min=\"1\" max=\"1\" width=\"32\" customWidth=\"1\"/><col min=\"2\" max=\"12\" width=\"22\" customWidth=\"1\"/></cols><sheetData>$rows</sheetData></worksheet>")
        }
    }
    return out.toByteArray()
}
