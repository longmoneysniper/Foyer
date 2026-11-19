package app.jellylauncher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Scores for one title. Any of them can be missing. */
@Serializable
data class Scores(
    val imdb: String? = null,
    val critics: String? = null,
    val audience: String? = null,
)

/** Looks up IMDb and Rotten Tomatoes (critics and audience) scores from mdblist.com and remembers them. */
class RatingsRepository(context: Context, private val prefs: Prefs) {

    private val sp = context.applicationContext.getSharedPreferences("ratings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** [isShow] is true for series and episodes, false for movies. */
    suspend fun get(imdbId: String, isShow: Boolean): Scores? = withContext(Dispatchers.IO) {
        val cacheKey = "v2:" + (if (isShow) "show:" else "movie:") + imdbId
        val saved = sp.getString(cacheKey, null)
        if (saved != null) {
            try {
                return@withContext json.decodeFromString<Scores>(saved)
            } catch (e: Exception) {
                // fall through and fetch again
            }
        }
        val key = prefs.ratingsKey
        if (key.isBlank()) return@withContext null

        val kind = if (isShow) "show" else "movie"
        val url = "https://api.mdblist.com/imdb/$kind/$imdbId/".toHttpUrl().newBuilder()
            .addQueryParameter("apikey", key)
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            val ratings = root["ratings"]?.jsonArray?.map { it.jsonObject } ?: return@use null

            fun valueOf(vararg sources: String): Double? {
                val entry: JsonObject = ratings.firstOrNull { it["source"]?.jsonPrimitive?.content in sources } ?: return null
                for (field in listOf("value", "score")) {
                    val v = entry[field]
                    if (v == null || v is JsonNull) continue
                    val d = v.jsonPrimitive.content.toDoubleOrNull()
                    if (d != null && d > 0.0) return d
                }
                return null
            }

            val scores = Scores(
                imdb = valueOf("imdb")?.let { String.format("%.1f", if (it > 10.0) it / 10.0 else it) },
                critics = valueOf("tomatoes")?.let { "${it.toInt()}%" },
                audience = valueOf("tomatoesaudience", "popcorn")?.let { "${it.toInt()}%" },
            )
            // Only remember a result that has something in it, so an empty answer is retried later.
            if (scores.imdb != null || scores.critics != null || scores.audience != null) {
                sp.edit().putString(cacheKey, json.encodeToString(Scores.serializer(), scores)).apply()
            }
            scores
        }
    }
}
