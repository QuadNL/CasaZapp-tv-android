package nl.casazapp.tv.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.casazapp.tv.BuildConfig

/**
 * Updates the app from the GitHub releases of CasaZapp-tv-android, independent of the server:
 * each release is tagged `build-N`, with N the version code. Android asks the user to confirm.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/QuadNL/CasaZapp-tv-android/releases/latest"

    data class Release(val build: Int, val apkUrl: String)

    /** The latest release when it is newer than this app; null when up to date or unreachable. */
    suspend fun newer(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val body = open(LATEST).inputStream.bufferedReader().use { it.readText() }
            val release = Json.parseToJsonElement(body).jsonObject
            val build = release["tag_name"]!!.jsonPrimitive.content.removePrefix("build-").toInt()
            val apk = release["assets"]!!.jsonArray
                .map { it.jsonObject }
                .first { it["name"]!!.jsonPrimitive.content.endsWith(".apk") }["browser_download_url"]!!
                .jsonPrimitive.content
            Release(build, apk)
        }.getOrNull()?.takeIf { it.build > BuildConfig.VERSION_CODE }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CasaZapp-TV")
            connectTimeout = 15_000
            readTimeout = 60_000
        }

    /** Downloads the APK and hands it to the system installer, which asks the user to confirm. */
    suspend fun install(context: Context, release: Release, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update.apk")
        val connection = open(release.apkUrl)
        val total = connection.contentLengthLong.coerceAtLeast(1)
        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    onProgress(done.toFloat() / total)
                }
            }
        }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("casazapp-tv.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val result = PendingIntent.getBroadcast(context, id, Intent(context, InstallResult::class.java), flags)
            session.commit(result.intentSender)
        }
    }
}

/** The installer reports back here; when it needs the user's OK, show its confirmation screen. */
class InstallResult : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        @Suppress("DEPRECATION")
        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
