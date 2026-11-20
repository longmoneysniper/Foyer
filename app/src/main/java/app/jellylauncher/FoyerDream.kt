package app.jellylauncher

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import app.jellylauncher.data.ResumeCard
import coil.load
import kotlinx.serialization.json.Json

/** Screensaver: slowly cycles through the backdrops of what's on your Foyer home screen. */
class FoyerDream : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private var cards: List<ResumeCard> = emptyList()
    private var index = 0
    private var image: ImageView? = null
    private var title: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            if (cards.isNotEmpty()) {
                val card = cards[index % cards.size]
                index++
                image?.load(card.heroImage ?: card.cardImage) { crossfade(1500) }
                title?.text = card.title
            }
            handler.postDelayed(this, 12_000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        cards = try {
            val saved = getSharedPreferences("cache", MODE_PRIVATE).getString("cards", "[]").orEmpty()
            Json { ignoreUnknownKeys = true }.decodeFromString<List<ResumeCard>>(saved)
                .filter { it.heroImage != null || it.cardImage != null }
                .shuffled()
        } catch (e: Exception) {
            emptyList()
        }

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        root.addView(img, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val label = TextView(this)
        label.setTextColor(Color.WHITE)
        label.textSize = 28f
        label.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        label.setShadowLayer(12f, 0f, 3f, Color.BLACK)
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.BOTTOM or Gravity.START
        lp.setMargins(96, 0, 96, 72)
        root.addView(label, lp)

        image = img
        title = label
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.post(tick)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(tick)
        super.onDreamingStopped()
    }
}
