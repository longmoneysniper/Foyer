package app.jellylauncher.data

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val JELLYFIN_PACKAGE = "org.jellyfin.androidtv"
const val WHOLPHIN_PACKAGE = "com.github.damontecres.wholphin"

/** Lists the apps installed on the TV so the launcher can show them in the dock and the All apps screen. */
class AppsRepository(private val context: Context) {

    private class Entry(val app: LaunchableApp, val leanback: Boolean)

    suspend fun load(): List<LaunchableApp> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val leanback = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
        )
        val regular = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )

        val seen = mutableSetOf(context.packageName)
        val entries = mutableListOf<Entry>()

        for ((isLeanback, list) in listOf(true to leanback, false to regular)) {
            for (info in list) {
                val pkg = info.activityInfo.packageName
                if (!seen.add(pkg)) continue

                val icon = try {
                    stripDarkBackground(info.loadIcon(pm).toBitmap(128, 128))
                } catch (e: Exception) {
                    continue
                }
                val banner = try {
                    val drawable = info.activityInfo.loadBanner(pm)
                        ?: info.activityInfo.applicationInfo.loadBanner(pm)
                    drawable?.toBitmap(320, 180)
                } catch (e: Exception) {
                    null
                }

                entries += Entry(
                    app = LaunchableApp(
                        label = info.loadLabel(pm).toString(),
                        packageName = pkg,
                        icon = icon.asImageBitmap(),
                        banner = banner?.asImageBitmap(),
                    ),
                    leanback = isLeanback,
                )
            }
        }

        // Jellyfin first, then real TV apps, then everything else; alphabetical inside each group.
        entries
            .sortedWith(
                compareBy<Entry>(
                    { it.app.packageName != WHOLPHIN_PACKAGE },
                    { it.app.packageName != JELLYFIN_PACKAGE },
                    { !it.leanback },
                    { it.app.label.lowercase() },
                )
            )
            .map { it.app }
    }

    /**
     * Some app icons (Wholphin, for one) come with a solid black square behind the logo.
     * If the icon's edge is mostly near-black, make that background transparent: flood in from
     * the edges through near-black or already-transparent pixels, so dark parts inside the logo stay.
     */
    private fun stripDarkBackground(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        fun clear(c: Int) = (c ushr 24) < 16
        fun dark(c: Int) = (c ushr 24) >= 16 &&
            ((c shr 16) and 255) < 56 && ((c shr 8) and 255) < 56 && (c and 255) < 56

        val border = ArrayList<Int>()
        for (x in 0 until w) { border.add(x); border.add((h - 1) * w + x) }
        for (y in 0 until h) { border.add(y * w); border.add(y * w + w - 1) }
        val darkCount = border.count { dark(px[it]) }
        if (darkCount * 10 < border.size * 3) return src // edge is not mostly dark: leave it alone

        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var top = 0
        for (i in border) {
            if (!seen[i] && (dark(px[i]) || clear(px[i]))) { seen[i] = true; stack[top++] = i }
        }
        while (top > 0) {
            val i = stack[--top]
            px[i] = 0
            val x = i % w
            val y = i / w
            if (x > 0) { val n = i - 1; if (!seen[n] && (dark(px[n]) || clear(px[n]))) { seen[n] = true; stack[top++] = n } }
            if (x < w - 1) { val n = i + 1; if (!seen[n] && (dark(px[n]) || clear(px[n]))) { seen[n] = true; stack[top++] = n } }
            if (y > 0) { val n = i - w; if (!seen[n] && (dark(px[n]) || clear(px[n]))) { seen[n] = true; stack[top++] = n } }
            if (y < h - 1) { val n = i + w; if (!seen[n] && (dark(px[n]) || clear(px[n]))) { seen[n] = true; stack[top++] = n } }
        }
        return android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888)
    }
}
