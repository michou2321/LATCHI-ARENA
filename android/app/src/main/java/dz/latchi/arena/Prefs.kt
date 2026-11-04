package dz.latchi.arena

import android.content.Context

/** إعدادات بسيطة: اللغة · الثيم · آخر تحديث تم تخطيه */
object Prefs {
    private const val F = "latchi_arena"
    private const val K_LANG = "lang"
    private const val K_THEME = "theme"
    private const val K_SKIP = "skip_version"
    private const val K_FIRST = "first_run"

    private fun sp(c: Context) = c.getSharedPreferences(F, Context.MODE_PRIVATE)

    fun lang(c: Context): String = sp(c).getString(K_LANG, "ar") ?: "ar"
    fun setLang(c: Context, v: String) = sp(c).edit().putString(K_LANG, v).apply()

    /** auto · dark · light */
    fun theme(c: Context): String = sp(c).getString(K_THEME, "auto") ?: "auto"
    fun setTheme(c: Context, v: String) = sp(c).edit().putString(K_THEME, v).apply()

    fun skipped(c: Context): String = sp(c).getString(K_SKIP, "") ?: ""
    fun skip(c: Context, v: String) = sp(c).edit().putString(K_SKIP, v).apply()

    fun firstRun(c: Context): Boolean = sp(c).getBoolean(K_FIRST, true)
    fun doneFirstRun(c: Context) = sp(c).edit().putBoolean(K_FIRST, false).apply()
}
