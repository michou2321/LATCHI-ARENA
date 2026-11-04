package dz.latchi.arena

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Latchi Arena — غلاف أصلي لموقع arena.ai
 * كل ميزة فـالموقع (المحادثة · اختيار الموديل · التصويت · السجل · التوليد)
 * تخدم داخل التطبيق بلا متصفح.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val START = "https://arena.ai/"
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        /** النطاقات اللي تبقى داخل التطبيق (الموقع + تسجيل الدخول) */
        private val INTERNAL = listOf(
            "arena.ai",
            "google.com", "googleapis.com", "gstatic.com", "googleusercontent.com",
            "recaptcha.net", "apple.com", "appleid.apple.com", "github.com",
            "cloudflare.com", "hcaptcha.com", "twitter.com", "x.com",
            "facebook.com", "microsoftonline.com", "live.com"
        )
    }

    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var errorBox: View

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var backAt = 0L
    private var desktopMode = false
    private var loadError = false

    private val picker: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val cb = fileCallback
            fileCallback = null
            if (cb == null) return@registerForActivityResult
            var out: Array<Uri>? = null
            val d = res.data
            if (res.resultCode == RESULT_OK && d != null) {
                val clip = d.clipData
                out = if (clip != null && clip.count > 0) {
                    Array(clip.count) { clip.getItemAt(it).uri }
                } else {
                    d.data?.let { arrayOf(it) }
                }
            }
            cb.onReceiveValue(out)
        }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_main)

        swipe = findViewById(R.id.swipe)
        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        errorBox = findViewById(R.id.errorBox)

        swipe.setOnRefreshListener { loadError = false; web.reload() }
        swipe.setColorSchemeColors(0xFF7C4DFF.toInt(), 0xFF22D3EE.toInt())
        swipe.setProgressBackgroundColorSchemeColor(0xFF14141F.toInt())

        findViewById<View>(R.id.retry).setOnClickListener {
            errorBox.visibility = View.GONE
            loadError = false
            web.loadUrl(START)
        }
        findViewById<View>(R.id.more).setOnClickListener { showMenu() }

        setupWeb()

        if (saved != null) web.restoreState(saved) else web.loadUrl(START)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportMultipleWindows(true)
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.userAgentString = MOBILE_UA

        // كوكيز + تخزين دائم باش الحساب يبقى مسجّل
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        web.setBackgroundColor(0xFF0A0A12.toInt())
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.isScrollbarFadingEnabled = true

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, req: WebResourceRequest
            ): Boolean = handleUrl(req.url)

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                handleUrl(Uri.parse(url))

            override fun onPageFinished(view: WebView, url: String) {
                swipe.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView, req: WebResourceRequest, err: WebResourceError
            ) {
                if (req.isForMainFrame) {
                    loadError = true
                    swipe.isRefreshing = false
                    errorBox.visibility = View.VISIBLE
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                if (newProgress in 1..99 && !loadError) {
                    progress.visibility = View.VISIBLE
                } else {
                    progress.visibility = View.GONE
                }
            }

            override fun onShowFileChooser(
                view: WebView?,
                cb: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = cb
                val intent = params?.createIntent()
                if (intent == null) {
                    fileCallback = null
                    return false
                }
                return try {
                    picker.launch(intent)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }

            // نوافذ target=_blank تتحلّ فنفس التطبيق
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val v = view ?: return false
                val href = v.handler.obtainMessage()
                v.requestFocusNodeHref(href)
                val url = href.data?.getString("url")
                if (url.isNullOrBlank()) return false
                web.loadUrl(url)
                return true
            }
        }

        web.setDownloadListener { url, ua, disposition, mime, _ ->
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                Toast.makeText(this, "هذا النوع من التحميل ما مدعومش", Toast.LENGTH_SHORT).show()
                return@setDownloadListener
            }
            try {
                val name = URLUtil.guessFileName(url, disposition, mime)
                val req = DownloadManager.Request(Uri.parse(url))
                req.setMimeType(mime)
                req.addRequestHeader("User-Agent", ua)
                CookieManager.getInstance().getCookie(url)?.let {
                    req.addRequestHeader("Cookie", it)
                }
                req.setTitle(name)
                req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this, "يتحمّل: $name", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "فشل التحميل", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleUrl(u: Uri): Boolean {
        val scheme = u.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https") {
            val host = u.host?.lowercase() ?: return false
            if (INTERNAL.any { host == it || host.endsWith(".$it") }) return false
            return openExternal(u)
        }
        // intent:// · tel: · mailto: · whatsapp:
        return try {
            startActivity(Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openExternal(u: Uri): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, u))
        true
    } catch (e: Exception) {
        Toast.makeText(this, "ما لقيتش تطبيق يفتح هذا الرابط", Toast.LENGTH_SHORT).show()
        true
    }

    private fun showMenu() {
        val items = arrayOf(
            "🔄  إعادة تحميل",
            "🌐  فتح فـالمتصفح",
            if (desktopMode) "📱  نسخة الهاتف" else "🖥️  نسخة سطح المكتب",
            "🧹  مسح الكاش",
            "ℹ️  عن التطبيق"
        )
        AlertDialog.Builder(this)
            .setTitle("Latchi Arena")
            .setItems(items) { _, i ->
                when (i) {
                    0 -> web.reload()
                    1 -> openExternal(Uri.parse(web.url ?: START))
                    2 -> {
                        desktopMode = !desktopMode
                        web.settings.userAgentString = if (desktopMode) DESKTOP_UA else MOBILE_UA
                        web.reload()
                    }
                    3 -> {
                        web.clearCache(true)
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        Toast.makeText(this, "تنحى الكاش والكوكيز", Toast.LENGTH_SHORT).show()
                        web.loadUrl(START)
                    }
                    else -> about()
                }
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun about() {
        AlertDialog.Builder(this)
            .setTitle("Latchi Arena 1.0.0")
            .setMessage(
                "غلاف أصلي لموقع arena.ai — كل المزايا تخدم داخل التطبيق:\n\n" +
                    "• المحادثة مع أي موديل\n" +
                    "• اختيار الموديل والمقارنة\n" +
                    "• التصويت والسجل\n" +
                    "• تحميل الملفات · رفع الصور\n\n" +
                    "صُنع بواسطة LatchiDrop · dz.latchi.arena"
            )
            .setPositiveButton("تمام", null)
            .show()
    }

    override fun onBackPressed() {
        if (errorBox.visibility == View.VISIBLE) {
            errorBox.visibility = View.GONE
            return
        }
        if (web.canGoBack()) {
            web.goBack()
            return
        }
        val now = System.currentTimeMillis()
        if (now - backAt < 2200L) {
            super.onBackPressed()
            return
        }
        backAt = now
        Toast.makeText(this, "اضغط رجوع مرة أخرى للخروج", Toast.LENGTH_SHORT).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
