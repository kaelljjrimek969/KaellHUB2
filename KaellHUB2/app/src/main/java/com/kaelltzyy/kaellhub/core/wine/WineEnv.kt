package com.kaelltzyy.kaellhub.core.wine

import android.content.Context
import java.io.File

// All Wine runtime paths live here — one source of truth
object WineEnv {

    fun rootDir(context: Context): File =
        File(context.getExternalFilesDir(null), "kaellhub").also { it.mkdirs() }

    fun wineDir(context: Context)       = File(rootDir(context), "wine").also { it.mkdirs() }
    fun box64Dir(context: Context)      = File(rootDir(context), "box64").also { it.mkdirs() }
    fun dxvkDir(context: Context)       = File(rootDir(context), "dxvk").also { it.mkdirs() }
    fun virglDir(context: Context)      = File(rootDir(context), "virgl").also { it.mkdirs() }
    fun prefixDir(context: Context)     = File(rootDir(context), "prefix").also { it.mkdirs() }
    fun gamesDir(context: Context)      = File(rootDir(context), "games").also { it.mkdirs() }
    fun driversDir(context: Context)    = File(rootDir(context), "drivers").also { it.mkdirs() }

    fun wineServer(context: Context)    = File(wineDir(context), "bin/wineserver")
    fun wineLoader(context: Context)    = File(wineDir(context), "bin/wine64")
    fun box64Bin(context: Context)      = File(box64Dir(context), "box64")

    fun isWineInstalled(context: Context)  = wineLoader(context).exists()
    fun isBox64Installed(context: Context) = box64Bin(context).exists()
    fun isDxvkInstalled(context: Context)  = File(dxvkDir(context), "x64/d3d11.dll").exists()
    fun isVirglInstalled(context: Context) = File(virglDir(context), "libvirgl_test_server.so").exists()

    // build env map for ProcessBuilder
    fun buildEnv(context: Context, renderBackend: String, renderScale: Float): Map<String, String> {
        val root = rootDir(context)
        val wine = wineDir(context)
        val box64 = box64Dir(context)
        val prefix = prefixDir(context)

        val dxvkEnabled = renderBackend == "dxvk" && isDxvkInstalled(context)
        val virglEnabled = !dxvkEnabled && isVirglInstalled(context)

        val scalePct = (renderScale * 100).toInt()

        return buildMap {
            put("WINEPREFIX", prefix.absolutePath)
            put("WINEARCH", "win64")
            put("WINELOADER", wineLoader(context).absolutePath)
            put("WINESERVER", wineServer(context).absolutePath)
            put("PATH", "${wine.absolutePath}/bin:${box64.absolutePath}:${System.getenv("PATH") ?: ""}")
            put("LD_LIBRARY_PATH", "${wine.absolutePath}/lib:${wine.absolutePath}/lib64:${root.absolutePath}/lib")

            // Box64 tuning — optimized for Helio G99 big.LITTLE
            put("BOX64_DYNAREC_BIGBLOCK", "1")
            put("BOX64_DYNAREC_SAFEFLAGS", "1")
            put("BOX64_DYNAREC_STRONGMEM", "1")
            put("BOX64_LOG", "0")
            put("BOX64_NOBANNER", "1")

            // render backend
            if (dxvkEnabled) {
                put("DXVK_HUD", "0")           // no DXVK overlay — using our own HUD
                put("DXVK_STATE_CACHE", "1")
                put("DXVK_CONFIG_FILE", "${root.absolutePath}/dxvk.conf")
                put("WINEDLLOVERRIDES", "d3d11=n,b;d3d10core=n,b;d3d9=n,b;dxgi=n,b")
            } else if (virglEnabled) {
                put("LIBGL_DRIVERS_PATH", virglDir(context).absolutePath)
                put("GALLIUM_DRIVER", "virpipe")
                put("MESA_GL_VERSION_OVERRIDE", "4.5")
                put("MESA_GLSL_VERSION_OVERRIDE", "450")
            }

            // frame pacing — keep vsync tight
            put("__GL_SYNC_TO_VBLANK", "1")
            put("WINE_LARGE_ADDRESS_AWARE", "1")
            put("WINE_DISABLE_WRITE_WATCH", "1")   // perf: skip write-watch for clean games

            // render scale passed to FSR layer
            put("KAELL_RENDER_SCALE", scalePct.toString())
        }
    }
}
