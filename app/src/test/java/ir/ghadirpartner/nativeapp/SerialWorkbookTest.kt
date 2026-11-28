package ir.ghadirpartner.nativeapp

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SerialWorkbookTest {
    @Test
    fun serialsAndImeisRemainTextInARightToLeftWorkbook() {
        val bytes = ByteArrayOutputStream()
        SerialWorkbook.write(bytes, "GHP-42", listOf(OrderSerialRow("مدل <A>", "00015", "=123456789012345")))
        val parts = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes()
            }
        }
        assertEquals(5, parts.size)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(parts.getValue("xl/worksheets/sheet1.xml")))
        assertEquals("1", document.getElementsByTagName("sheetView").item(0).attributes.getNamedItem("rightToLeft").nodeValue)
        val cells = document.getElementsByTagName("c")
        assertEquals("مدل <A>", cells.item(5).textContent)
        assertEquals("00015", cells.item(6).textContent)
        assertEquals("inlineStr", cells.item(7).attributes.getNamedItem("t").nodeValue)
        assertFalse(document.getElementsByTagName("f").length > 0)
    }
}
