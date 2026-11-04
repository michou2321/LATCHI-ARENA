package dz.latchi.arena

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * الشاشة الرئيسية — arena.ai داخل التطبيق مع شريط أدوات واضح.
 */
class MainActivity : BaseActivity() {

    companion object {
        private const val START = "https://arena.ai/"
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

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
    private lateinit var topBar: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var backBtn: ImageButton
    private lateinit var fwdBtn: ImageButton

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
                out = if (clip != null && clip.itemCount > 0) {
                    Array(clip.itemCount) { clip.getItemAt(it).uri }
                } else {
                    d.data?.let { arrayOf(it) }
                }
            }
            cb.onReceiveValue(out)
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_main)

        swipe = findViewById(R.id.swipe)
        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        errorBox = findViewById(R.id.errorBox)
        topBar = findViewById(R.id.topBar)
        titleText = findViewById(R.id.titleText)
        backBtn = findViewById(R.id.btnBack)
        fwdBtn = findViewById(R.id.btnFwd)

        getSharedPreferences("latchi_arena", MODE_PRIVATE)
            .edit().putBoolean("logged_in", true).apply()

        swipe.setColorSchemeColors(0xFF7C4DFF.toInt(), 0xFF22D3EE.toInt())
        swipe.setProgressBackgroundColorSchemeColor(0xFF14141F.toInt())
        swipe.setOnRefreshListener { loadError = false; web.reload() }

        findViewById<View>(R.id.retry).setOnClickListener {
            errorBox.visibility = View.GONE
            loadError = false
            web.loadUrl(START)
        }

        backBtn.setOnClickListener { if (web.canGoBack()) web.goBack() else goLogin() }
        fwdBtn.setOnClickListener { if (web.canGoForward()) web.goForward() }
        findViewById<View>(R.id.btnHome).setOnClickListener { web.loadUrl(START) }
        findViewById<View>(R.id.btnReload).setOnClickListener { web.reload() }
        findViewById<View>(R.id.btnLang).setOnClickListener { toggleLang() }
        findViewById<View>(R.id.btnMenu).setOnClickListener { showMenu() }

        setupWeb()
        applyChrome()

        if (saved != null) web.restoreState(saved) else web.loadUrl(START)

        UpdateChecker.check(this, false)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        applyChrome()
    }

    // ═══════════ الألوان حسب الثيم ═══════════
    private fun applyChrome() {
        val dark = Theme.isDark(this)
        findViewById<View>(R.id.mainRoot).setBackgroundColor(
            Color.parseColor(if (dark) "#0A0A12" else "#F7F7FA")
        )
        topBar.setBackgroundColor(Color.parseColor(if (dark) "#101018" else "#FFFFFF"))
        titleText.setTextColor(Color.parseColor(Theme.text(this)))
        val icons = listOf(
            R.id.btnBack, R.id.btnFwd, R.id.btnHome, R.id.btnReload,
            R.id.btnLang, R.id.btnMenu
        )
        for (id in icons) {
            findViewById<ImageButton>(id).apply {
                imageTintList = android.content.res.ColorStateList.valueOf(
                    Color.parseColor(Theme.text(this@MainActivity))
                )
                setBackgroundResource(
                    if (dark) R.drawable.bg_pill_dark else R.drawable.bg_pill_light
                )
            }
        }
        web.setBackgroundColor(Color.parseColor(if (dark) "#0A0A12" else "#FFFFFF"))
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
        s.userAgentString = MOBILE_UA

        // الثيم: أندرويد 13+ يقدر يفرض الداكن فـالموقع، والقديم بـCSS
        applyWebTheme(s)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.setBackgroundColor(Color.parseColor(if (Theme.isDark(this)) "#0A0A12" else "#FFFFFF"))

        // السحب للتحديث يخدم غير كي تكون الصفحة فوق
        web.setOnScrollChangeListener { _, _, y, _, _ -> swipe.isEnabled = (y == 0) }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, req: WebResourceRequest
            ): Boolean = handleUrl(req.url)

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                handleUrl(Uri.parse(url))

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                titleText.text = getString(R.string.loading)
                backBtn.alpha = if (view.canGoBack()) 1f else 0.35f
                fwdBtn.alpha = if (view.canGoForward()) 1f else 0.35f
            }

            override fun onPageFinished(view: WebView, url: String) {
                swipe.isRefreshing = false
                titleText.text = view.title?.take(46) ?: "Latchi Arena"
                backBtn.alpha = if (view.canGoBack()) 1f else 0.35f
                fwdBtn.alpha = if (view.canGoForward()) 1f else 0.35f
                injectThemeCss(view)
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
                progress.visibility =
                    if (newProgress in 1..99 && !loadError) View.VISIBLE else View.GONE
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

            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
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
                Toast.makeText(this, getString(R.string.dl_unsupported), Toast.LENGTH_SHORT).show()
                return@setDownloadListener
            }
            try {
                val name = URLUtil.guessFileName(url, disposition, mime)
                val req = DownloadManager.Request(Uri.parse(url))
                req.setMimeType(mime)
                req.addRequestHeader("User-Agent", ua)
                CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
                req.setTitle(name)
                req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this, getString(R.string.dl_started) + " " + name, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.dl_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** ثيم الويب */
    private fun applyWebTheme(s: WebSettings) {
        val dark = Theme.isDark(this)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            try {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, dark)
            } catch (_: Throwable) {
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            try {
                WebSettingsCompat.setForceDark(
                    s,
                    if (dark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                )
            } catch (_: Throwable) {
            }
        }
    }

    /** لمسة CSS باش الموقع يمتزج مع التطبيق */
    private fun injectThemeCss(v: WebView) {
        val dark = Theme.isDark(this)
        val bg = if (dark) "#0A0A12" else "#FFFFFF"
        val js = """
            (function(){
              var id='latchi-theme';
              var old=document.getElementById(id);
              if(old) old.remove();
              var st=document.createElement('style');
              st.id=id;
              st.innerHTML = 'html,body{background-color:$bg !important;}' +
                '::-webkit-scrollbar{width:0px;height:0px;}';
              document.documentElement.appendChild(st);
            })();
        """.trimIndent()
        v.evaluateJavascript(js, null)
    }

    private fun handleUrl(u: Uri): Boolean {
        val scheme = u.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https") {
            val host = u.host?.lowercase() ?: return false
            if (INTERNAL.any { host == it || host.endsWith(".$it") }) return false
            return openExternal(u)
        }
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
        Toast.makeText(this, getString(R.string.no_app), Toast.LENGTH_SHORT).show()
        true
    }

    private fun showMenu() {
        val ar = Prefs.lang(this) == "ar"
        val items = arrayOf(
            if (ar) "🔄  إعادة تحميل" else "🔄  Reload",
            if (ar) "🖥️  نسخة سطح المكتب" else "🖥️  Desktop mode",
            if (ar) "🌐  فتح فـالمتصفح" else "🌐  Open in browser",
            (if (ar) "🎨  الثيم: " else "🎨  Theme: ") + Theme.label(this),
            if (ar) "🌍  تغيير اللغة" else "🌍  Change language",
            if (ar) "⬇️  تحديث التطبيق" else "⬇️  Check for updates",
            if (ar) "🧹  مسح الكاش" else "🧹  Clear cache",
            if (ar) "🚪  تسجيل الخروج" else "🚪  Sign out",
            if (ar) "ℹ️  عن التطبيق" else "ℹ️  About"
        )
        AlertDialog.Builder(this)
            .setTitle("Latchi Arena AI")
            .setItems(items) { _, i ->
                when (i) {
                    0 -> web.reload()
                    1 -> {
                        desktopMode = !desktopMode
                        web.settings.userAgentString =
                            if (desktopMode) DESKTOP_UA else MOBILE_UA
                        web.reload()
                    }
                    2 -> openExternal(Uri.parse(web.url ?: START))
                    3 -> {
                        Theme.cycle(this)
                        applyChrome()
                        applyWebTheme(web.settings)
                        web.reload()
                    }
                    4 -> toggleLang()
                    5 -> UpdateChecker.check(this, true)
                    6 -> clearCache()
                    7 -> confirmLogout()
                    else -> about()
                }
            }
            .setNegativeButton(if (ar) "إغلاق" else "Close", null)
            .show()
    }

    private fun clearCache() {
        web.clearCache(true)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        Toast.makeText(this, getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
        web.loadUrl(START)
    }

    private fun confirmLogout() {
        val ar = Prefs.lang(this) == "ar"
        AlertDialog.Builder(this)
            .setTitle(if (ar) "تسجيل الخروج" else "Sign out")
            .setMessage(
                if (ar) "يتنحى حسابك وكل الكوكيز والكاش من التطبيق. الموقع ما يتأثرش."
                else "Your session, cookies and cache will be cleared from the app."
            )
            .setPositiveButton(if (ar) "اخرج" else "Sign out") { _, _ ->
                logout()
            }
            .setNegativeButton(if (ar) "إلغاء" else "Cancel", null)
            .show()
    }

    private fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        web.clearCache(true)
        web.clearHistory()
        web.clearFormData()
        web.clearSslPreferences()
        try {
            android.webkit.WebStorage.getInstance().deleteAllData()
        } catch (_: Throwable) {
        }
        getSharedPreferences("latchi_arena", MODE_PRIVATE)
            .edit().putBoolean("logged_in", false).apply()
        goLogin()
    }

    private fun goLogin() {
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun about() {
        val ar = Prefs.lang(this) == "ar"
        AlertDialog.Builder(this)
            .setTitle("Latchi Arena AI  ·  v" + BuildConfig.VERSION_NAME)
            .setMessage(
                if (ar) "غلاف أصلي لموقع arena.ai\n\n" +
                    "• المحادثة واختيار الموديل والمقارنة والتصويت\n" +
                    "• رفع الصور والملفات · التحميل\n" +
                    "• تسجيل الدخول داخل التطبيق\n\n" +
                    "dz.latchi.arena"
                else "Native shell for arena.ai\n\n" +
                    "• Chat, model picker, comparison and voting\n" +
                    "• Uploads and downloads\n" +
                    "• In-app sign-in\n\n" +
                    "dz.latchi.arena"
            )
            .setPositiveButton(if (ar) "تمام" else "OK", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
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
        Toast.makeText(this, getString(R.string.press_again), Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
