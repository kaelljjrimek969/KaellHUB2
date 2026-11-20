package com.kaelltzyy.kaellhub.model

data class Game(
    val id: String,
    val name: String,
    val exePath: String,          // absolute path to .exe
    val iconPath: String? = null,
    val renderBackend: String = "auto",   // "auto" | "dxvk" | "virgl" | "zink"
    val renderScale: Float = -1f,         // -1 = use global setting
    val enableFsr: Boolean = true,
    val fsrSharpness: Float = 0.5f,
    val cpuAffinity: String = "",         // e.g. "4-7" for big cores only
    val extraArgs: String = "",
    val lastPlayed: Long = 0L,
    val isFavorite: Boolean = false
)
