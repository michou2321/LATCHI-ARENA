package dz.latchi.arena

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/** كل الشاشات: تطبّق اللغة على السياق */
open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = Prefs.lang(newBase)
        val loc = Locale(lang)
        Locale.setDefault(loc)
        val cfg = Configuration(newBase.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cfg.setLocale(loc)
            cfg.setLayoutDirection(loc)
            super.attachBaseContext(newBase.createConfigurationContext(cfg))
        } else {
            @Suppress("DEPRECATION")
            cfg.locale = loc
            @Suppress("DEPRECATION")
            newBase.resources.updateConfiguration(cfg, newBase.resources.displayMetrics)
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val dark = Theme.isDark(this)
        window.decorView.setBackgroundColor(if (dark) 0xFF07070F.toInt() else 0xFFFFFFFF.toInt())
    }

    /** بدّل اللغة وعاود الشاشة */
    protected fun toggleLang() {
        Prefs.setLang(this, if (Prefs.lang(this) == "ar") "en" else "ar")
        recreate()
    }
}
