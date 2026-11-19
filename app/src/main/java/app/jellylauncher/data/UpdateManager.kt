package app.jellylauncher.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.pm.PackageInfoCompat
import app.jellylauncher.UpdateReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Your GitHub repo that builds Foyer. Change this if you rename the repo. */
const val UPDATE_REPO = "longmoneysniper/Foyer"

data class UpdateInfo(val build: Int, val url: String)

/** Checks your GitHub releases for a newer build and installs it. */
class UpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun installedBuild(): Int =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt()

    /** Returns the newer build if there is one, otherwise null. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$UPDATE_REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.content ?: return@use null
            val build = tag.removePrefix("build-").toIntOrNull() ?: return@use null
            val asset = root["assets"]?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                ?: return@use null
            val url = asset["browser_download_url"]?.jsonPrimitive?.content ?: return@use null
            if (build > installedBuild()) UpdateInfo(build, url) else null
        }
    }

    /** Downloads the APK and hands it to Android's installer, which asks you to confirm. */
    suspend fun install(info: UpdateInfo) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
        }
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        try {
            val request = Request.Builder().url(info.url).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Download failed (HTTP ${resp.code})")
                val body = resp.body ?: throw IOException("Empty download")
                session.openWrite("encore.apk", 0, body.contentLength()).use { out ->
                    body.byteStream().copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(UpdateReceiver.ACTION)
            val pending = PendingIntent.getBroadcast(
                context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }
}
