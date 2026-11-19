package com.kaelltzyy.kaellhub.core.gpu

import android.content.Context
import android.opengl.EGL14
import android.opengl.GLES20
import java.io.File

enum class GpuVendor { ADRENO, MALI, POWERVR, UNKNOWN }

data class GpuInfo(
    val vendor: GpuVendor,
    val renderer: String,
    val supportsFrameGen: Boolean,   // Adreno 600+ only
    val supportsVulkan: Boolean,
    val recommendedBackend: String,  // "dxvk" | "virgl" | "zink"
    val recommendedRenderScale: Float
)

object GpuDetector {

    fun detect(context: Context): GpuInfo {
        val renderer = readRenderer()
        val vendor = classifyVendor(renderer)
        val adreno6xx = vendor == GpuVendor.ADRENO && extractAdrenoGen(renderer) >= 600
        val vulkan = hasVulkan(context)

        return GpuInfo(
            vendor = vendor,
            renderer = renderer,
            supportsFrameGen = adreno6xx && vulkan,
            supportsVulkan = vulkan,
            recommendedBackend = when {
                vendor == GpuVendor.ADRENO && vulkan -> "dxvk"
                vulkan -> "zink"      // Mali/PowerVR with Vulkan — use Zink (OpenGL over Vulkan)
                else -> "virgl"       // OpenGL fallback
            },
            recommendedRenderScale = when {
                adreno6xx -> 1.0f
                vendor == GpuVendor.ADRENO -> 0.8f
                vendor == GpuVendor.MALI -> 0.7f   // G57 sits here
                else -> 0.6f
            }
        )
    }

    // read GL renderer string — works without EGL surface via /proc
    private fun readRenderer(): String {
        // try sysfs first — no context needed, works pre-EGL
        val sysfsPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/kernel/gpu/gpu_model",
            "/proc/pvr/version"
        )
        for (path in sysfsPaths) {
            val f = File(path)
            if (f.exists() && f.canRead()) {
                val txt = f.readText().trim()
                if (txt.isNotEmpty()) return txt
            }
        }
        // fallback: parse /proc/cpuinfo for GPU hints
        return try {
            File("/proc/cpuinfo").readText()
                .lineSequence()
                .firstOrNull { it.contains("Hardware", ignoreCase = true) }
                ?.substringAfter(":")?.trim() ?: "unknown"
        } catch (e: Exception) { "unknown" }
    }

    private fun classifyVendor(renderer: String): GpuVendor {
        val r = renderer.lowercase()
        return when {
            r.contains("adreno") || r.contains("kgsl") -> GpuVendor.ADRENO
            r.contains("mali") -> GpuVendor.MALI
            r.contains("powervr") || r.contains("pvr") -> GpuVendor.POWERVR
            else -> GpuVendor.UNKNOWN
        }
    }

    private fun extractAdrenoGen(renderer: String): Int {
        // "Adreno (TM) 650" → 650
        val match = Regex("""(\d{3,4})""").find(renderer) ?: return 0
        return match.value.toIntOrNull() ?: 0
    }

    private fun hasVulkan(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            pm.hasSystemFeature("android.hardware.vulkan.version")
        } catch (e: Exception) { false }
    }
}
