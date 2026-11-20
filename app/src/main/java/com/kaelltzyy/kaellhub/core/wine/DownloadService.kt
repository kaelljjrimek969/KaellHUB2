package com.kaelltzyy.kaellhub.core.wine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream

class DownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val active = AtomicInteger(0)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
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
        goForeground("KaellHUB", "Preparing download...")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // must call startForeground again every start (startForegroundService contract)
        goForeground("KaellHUB", "Preparing download...")
        val id = intent?.getStringExtra(EXTRA_COMPONENT_ID)
        val comp = id?.let { ComponentCatalog.findById(it) }
        if (comp == null) {
            if (active.get() == 0) stopSelf()
            return START_NOT_STICKY
        }
        active.incrementAndGet()
        scope.launch { download(comp) }
        return START_NOT_STICKY
    }

    private fun goForeground(title: String, text: String) {
        val n = buildNotification(title, text)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, n)
        }
    }

    private fun download(comp: Component) {
        val archive = File(cacheDir, comp.downloadUrl.substringAfterLast("/"))
        val tmpDir = File(cacheDir, "extract_${comp.id}")
        try {
            broadcast(comp.id, 0, false, null)

            val req = Request.Builder().url(comp.downloadUrl).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw Exception("HTTP ${resp.code} - link download tidak valid: ${comp.downloadUrl}")
                }
                val body = resp.body ?: throw Exception("response kosong")
                val total = body.contentLength()
                var downloaded = 0L
                var lastPct = -1
                FileOutputStream(archive).use { out ->
                    body.byteStream().use { inp ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = inp.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            downloaded += n
                            val pct = if (total > 0) (downloaded * 100 / total).toInt().coerceAtMost(99) else 0
                            if (pct != lastPct) {          // only when % changes, no UI flood
                                lastPct = pct
                                broadcast(comp.id, pct, false, null)
                                updateNotification(comp.name, "Downloading... $pct%")
                            }
                        }
                    }
                }
            }

            updateNotification(comp.name, "Extracting...")
            tmpDir.deleteRecursively(); tmpDir.mkdirs()
            extract(archive, tmpDir)

            // flatten: if archive has a single top folder, use its contents as root
            val destDir = File(WineEnv.rootDir(this), comp.installDir)
            destDir.deleteRecursively(); destDir.mkdirs()
            val kids = tmpDir.listFiles().orEmpty()
            val srcRoot = if (kids.size == 1 && kids[0].isDirectory) kids[0] else tmpDir
            srcRoot.listFiles().orEmpty().forEach { f ->
                if (!f.renameTo(File(destDir, f.name))) f.copyRecursively(File(destDir, f.name), true)
            }
            File(destDir, "bin").listFiles()?.forEach { it.setExecutable(true, false) }
            if (comp.id == "box64") File(destDir, "box64").setExecutable(true, false)

            broadcast(comp.id, 100, true, null)
            updateNotification(comp.name, "Done")
        } catch (e: Exception) {
            broadcast(comp.id, -1, false, e.message ?: e.javaClass.simpleName)
        } finally {
            archive.delete()
            tmpDir.deleteRecursively()
            if (active.decrementAndGet() <= 0) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    // ---- extraction in pure Kotlin (Android has no usable tar/xz/unzip binaries) ----
    private fun extract(archive: File, dest: File) {
        val name = archive.name
        if (name.endsWith(".zip")) { extractZip(archive, dest); return }

        val raw = BufferedInputStream(FileInputStream(archive), 64 * 1024)
        val decompressed: InputStream = when {
            name.endsWith(".tar.xz") || name.endsWith(".txz") -> XZCompressorInputStream(raw)
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> GzipCompressorInputStream(raw)
            name.endsWith(".tar.bz2") -> BZip2CompressorInputStream(raw)
            else -> { raw.close(); throw Exception("format arsip tidak dikenal: $name") }
        }
        TarArchiveInputStream(decompressed).use { tar ->
            var e: TarArchiveEntry? = tar.nextTarEntry
            while (e != null) {
                val out = safeFile(dest, e.name)
                when {
                    e.isDirectory -> out.mkdirs()
                    e.isSymbolicLink -> {
                        out.parentFile?.mkdirs()
                        try {
                            out.delete()
                            Files.createSymbolicLink(out.toPath(), java.nio.file.Paths.get(e.linkName))
                        } catch (_: Exception) { }
                    }
                    e.isFile -> {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { tar.copyTo(it, 64 * 1024) }
                        if (e.mode and 0b001001001 != 0) out.setExecutable(true, false)
                    }
                }
                e = tar.nextTarEntry
            }
        }
    }

    private fun extractZip(archive: File, dest: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val out = safeFile(dest, e.name)
                if (e.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zip.copyTo(it, 64 * 1024) }
                }
                e = zip.nextEntry
            }
        }
    }

    // zip-slip protection
    private fun safeFile(dest: File, entryName: String): File {
        val f = File(dest, entryName)
        if (!f.canonicalPath.startsWith(dest.canonicalPath + File.separator) && f.canonicalPath != dest.canonicalPath)
            throw Exception("entry arsip tidak aman: $entryName")
        return f
    }

    private fun broadcast(id: String, progress: Int, done: Boolean, error: String?) {
        sendBroadcast(Intent(ACTION_PROGRESS).apply {
            setPackage(packageName)
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
        getSystemService(NotificationManager::class.java).notify(1, buildNotification(title, text))
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
