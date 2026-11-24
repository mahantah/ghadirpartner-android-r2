package ir.ghadirpartner.nativeapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Find a control through the same page buttons a customer uses. */
internal fun ComposeTestRule.revealOnPage(target: SemanticsMatcher): SemanticsNodeInteraction {
    fun visibleTarget(): SemanticsNodeInteraction? {
        val dialogs = onAllNodes(isDialog()).fetchSemanticsNodes()
        val candidates = onAllNodes(if (dialogs.isEmpty()) target else target and hasAnyAncestor(isDialog()))
        return (candidates.fetchSemanticsNodes().indices.reversed()).asSequence()
            .map { candidates[it] }
            .firstOrNull { it.isDisplayed() }
    }
    visibleTarget()?.let { return it }
    returnToFirstPage()
    do {
        visibleTarget()?.let { return it }
    } while (turnTestPage("صفحهٔ بعد"))
    throw AssertionError("No visible control matching $target on any page")
}

/** Return the currently active list or dialog to its first page. */
internal fun ComposeTestRule.returnToFirstPage() {
    while (turnTestPage("صفحهٔ قبل")) Unit
}

private fun ComposeTestRule.turnTestPage(label: String): Boolean {
    val inDialog = onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()
    val byLabel = hasText(label)
    val buttons = onAllNodes(if (inDialog) byLabel and hasAnyAncestor(isDialog()) else byLabel)
    if (buttons.fetchSemanticsNodes().isEmpty()) return false
    val last = buttons.onLast()
    if (!last.isDisplayed()) return false
    if (!last.fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)) {
        last.performClick()
        waitForIdle()
        return true
    }
    return false
}
