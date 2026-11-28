package ir.ghadirpartner.nativeapp

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A serial and optional IMEI assigned to one model within the selected order. */
internal data class OrderSerialRow(val model: String, val serial: String, val imei: String)

/** Write a portable Excel workbook while keeping serials and IMEIs as text. */
internal object SerialWorkbook {
    const val mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** Write one right-to-left sheet to the provided download stream. */
    fun write(output: OutputStream, orderNumber: String, rows: List<OrderSerialRow>) {
        ZipOutputStream(output).use { zip ->
            fun entry(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>""".trimIndent())
            entry("_rels/.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>""".trimIndent())
            entry("xl/workbook.xml", """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <sheets><sheet name="سریال‌ها" sheetId="1" r:id="rId1"/></sheets></workbook>""".trimIndent())
            entry("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>""".trimIndent())
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val sheet = zip.writer(Charsets.UTF_8)
            sheet.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews>
                <cols><col min="1" max="1" width="19" customWidth="1"/><col min="2" max="4" width="24" customWidth="1"/></cols>
                <sheetData>""")
            fun row(number: Int, values: List<String>) {
                sheet.append("<row r=\"$number\">")
                values.forEachIndexed { column, value ->
                    val address = "${'A' + column}$number"
                    sheet.append("<c r=\"$address\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    value.forEach { character ->
                        when (character) {
                            '&' -> sheet.append("&amp;")
                            '<' -> sheet.append("&lt;")
                            '>' -> sheet.append("&gt;")
                            else -> if (character >= ' ' || character in "\t\n\r") sheet.append(character)
                        }
                    }
                    sheet.append("</t></is></c>")
                }
                sheet.append("</row>")
            }
            row(1, listOf("شماره سفارش", "مدل دستگاه", "سریال", "IMEI"))
            rows.forEachIndexed { index, value -> row(index + 2, listOf(orderNumber, value.model, value.serial, value.imei)) }
            sheet.append("</sheetData><autoFilter ref=\"A1:D${rows.size + 1}\"/></worksheet>")
            sheet.flush()
            zip.closeEntry()
        }
    }
}
