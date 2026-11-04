package dz.latchi.arena

import android.content.Context
import android.content.res.Configuration

/** الثيم: تلقائي · داكن · فاتح */
object Theme {

    fun isDark(c: Context): Boolean = when (Prefs.theme(c)) {
        "dark" -> true
        "light" -> false
        else -> (c.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun label(c: Context): String {
        val ar = Prefs.lang(c) == "ar"
        return when (Prefs.theme(c)) {
            "dark" -> if (ar) "داكن" else "Dark"
            "light" -> if (ar) "فاتح" else "Light"
            else -> if (ar) "تلقائي" else "Auto"
        }
    }

    fun cycle(c: Context) {
        Prefs.setTheme(c, when (Prefs.theme(c)) {
            "auto" -> "dark"
            "dark" -> "light"
            else -> "auto"
        })
    }

    /** ألوان الواجهة الخارجية */
    fun bg(c: Context) = if (isDark(c)) "#0A0A12" else "#FFFFFF"
    fun card(c: Context) = if (isDark(c)) "#14141F" else "#F2F3F7"
    fun text(c: Context) = if (isDark(c)) "#EDEDF5" else "#101018"
    fun sub(c: Context) = if (isDark(c)) "#9A9AB0" else "#5B5B70"
}
