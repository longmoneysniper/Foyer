package app.jellylauncher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AuthExpiredException : IOException("Session expired")

class JellyfinApi(private val prefs: Prefs) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun authHeader(token: String?): String = buildString {
        append("MediaBrowser Client=\"Jelly Launcher\", Device=\"Android TV\", ")
        append("DeviceId=\"${prefs.deviceId}\", Version=\"1.0\"")
        if (token != null) append(", Token=\"$token\"")
    }

    fun normalizeUrl(raw: String): String {
        var url = raw.trim().trimEnd('/')
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "http://$url"
        }
        return url
    }

    /** Signs in and stores server, token and user id. Throws IOException with a readable message. */
    suspend fun login(server: String, username: String, password: String) {
        val base = normalizeUrl(server)
        val body = buildJsonObject {
            put("Username", username)
            put("Pw", password)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$base/Users/AuthenticateByName".toHttpUrl())
            .header("Authorization", authHeader(null))
            .post(body)
            .build()

        val result = withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 -> throw IOException("Wrong username or password")
                    !resp.isSuccessful -> throw IOException("Server answered with HTTP ${resp.code}: ${text.take(150)}")
                    else -> json.decodeFromString<AuthResult>(text)
                }
            }
        }

        prefs.serverUrl = base
        prefs.token = result.accessToken
        prefs.userId = result.user.id
        prefs.userName = result.user.name
    }

    /** Items that are partly watched, most recently played first (Jellyfin's "Continue Watching"). */
    suspend fun continueWatching(): List<JfItem> = withContext(Dispatchers.IO) {
        val userId = prefs.userId ?: throw AuthExpiredException()
        val url = "${prefs.serverUrl}/Users/$userId/Items/Resume".toHttpUrl().newBuilder()
            .addQueryParameter("Limit", "20")
            .addQueryParameter("MediaTypes", "Video")
            .addQueryParameter("Fields", "Overview,Genres,ProviderIds")
            .addQueryParameter("EnableUserData", "true")
            .addQueryParameter("EnableImageTypes", "Primary,Backdrop,Thumb")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(prefs.token))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            when {
                resp.code == 401 -> throw AuthExpiredException()
                !resp.isSuccessful -> throw IOException("Server answered with HTTP ${resp.code}: ${text.take(150)}")
                else -> json.decodeFromString<ItemsResponse>(text).items
            }
        }
    }

    private fun fetchText(url: okhttp3.HttpUrl): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(prefs.token))
            .build()
        return client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            when {
                resp.code == 401 -> throw AuthExpiredException()
                !resp.isSuccessful -> throw IOException("Server answered with HTTP ${resp.code}: ${text.take(150)}")
                else -> text
            }
        }
    }

    /** The next unwatched episode of the shows you follow. */
    suspend fun nextUp(): List<JfItem> = withContext(Dispatchers.IO) {
        val userId = prefs.userId ?: throw AuthExpiredException()
        val url = "${prefs.serverUrl}/Shows/NextUp".toHttpUrl().newBuilder()
            .addQueryParameter("UserId", userId)
            .addQueryParameter("Limit", "12")
            .addQueryParameter("Fields", "Overview,Genres,ProviderIds")
            .addQueryParameter("EnableUserData", "true")
            .addQueryParameter("EnableResumable", "false")
            .addQueryParameter("EnableImageTypes", "Primary,Backdrop,Thumb")
            .build()
        json.decodeFromString<ItemsResponse>(fetchText(url)).items
    }

    /** Recently added movies and episodes you haven't watched. */
    suspend fun latest(): List<JfItem> = withContext(Dispatchers.IO) {
        val userId = prefs.userId ?: throw AuthExpiredException()
        val url = "${prefs.serverUrl}/Users/$userId/Items/Latest".toHttpUrl().newBuilder()
            .addQueryParameter("Limit", "16")
            .addQueryParameter("IncludeItemTypes", "Movie,Episode")
            .addQueryParameter("GroupItems", "false")
            .addQueryParameter("IsPlayed", "false")
            .addQueryParameter("Fields", "Overview,Genres,ProviderIds")
            .addQueryParameter("EnableUserData", "true")
            .addQueryParameter("EnableImageTypes", "Primary,Backdrop,Thumb")
            .build()
        json.decodeFromString<List<JfItem>>(fetchText(url))
    }

    /** The IMDb id of a show (episodes are rated by their show). */
    suspend fun imdbIdOf(itemId: String): String? = withContext(Dispatchers.IO) {
        val userId = prefs.userId ?: throw AuthExpiredException()
        val url = "${prefs.serverUrl}/Users/$userId/Items/$itemId".toHttpUrl().newBuilder()
            .addQueryParameter("Fields", "ProviderIds")
            .build()
        json.decodeFromString<JfItem>(fetchText(url)).providerIds["Imdb"]
    }

    // ---- mapping to UI models ----

    fun toCard(item: JfItem, section: String = "Continue watching"): ResumeCard {
        val isEpisode = item.type == "Episode"
        val title = if (isEpisode) item.seriesName ?: item.name else item.name
        val episodeLabel =
            if (item.seasonNumber != null && item.episodeNumber != null) "S${item.seasonNumber}:E${item.episodeNumber}" else null
        val genres = item.genres.take(3).joinToString(" · ")
        val subtitle = if (isEpisode) {
            listOfNotNull(episodeLabel, item.name).joinToString(" · ")
        } else {
            genres.ifEmpty { item.year?.toString().orEmpty() }
        }
        val word = when (section) {
            "Next up" -> "NEXT UP"
            "Latest added" -> if (isEpisode) "NEW EPISODE" else "NEW MOVIE"
            else -> if (isEpisode) "EPISODE" else "MOVIE"
        }
        val kicker = if (isEpisode) {
            listOfNotNull(word, episodeLabel).joinToString(" · ")
        } else {
            listOfNotNull(word, item.year?.toString()).joinToString(" · ")
        }

        val total = item.runTimeTicks ?: 0L
        val position = item.userData?.playbackPositionTicks ?: 0L
        val progress = if (total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f

        return ResumeCard(
            id = item.id,
            title = title,
            subtitle = subtitle,
            overview = item.overview.orEmpty(),
            progress = progress,
            remaining = if (position > 0) remainingText(total - position) else runtimeText(total),
            cardImage = cardImage(item),
            heroImage = heroImage(item),
            poster = poster(item),
            kicker = kicker,
            positionMs = position / 10_000,
            section = section,
            logo = logo(item),
            chips = listOfNotNull(
                item.year?.toString(),
                item.officialRating?.takeIf { it.isNotBlank() },
                runtimeText(total).ifEmpty { null },
            ),
            resumeAt = resumeAtText(position),
            isNew = section == "Latest added",
            imdbId = item.providerIds["Imdb"],
            community = item.communityRating,
            critic = item.criticRating?.toInt(),
            seriesId = if (isEpisode) item.seriesId else null,
        )
    }

    /** The show or movie's title artwork (transparent logo), when Jellyfin has one. */
    private fun logo(item: JfItem): String? {
        item.imageTags["Logo"]?.let { return image(item.id, "Logo", it, 600) }
        val parent = item.parentLogoItemId
        val tag = item.parentLogoImageTag
        if (parent != null && tag != null) return image(parent, "Logo", tag, 600)
        return null
    }

    private fun resumeAtText(ticks: Long): String {
        if (ticks <= 0) return ""
        val s = ticks / 10_000_000L
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("Resume at %d:%02d:%02d", h, m, sec) else String.format("Resume at %d:%02d", m, sec)
    }

    /** Tall poster: the series poster for episodes, the item's own poster for movies. */
    private fun poster(item: JfItem): String? {
        val seriesId = item.seriesId
        val seriesTag = item.seriesPrimaryImageTag
        if (item.type == "Episode" && seriesId != null && seriesTag != null) {
            return image(seriesId, "Primary", seriesTag, 400)
        }
        item.imageTags["Primary"]?.let { return image(item.id, "Primary", it, 400) }
        return null
    }

    private fun runtimeText(ticks: Long): String {
        if (ticks <= 0) return ""
        val minutes = (ticks / TICKS_PER_MINUTE).toInt().coerceAtLeast(1)
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"
    }

    private fun remainingText(ticksLeft: Long): String {
        if (ticksLeft <= 0) return ""
        val minutes = (ticksLeft / TICKS_PER_MINUTE).toInt().coerceAtLeast(1)
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m left" else "$minutes min left"
    }

    private fun image(itemId: String, type: String, tag: String?, maxWidth: Int): String {
        val path = if (type == "Backdrop") "Backdrop/0" else type
        val tagPart = if (tag != null) "&tag=$tag" else ""
        return "${prefs.serverUrl}/Items/$itemId/Images/$path?maxWidth=$maxWidth$tagPart"
    }

    /** 16:9 picture for the card: thumb, then backdrop (own or series), then primary. */
    private fun cardImage(item: JfItem): String? {
        item.imageTags["Thumb"]?.let { return image(item.id, "Thumb", it, 640) }
        item.backdropImageTags.firstOrNull()?.let { return image(item.id, "Backdrop", it, 640) }
        val parent = item.parentBackdropItemId
        if (parent != null && item.parentBackdropImageTags.isNotEmpty()) {
            return image(parent, "Backdrop", item.parentBackdropImageTags.first(), 640)
        }
        item.imageTags["Primary"]?.let { return image(item.id, "Primary", it, 640) }
        return null
    }

    /** Wide backdrop for the hero area behind the rows. */
    private fun heroImage(item: JfItem): String? {
        item.backdropImageTags.firstOrNull()?.let { return image(item.id, "Backdrop", it, 1920) }
        val parent = item.parentBackdropItemId
        if (parent != null && item.parentBackdropImageTags.isNotEmpty()) {
            return image(parent, "Backdrop", item.parentBackdropImageTags.first(), 1920)
        }
        item.imageTags["Thumb"]?.let { return image(item.id, "Thumb", it, 1920) }
        return null
    }

    private companion object {
        const val TICKS_PER_MINUTE = 600_000_000L
    }
}
