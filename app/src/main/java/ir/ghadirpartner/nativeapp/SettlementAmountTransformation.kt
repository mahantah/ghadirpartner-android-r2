package ir.ghadirpartner.nativeapp

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Keeps grouping and Persian digits in the rendered amount, outside the editable value. */
internal object SettlementAmountTransformation : VisualTransformation {
    /** Maps caret positions through the separators without changing the underlying amount. */
    override fun filter(text: AnnotatedString): TransformedText {
        val display = StringBuilder()
        val toDisplay = IntArray(text.length + 1)
        val toSource = mutableListOf(0)
        text.forEachIndexed { index, digit ->
            if (index > 0 && (text.length - index) % 3 == 0) {
                display.append('٬')
                toSource.add(index)
            }
            display.append("۰۱۲۳۴۵۶۷۸۹"[digit - '0'])
            toSource.add(index + 1)
            toDisplay[index + 1] = display.length
        }
        val offsets =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = toDisplay[offset]

                override fun transformedToOriginal(offset: Int): Int = toSource[offset]
            }
        return TransformedText(AnnotatedString(display.toString()), offsets)
    }
}
