package ir.ghadirpartner.nativeapp

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class SettlementAmountTransformationTest {
    @Test
    fun amountGroupingKeepsTheCaretAtTheSameDigit() {
        val formatted = SettlementAmountTransformation.filter(AnnotatedString("1234567"))

        assertEquals("۱٬۲۳۴٬۵۶۷", formatted.text.text)
        for (offset in 0..7) {
            val displayOffset = formatted.offsetMapping.originalToTransformed(offset)
            assertEquals(offset, formatted.offsetMapping.transformedToOriginal(displayOffset))
        }
        assertEquals(1, formatted.offsetMapping.transformedToOriginal(2))
        assertEquals(4, formatted.offsetMapping.transformedToOriginal(6))
    }
}
