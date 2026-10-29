package ir.ghadirpartner.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Physical delivery limits do not depend on whether all serials are already ready. */
class PartialDeliveryPolicyTest {
    private val policy = PartialDeliveryPolicy(999)
    private val lines = listOf(PartialDeliveryLine("I90", 6, 0, 6), PartialDeliveryLine("I80", 3, 0, 3))

    /** One third may leave while the remaining order stays open. */
    @Test fun oneThirdOfFullyReadyOrderIsValid() {
        policy.validate(lines, listOf(PartialDeliveryQuantity("I90", 2), PartialDeliveryQuantity("I80", 1)))
        assertEquals(9, lines.sumOf { it.remaining })
    }

    /** Whole-order delivery cannot use the partial financial exception. */
    @Test fun wholeRemainderAndDuplicateModelsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            policy.validate(lines, listOf(PartialDeliveryQuantity("I90", 6), PartialDeliveryQuantity("I80", 3)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy.validate(lines, listOf(PartialDeliveryQuantity("I90", 1), PartialDeliveryQuantity("I90", 1)))
        }
    }

    /** Already shipped, not-yet-ready, negative and unknown quantities cannot be released. */
    @Test fun quantitiesAreBoundedByReadyUnshippedUnits() {
        val partial = listOf(PartialDeliveryLine("I90", 6, 3, 1))
        policy.validate(partial, listOf(PartialDeliveryQuantity("I90", 1)))
        for (quantity in listOf(-1, 0, 2, 4, 1000)) {
            assertThrows(IllegalArgumentException::class.java) {
                policy.validate(partial, listOf(PartialDeliveryQuantity("I90", quantity)))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy.validate(partial, listOf(PartialDeliveryQuantity("UNKNOWN", 1)))
        }
    }
}
