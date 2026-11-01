package org.adaptopen.nike

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

/** Termini d'uso. Modo "accept": vanno accettati per usare l'app. Modo "view": sola lettura. */
class TermsActivity : Activity() {

    private var acceptMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptMode = intent.getStringExtra("mode") == "accept"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@TermsActivity, 16), Ui.dp(this@TermsActivity, 22), Ui.dp(this@TermsActivity, 16), Ui.dp(this@TermsActivity, 28))
        }

        if (!acceptMode) root.addView(Ui.btn(this, S.t("back")) { finish() }, LinearLayout.LayoutParams(Ui.dp(this, 130), LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = S.t("terms_title"); textSize = 22f; typeface = Ui.MONO_BOLD; setTextColor(Ui.GREEN)
            setPadding(0, Ui.dp(this@TermsActivity, 10), 0, 0); Ui.glow(this)
        })
        root.addView(Ui.text(this, S.t("terms_version", Settings.TERMS_VERSION), 12f, Ui.MUTED))

        val card = Ui.card(this)
        card.addView(Ui.text(this, S.t("terms_intro"), 13f))
        Terms.sections().forEach { (title, body) ->
            card.addView(TextView(this).apply {
                text = title; textSize = 13f; typeface = Ui.MONO_BOLD; setTextColor(Ui.GREEN)
                setPadding(0, Ui.dp(this@TermsActivity, 14), 0, Ui.dp(this@TermsActivity, 2)); Ui.glow(this)
            })
            card.addView(Ui.text(this, body, 12.5f, Ui.GREEN))
        }
        card.addView(Ui.text(this, S.t("terms_discord"), 12.5f, Ui.MUTED).apply { setPadding(0, Ui.dp(this@TermsActivity, 14), 0, 0) })
        card.addView(Ui.btn(this, S.t("discord_btn")) { Flow.openDiscord(this) })
        Ui.add(root, card, 12)

        if (acceptMode) {
            val check = CheckBox(this).apply {
                text = S.t("terms_check"); typeface = Ui.MONO; textSize = 13f; setTextColor(Ui.GREEN)
                buttonTintList = ColorStateList.valueOf(Ui.GREEN)
            }
            val accept = Ui.btn(this, S.t("terms_accept"), true) {
                if (check.isChecked) {
                    Settings.acceptTerms(this)
                    Flow.next(this)
                }
            }
            accept.alpha = 0.35f
            check.setOnCheckedChangeListener { _, on -> accept.animate().alpha(if (on) 1f else 0.35f).setDuration(160).start() }
            val decline = Ui.btn(this, S.t("terms_decline")) { finishAffinity() }
            Ui.add(root, check, 14)
            Ui.add(root, accept, 6)
            Ui.add(root, decline, 4)
        }

        setContentView(Ui.screen(this, root))
        Ui.enter(root)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (acceptMode) finishAffinity() else super.onBackPressed()
    }
}
