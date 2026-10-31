package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercise readable RTL cards and their identity across a quantity-price transition. */
class OrderLineCardTest {
    @get:Rule val compose = createComposeRule()

    /** A long model remains visible on a narrow large-font screen after increasing quantity. */
    @Test fun narrowLargeFontCartKeepsModelAndPurchaseOptionTogether() {
        val model = "دستگاه کارتخوان سیار I90 4G WiFi PCI 6 با مشخصات کامل"
        val service = OrderCartService(OrderPolicy(1000, 50))
        val product = OrderCatalogEntry(model, 1000, mapOf("serial_1_50" to 12000000L, "serial_51_200" to 11500000L))
        var lines by mutableStateOf(service.add(emptyList(), product, 50, "serial_1_50"))
        val identity = lines.single().stableKey
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density.density, 1.5f),
            ) {
                GhadirTheme {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        lines.forEach { line ->
                            key(line.stableKey) {
                                OrderLineCard(
                                    line,
                                    onIncrease = { lines = service.changeQuantity(lines, line, product, line.qty + 1) },
                                    onDecrease = { lines = service.changeQuantity(lines, line, product, line.qty - 1) },
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText(model).assertIsDisplayed()
        compose.onNodeWithContentDescription("افزایش تعداد " + model).performScrollTo().performClick()
        compose.onNodeWithText(model).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("سریال آزاد — ۵۱ تا ۲۰۰").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("مبلغ ردیف").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(identity, lines.single().stableKey)
            assertEquals(51, lines.single().qty)
            assertEquals(11500000L, lines.single().unit)
        }
    }
}
