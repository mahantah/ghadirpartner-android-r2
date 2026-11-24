package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/** Verify that both viewport types move only through their page controls. */
class PagedViewportTest {
    @get:Rule val compose = createComposeRule()

    /** A drag over list items must keep the first item in place. */
    @Test fun listIgnoresDragAndUsesPageButtons() {
        compose.setContent {
            GhadirTheme {
                ButtonPagedList(Modifier.height(240.dp)) {
                    items(20) { index -> Text("ردیف $index", Modifier.height(56.dp)) }
                }
            }
        }
        verifyPaging()
    }

    /** A drag over a form must keep its visible fields in place. */
    @Test fun columnIgnoresDragAndUsesPageButtons() {
        compose.setContent {
            GhadirTheme {
                ButtonPagedColumn(Modifier.height(240.dp)) {
                    repeat(20) { index -> Text("ردیف $index", Modifier.height(56.dp)) }
                }
            }
        }
        verifyPaging()
    }

    private fun verifyPaging() {
        compose.onNodeWithText("ردیف 0").assertIsDisplayed()
        compose.onRoot().performTouchInput {
            swipeUp(startY = bottom * .18f, endY = bottom * .03f)
        }
        compose.onNodeWithText("ردیف 0").assertIsDisplayed()
        compose.onNodeWithText("صفحهٔ قبل").assertIsNotEnabled()
        compose.onNodeWithText("صفحهٔ بعد").performClick()
        compose.onNodeWithText("ردیف 0").assertIsNotDisplayed()
        compose.onNodeWithText("صفحهٔ قبل").performClick()
        compose.onNodeWithText("ردیف 0").assertIsDisplayed()
        compose.revealOnPage(hasText("ردیف 19")).assertIsDisplayed()
    }
}
