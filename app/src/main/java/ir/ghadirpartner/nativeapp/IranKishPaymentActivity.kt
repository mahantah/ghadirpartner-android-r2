package ir.ghadirpartner.nativeapp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/** Open the bank directly and close the payment screen once the merchant receives the return. */
class IranKishPaymentActivity : Activity() {
    private var bankView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launch = intent.data
        if (launch == null || !IranKishNavigation.isLaunch(launch)) {
            finish()
            return
        }
        val web = WebView(this)
        bankView = web
        setContentView(web)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            web.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        // The launch form and bank page need JavaScript. Keep local files and native APIs outside the page.
        web.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !IranKishNavigation.allows(request.url)

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (IranKishNavigation.allows(request.url)) return null
                return WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(byteArrayOf()))
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                val uri = Uri.parse(url)
                if (!IranKishNavigation.allows(uri)) {
                    view.stopLoading()
                    finish()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (IranKishNavigation.isReturn(Uri.parse(url))) {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
        // Reloading a launch form after rotation could resubmit the same bank token.
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            if (savedInstanceState == null) web.loadUrl(launch.toString()) else finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        bankView?.saveState(outState)
    }

    override fun onDestroy() {
        bankView?.let { web ->
            (web.parent as? android.view.ViewGroup)?.removeView(web)
            web.destroy()
        }
        bankView = null
        super.onDestroy()
    }

    companion object {
        /** Start only the internal, non-exported gateway activity. */
        internal fun intent(context: Context, uri: Uri): Intent =
            Intent(context, IranKishPaymentActivity::class.java).setData(uri)
    }
}

/** Restrict payment navigation to the exact bank and merchant payment endpoints. */
internal object IranKishNavigation {
    private const val merchant = "ghadirpartner.ir"
    private const val bank = "ikc.shaparak.ir"
    private const val launch = "/partners/api/orders/irankish/launch"
    private const val result = "/partners/api/orders/irankish/result"
    private const val callback = "/partners/api/wallet/irankish/callback"

    /** Match the opaque server-issued launch endpoint. */
    fun isLaunch(uri: Uri): Boolean =
        secureHost(uri, merchant) && uri.path == launch && uri.getQueryParameter("id")?.matches(Regex("[a-f0-9]{20}")) == true

    /** Recognize the completed return and the merchant fallback for a gateway with no request ID. */
    fun isReturn(uri: Uri): Boolean =
        secureHost(uri, merchant) && (uri.path == result || (uri.path == "/partners/" && uri.getQueryParameter("wallet") == "return"))

    /** Permit bank resources, the initial handoff and the merchant return. */
    fun allows(uri: Uri): Boolean =
        secureHost(uri, bank) || isLaunch(uri) || isReturn(uri) || (secureHost(uri, merchant) && uri.path == callback)

    private fun secureHost(uri: Uri, host: String): Boolean =
        uri.isHierarchical && uri.scheme == "https" && uri.host == host && uri.userInfo == null && uri.port in listOf(-1, 443)
}
