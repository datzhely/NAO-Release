package org.adaptopen.nike

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView

/** Schermata iniziale con il logo di The Citadel: compare in dissolvenza e sparisce dopo un paio di secondi. */
class SplashActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val img = ImageView(this).apply {
            setImageResource(R.drawable.citadel_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val p = Ui.dp(this@SplashActivity, 12)
            setPadding(p, p, p, p)
            alpha = 0f
            scaleX = 0.92f
            scaleY = 0.92f
        }
        root.addView(img, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        img.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start()
        handler.postDelayed({
            img.animate().alpha(0f).setDuration(500).withEndAction { goNext() }.start()
        }, 2000)
    }

    private fun goNext() {
        if (isFinishing) return
        Flow.next(this)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
