package org.adaptopen.nike

import android.app.Activity
import android.content.Intent
import android.net.Uri

/** Percorso di avvio: logo -> lingua (solo la prima volta) -> termini (finche' non accettati) -> home. */
object Flow {
    @Suppress("DEPRECATION")
    fun next(a: Activity) {
        val cls: Class<*> = when {
            Settings.lang(a) == null -> LanguageActivity::class.java
            !Settings.termsAccepted(a) -> TermsActivity::class.java
            else -> MainActivity::class.java
        }
        val i = Intent(a, cls)
        if (cls == TermsActivity::class.java) i.putExtra("mode", "accept")
        a.startActivity(i)
        a.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        a.finish()
    }

    /** Apre l'invito al server Discord di The Citadel. */
    fun openDiscord(a: Activity) {
        try {
            a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_URL)))
        } catch (e: Exception) {
            ShoeManager.log(S.t("discord_fail"))
        }
    }
}
