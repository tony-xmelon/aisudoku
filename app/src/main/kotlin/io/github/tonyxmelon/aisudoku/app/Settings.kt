package io.github.tonyxmelon.aisudoku.app

import android.content.Context
import androidx.core.content.edit

/** Preferences that survive restarts. Deliberately few. */
data class Settings(
    val hintStyle: HintStyle = HintStyle.EXPLAIN,
    val autoCapture: Boolean = true,
) {
    companion object {
        private const val FILE = "aisudoku.settings"
        private const val KEY_HINT_STYLE = "hintStyle"
        private const val KEY_AUTO_CAPTURE = "autoCapture"

        fun load(context: Context): Settings {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return Settings(
                hintStyle = runCatching {
                    HintStyle.valueOf(prefs.getString(KEY_HINT_STYLE, null) ?: HintStyle.EXPLAIN.name)
                }.getOrDefault(HintStyle.EXPLAIN),
                autoCapture = prefs.getBoolean(KEY_AUTO_CAPTURE, true),
            )
        }

        fun save(context: Context, settings: Settings) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
                putString(KEY_HINT_STYLE, settings.hintStyle.name)
                putBoolean(KEY_AUTO_CAPTURE, settings.autoCapture)
            }
        }
    }
}
