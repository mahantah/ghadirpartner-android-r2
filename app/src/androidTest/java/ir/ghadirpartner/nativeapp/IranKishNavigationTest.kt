package ir.ghadirpartner.nativeapp

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IranKishNavigationTest {
    /** The app closes only on its merchant return and rejects lookalike destinations. */
    @Test
    fun paymentNavigationStaysOnTrustedEndpoints() {
        val merchant = "https://ghadirpartner.ir/partners"
        assertTrue(IranKishNavigation.isLaunch(Uri.parse("$merchant/api/orders/irankish/launch?id=0123456789abcdef0123")))
        assertTrue(IranKishNavigation.isReturn(Uri.parse("$merchant/api/orders/irankish/result")))
        assertTrue(IranKishNavigation.isReturn(Uri.parse("$merchant/?wallet=return#gpIranKishWallet")))
        assertTrue(IranKishNavigation.allows(Uri.parse("$merchant/api/wallet/irankish/callback")))
        assertTrue(IranKishNavigation.allows(Uri.parse("https://ikc.shaparak.ir/iuiv3/IPG/Index/")))
        assertFalse(IranKishNavigation.allows(Uri.parse("https://ikc.shaparak.ir.example.org/")))
        assertFalse(IranKishNavigation.allows(Uri.parse("http://ikc.shaparak.ir/")))
        assertFalse(IranKishNavigation.allows(Uri.parse("https://ghadirpartner.ir@evil.example/")))
        assertFalse(IranKishNavigation.isReturn(Uri.parse("$merchant/")))
    }
}
