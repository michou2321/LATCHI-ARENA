package dz.latchi.arena

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * شاشة الدخول — الواجهة الخارجية فقط.
 * تسجيل الدخول الحقيقي يصير فـarena.ai داخل التطبيق.
 */
class LoginActivity : BaseActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val langBtn = findViewById<Button>(R.id.langBtn)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val browserBtn = findViewById<TextView>(R.id.browserBtn)
        val skipBtn = findViewById<TextView>(R.id.skipBtn)
        val themeBtn = findViewById<ImageButton>(R.id.themeBtn)
        val verText = findViewById<TextView>(R.id.verText)
        val icon = findViewById<ImageView>(R.id.loginIcon)

        // الصورة تظهر بسلاسة
        icon.alpha = 0f
        icon.scaleX = .82f
        icon.scaleY = .82f
        icon.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(620).start()

        langBtn.text = if (Prefs.lang(this) == "ar") "عربي  •  ENGLISH" else "ENGLISH  •  عربي"
        langBtn.setOnClickListener {
            toggleLang()
        }

        themeBtn.setOnClickListener {
            Theme.cycle(this)
            applyBg()
            themeBtn.contentDescription = Theme.label(this)
        }

        startBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        browserBtn.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://arena.ai/")))
            } catch (_: Exception) {
            }
        }

        skipBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        verText.text = "v" + BuildConfig.VERSION_NAME + "  ·  arena.ai"
        applyBg()
    }

    @SuppressLint("SetTextI18n")
    private fun applyBg() {
        val dark = Theme.isDark(this)
        val root = findViewById<View>(R.id.loginRoot)
        val card = findViewById<LinearLayout>(R.id.loginCard)
        val title = findViewById<TextView>(R.id.loginTitle)
        val desc = findViewById<TextView>(R.id.loginDesc)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val browserBtn = findViewById<TextView>(R.id.browserBtn)
        val skipBtn = findViewById<TextView>(R.id.skipBtn)
        val themeBtn = findViewById<ImageButton>(R.id.themeBtn)
        val verText = findViewById<TextView>(R.id.verText)
        val langBtn = findViewById<Button>(R.id.langBtn)
        val ornament = findViewById<View>(R.id.ornament)

        root.setBackgroundResource(
            if (dark) R.drawable.bg_login_dark else R.drawable.bg_login_light
        )
        card.setBackgroundResource(
            if (dark) R.drawable.bg_card_dark else R.drawable.bg_card_light
        )
        title.setTextColor(Color.parseColor(Theme.text(this)))
        desc.setTextColor(Color.parseColor(Theme.sub(this)))
        browserBtn.setTextColor(Color.parseColor(if (dark) "#22D3EE" else "#0891B2"))
        skipBtn.setTextColor(Color.parseColor(Theme.sub(this)))
        verText.setTextColor(Color.parseColor(Theme.sub(this)))
        themeBtn.imageTintList = ColorStateList.valueOf(Color.parseColor(Theme.text(this)))
        themeBtn.setBackgroundResource(
            if (dark) R.drawable.bg_pill_dark else R.drawable.bg_pill_light
        )
        langBtn.setTextColor(Color.parseColor(Theme.text(this)))
        langBtn.setBackgroundResource(
            if (dark) R.drawable.bg_pill_dark else R.drawable.bg_pill_light
        )
        ornament.alpha = if (dark) 0.55f else 0.30f
        startBtn.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.brand_violet)
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
    }
}
