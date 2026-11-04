package dz.latchi.arena

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * فحص التحديثات مباشرة من GitHub Releases.
 * يشتغل فـالخلفية بلا ما يعلّق التطبيق.
 */
object UpdateChecker {

    private const val API =
        "https://api.github.com/repos/michou2321/LATCHI-ARENA/releases/latest"

    private val pool = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    fun check(ctx: Context, manual: Boolean) {
        val app = ctx.applicationContext
        pool.execute {
            var tag: String? = null
            var url: String? = null
            var notes: String? = null
            var err = false
            try {
                val c = (URL(API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "LatchiArena")
                }
                if (c.responseCode == 200) {
                    val body = c.inputStream.bufferedReader().use { it.readText() }
                    val o = JSONObject(body)
                    tag = o.optString("tag_name", "")
                    notes = o.optString("body", "")
                    val assets = o.optJSONArray("assets")
                    if (assets != null) {
                        var i = 0
                        while (i < assets.length()) {
                            val a = assets.optJSONObject(i)
                            val n = a?.optString("name", "") ?: ""
                            if (n.endsWith(".apk")) {
                                url = a?.optString("browser_download_url", "")
                                break
                            }
                            i++
                        }
                    }
                } else {
                    err = true
                }
                c.disconnect()
            } catch (e: Exception) {
                err = true
            }

            val finalTag = tag
            val finalUrl = url
            val finalNotes = notes
            val failed = err

            ui.post {
                if (failed) {
                    if (manual) {
                        Toast.makeText(
                            app,
                            if (Prefs.lang(app) == "ar") "ما قدرناش نتحققو — تحقق من الإنترنت"
                            else "Could not check — verify your connection",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@post
                }
                if (finalTag.isNullOrBlank()) return@post
                val remote = finalTag.removePrefix("v").trim()
                val local = versionOf(app)
                if (!isNewer(remote, local)) {
                    if (manual) {
                        Toast.makeText(
                            app,
                            if (Prefs.lang(app) == "ar") "راك على آخر إصدار ✓"
                            else "You're on the latest version ✓",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@post
                }
                if (!manual && Prefs.skipped(app) == remote) return@post
                showDialog(app, remote, finalUrl, finalNotes)
            }
        }
    }

    private fun versionOf(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(s: String) = s.split(".").map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x > y) return true
            if (x < y) return false
        }
        return false
    }

    private fun showDialog(ctx: Context, v: String, url: String?, notes: String?) {
        val ar = Prefs.lang(ctx) == "ar"
        val dlg = AlertDialog.Builder(ctx, R.style.Theme_LatchiArena_Dialog)
            .setTitle(if (ar) "تحديث متاح — v$v" else "Update available — v$v")
            .setMessage(
                (if (ar) "نسخة جديدة من Latchi Arena AI واجدة.\nحدّث باش تلقى كل الجديد.\n\n"
                else "A new version of Latchi Arena AI is available.\n\n") +
                    (notes?.take(260)?.replace("#", "")?.trim() ?: "")
            )
            .setPositiveButton(if (ar) "حدّث دوكا" else "Update now") { _, _ ->
                if (url != null) {
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                    } catch (e: Exception) {
                    }
                }
            }
            .setNegativeButton(if (ar) "من بعد" else "Later", null)
            .setNeutralButton(if (ar) "تخطّي هاذ النسخة" else "Skip this version") { _, _ ->
                Prefs.skip(ctx, v)
            }
            .create()
        dlg.show()
    }
}
