package com.kaelltzyy.kaellhub.core.wine

import android.content.Context
import com.kaelltzyy.kaellhub.core.gpu.GpuDetector
import com.kaelltzyy.kaellhub.model.Game
import java.io.File

object WineLauncher {

    data class LaunchResult(val pid: Int, val error: String?)

    fun launch(context: Context, game: Game): LaunchResult {
        if (!WineEnv.isWineInstalled(context))
            return LaunchResult(-1, "Wine not installed — open Components to install")
        if (!WineEnv.isBox64Installed(context))
            return LaunchResult(-1, "Box64 not installed — open Components to install")

        val gpu = GpuDetector.detect(context)
        val backend = if (game.renderBackend == "auto") gpu.recommendedBackend else game.renderBackend
        val scale   = if (game.renderScale < 0f) gpu.recommendedRenderScale else game.renderScale

        val env = WineEnv.buildEnv(context, backend, scale)
        val exeFile = File(game.exePath)
        if (!exeFile.exists()) return LaunchResult(-1, "EXE not found: ${game.exePath}")

        val box64 = WineEnv.box64Bin(context).absolutePath
        val wine  = WineEnv.wineLoader(context).absolutePath

        val cmd = mutableListOf(box64, wine, game.exePath)
        if (game.extraArgs.isNotBlank())
            cmd += game.extraArgs.split(" ").filter { it.isNotBlank() }

        return try {
            val pb = ProcessBuilder(cmd).apply {
                environment().putAll(env)
                directory(exeFile.parentFile)
                redirectErrorStream(true)
            }
            // cpu affinity — bind to big cores if set
            if (game.cpuAffinity.isNotBlank()) {
                pb.command().addAll(0, listOf("taskset", "-c", game.cpuAffinity))
            }
            val proc = pb.start()
            LaunchResult(proc.pid(), null)
        } catch (e: Exception) {
            LaunchResult(-1, e.message)
        }
    }
}
