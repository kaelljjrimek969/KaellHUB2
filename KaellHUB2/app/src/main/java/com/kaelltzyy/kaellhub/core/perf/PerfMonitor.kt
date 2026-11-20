package com.kaelltzyy.kaellhub.core.perf

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class PerfSnapshot(
    val fpsEstimate: Int,         // estimated from frame timing
    val cpuPercent: Int,          // from /proc/stat
    val ramUsedMb: Int,
    val ramTotalMb: Int,
    val gpuFreqMhz: Int,          // from sysfs, 0 if unavailable
    val cpuTempC: Float           // from thermal zone, 0f if unavailable
)

object PerfMonitor {

    // emits a PerfSnapshot every intervalMs — collect in a coroutine
    fun snapshots(context: Context, intervalMs: Long = 1000L): Flow<PerfSnapshot> = flow {
        var prevIdle = 0L; var prevTotal = 0L
        while (true) {
            val (idle, total) = readCpuStat()
            val dTotal = total - prevTotal
            val dIdle = idle - prevIdle
            val cpuPct = if (dTotal > 0) ((dTotal - dIdle) * 100 / dTotal).toInt() else 0
            prevIdle = idle; prevTotal = total

            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val totalMb = (mem.totalMem / 1_048_576).toInt()
            val usedMb  = totalMb - (mem.availMem / 1_048_576).toInt()

            emit(PerfSnapshot(
                fpsEstimate  = estimateFps(),
                cpuPercent   = cpuPct,
                ramUsedMb    = usedMb,
                ramTotalMb   = totalMb,
                gpuFreqMhz   = readGpuFreq(),
                cpuTempC     = readCpuTemp()
            ))
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    private fun readCpuStat(): Pair<Long, Long> {
        return try {
            val line = File("/proc/stat").bufferedReader().readLine() ?: return 0L to 0L
            val parts = line.split(" ").drop(1).filter { it.isNotEmpty() }.map { it.toLong() }
            val idle = parts.getOrElse(3) { 0L }
            val total = parts.sum()
            idle to total
        } catch (e: Exception) { 0L to 0L }
    }

    private fun estimateFps(): Int {
        // rough estimate: read /proc/self/schedstat delta
        return try {
            val txt = File("/proc/self/schedstat").readText().trim().split(" ")
            val runTime = txt.getOrElse(0) { "0" }.toLong()
            // normalize to ~60fps range — not accurate but directional
            (60 - (runTime / 1_000_000L).coerceAtMost(60)).toInt()
        } catch (e: Exception) { 0 }
    }

    private fun readGpuFreq(): Int {
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/cur_pwrlevel",
            "/sys/kernel/gpu/gpu_clock",
            "/sys/devices/platform/mali/cur_freq"
        )
        for (p in paths) {
            try {
                val v = File(p).readText().trim().toLongOrNull() ?: continue
                // values can be Hz or MHz depending on driver
                return if (v > 1_000_000) (v / 1_000_000).toInt() else v.toInt()
            } catch (_: Exception) {}
        }
        return 0
    }

    private fun readCpuTemp(): Float {
        for (i in 0..9) {
            try {
                val t = File("/sys/class/thermal/thermal_zone$i/temp")
                    .readText().trim().toFloatOrNull() ?: continue
                val c = if (t > 1000) t / 1000f else t
                if (c in 20f..110f) return c
            } catch (_: Exception) {}
        }
        return 0f
    }
}
