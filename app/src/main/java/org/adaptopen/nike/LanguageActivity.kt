package org.adaptopen.nike

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Scelta della lingua (inglese / italiano), mostrata dopo il logo la prima volta. */
class LanguageActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@LanguageActivity, 20), Ui.dp(this@LanguageActivity, 30), Ui.dp(this@LanguageActivity, 20), Ui.dp(this@LanguageActivity, 30))
        }
        root.addView(Ui.banner(this))
        val prompt = TextView(this).apply {
            typeface = Ui.MONO_BOLD; textSize = 16f; setTextColor(Ui.GREEN)
            Ui.glow(this)
        }
        Ui.add(root, prompt, 22)
        Ui.add(root, Ui.text(this, "> SELECT LANGUAGE", 13f, Ui.MUTED), 4)
        Ui.add(root, Ui.text(this, "> SELEZIONA LA LINGUA", 13f, Ui.MUTED), 0)
        Ui.add(root, Ui.btn(this, "English", true) { choose("en") }, 22)
        Ui.add(root, Ui.btn(this, "Italiano", true) { choose("it") }, 8)

        setContentView(Ui.screen(this, root))
        Ui.typewriter(prompt, "THE CITADEL TERMLINK_", 40)
        Ui.enter(root)
    }

    private fun choose(lang: String) {
        Settings.setLang(this, lang)
        S.lang = lang
        Flow.next(this)
    }
}
