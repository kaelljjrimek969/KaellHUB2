package com.kaelltzyy.kaellhub.core.wine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kaelltzyy.kaellhub.R
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class DownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // no timeout for large downloads
        .build()

    companion object {
        const val EXTRA_COMPONENT_ID = "component_id"
        const val CHANNEL_ID = "kaellhub_dl"
        const val ACTION_PROGRESS = "com.kaelltzyy.kaellhub.DOWNLOAD_PROGRESS"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_COMPONENT = "component"
        const val EXTRA_DONE = "done"
        const val EXTRA_ERROR = "error"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, buildNotification("KaellHUB", "Preparing download..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_COMPONENT_ID) ?: return START_NOT_STICKY
        val comp = ComponentCatalog.findById(id) ?: return START_NOT_STICKY
        scope.launch { download(comp) }
        return START_NOT_STICKY
    }

    private suspend fun download(comp: Component) {
        try {
            val destDir = File(WineEnv.rootDir(this), comp.installDir).also { it.mkdirs() }
            val archiveName = comp.downloadUrl.substringAfterLast("/")
            val archive = File(cacheDir, archiveName)

            // download
            val req = Request.Builder().url(comp.downloadUrl).build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body ?: throw Exception("empty response")
                val total = body.contentLength()
                var downloaded = 0L
                FileOutputStream(archive).use { out ->
                    body.byteStream().use { inp ->
                        val buf = ByteArray(8192)
                        var n: Int
                        while (inp.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                            downloaded += n
                            val pct = if (total > 0) (downloaded * 100 / total).toInt() else -1
                            broadcast(comp.id, pct, false, null)
                            updateNotification(comp.name, "Downloading... $pct%")
                        }
                    }
                }
            }

            // extract
            updateNotification(comp.name, "Extracting...")
            extract(archive, destDir)
            archive.delete()
            broadcast(comp.id, 100, true, null)
        } catch (e: Exception) {
            broadcast(comp.id, -1, false, e.message)
        } finally {
            stopSelf()
        }
    }

    private fun extract(archive: File, destDir: File) {
        val name = archive.name
        val cmd = when {
            name.endsWith(".tar.xz")  -> arrayOf("tar", "-xJf", archive.absolutePath, "-C", destDir.absolutePath)
            name.endsWith(".tar.gz")  -> arrayOf("tar", "-xzf", archive.absolutePath, "-C", destDir.absolutePath)
            name.endsWith(".tar.bz2") -> arrayOf("tar", "-xjf", archive.absolutePath, "-C", destDir.absolutePath)
            name.endsWith(".zip")     -> arrayOf("unzip", "-o", archive.absolutePath, "-d", destDir.absolutePath)
            else -> throw Exception("unknown archive format: $name")
        }
        val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val exit = proc.waitFor()
        if (exit != 0) throw Exception("extract failed: ${proc.inputStream.bufferedReader().readText()}")
        // set executable bits on bin/
        File(destDir, "bin").listFiles()?.forEach { it.setExecutable(true, false) }
    }

    private fun broadcast(id: String, progress: Int, done: Boolean, error: String?) {
        sendBroadcast(Intent(ACTION_PROGRESS).apply {
            putExtra(EXTRA_COMPONENT, id)
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_DONE, done)
            error?.let { putExtra(EXTRA_ERROR, it) }
        })
    }

    private fun buildNotification(title: String, text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title).setContentText(text)
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, buildNotification(title, text))
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
