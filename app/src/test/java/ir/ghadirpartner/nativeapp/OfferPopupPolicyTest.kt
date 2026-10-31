package ir.ghadirpartner.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Exercise display history independently of Android storage or live offers. */
class OfferPopupPolicyTest {
    private val policy = OfferPopupPolicy()
    /** Prefer the newest server-ordered eligible offer. */
    @Test fun selectsFirstEligibleUnseenOffer() {
        assertEquals(3, policy.next(listOf(OfferPopupEntry(3,true,false),OfferPopupEntry(2,true,false)), emptySet()))
    }
    /** Dismissed, inactive, used and malformed entries cannot interrupt the user. */
    @Test fun skipsAcknowledgedAndIneligibleOffers() {
        val offers=listOf(OfferPopupEntry(0,true,false),OfferPopupEntry(1,false,false),OfferPopupEntry(2,true,true),OfferPopupEntry(3,true,false),OfferPopupEntry(4,true,false))
        assertEquals(4,policy.next(offers,setOf("3")))
    }
    /** No eligible offers means no modal. */
    @Test fun emptyOrAllSeenReturnsNothing() {
        assertNull(policy.next(emptyList(),emptySet()))
        assertNull(policy.next(listOf(OfferPopupEntry(3,true,false)),setOf("3")))
    }
}
