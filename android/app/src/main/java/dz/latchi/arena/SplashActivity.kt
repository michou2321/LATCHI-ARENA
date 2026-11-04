package dz.latchi.arena

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView

/**
 * شاشة الترحيب — صورة أصلية + نص حاد + انتقال تلقائي.
 * ضغطة واحدة = تجاوز فوري.
 */
class SplashActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var advanced = false

    private val next = Runnable { goNext() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val bar = findViewById<ProgressBar>(R.id.splashProgress)
        val logo = findViewById<TextView>(R.id.splashLogo)
        val sub = findViewById<TextView>(R.id.splashSub)
        val tag = findViewById<TextView>(R.id.splashTag)
        val root = findViewById<View>(R.id.splashRoot)

        // حركة دخول خفيفة
        listOf<View>(logo, sub, tag).forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = 26f
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(160L + i * 130L)
                .setDuration(520L)
                .start()
        }

        // شريط تقدّم وهمي
        bar.progress = 0
        val step = object : Runnable {
            private var p = 0
            override fun run() {
                p += 3 + (0..4).random()
                if (p > 100) p = 100
                bar.progress = p
                if (p < 100) handler.postDelayed(this, 42)
            }
        }
        handler.postDelayed(step, 120)

        root.setOnClickListener { goNext() }
        handler.postDelayed(next, 2400)
    }

    private fun goNext() {
        if (advanced) return
        advanced = true
        handler.removeCallbacksAndMessages(null)
        val intent = if (Prefs.firstRun(this)) {
            Prefs.doneFirstRun(this)
            Intent(this, LoginActivity::class.java)
        } else {
            val to = if (isLoggedIn()) MainActivity::class.java else LoginActivity::class.java
            Intent(this, to)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun isLoggedIn(): Boolean =
        getSharedPreferences("latchi_arena", MODE_PRIVATE).getBoolean("logged_in", false)

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
