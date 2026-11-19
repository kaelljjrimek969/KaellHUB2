package com.kaelltzyy.kaellhub.core.wine

data class Component(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val installDir: String,   // relative to rootDir
    val required: Boolean,
    val version: String
)

object ComponentCatalog {
    val all = listOf(
        Component(
            id = "wine",
            name = "Wine 9.0 (ARM64)",
            description = "Windows compatibility layer — required to run .exe files",
            downloadUrl = "https://github.com/italoplk/wine-ge-arm64/releases/download/9.0/wine-ge-9.0-arm64.tar.xz",
            installDir = "wine",
            required = true,
            version = "9.0"
        ),
        Component(
            id = "box64",
            name = "Box64 0.3.2",
            description = "x86_64 → ARM64 binary translator — required for 64-bit games",
            downloadUrl = "https://github.com/ptitSeb/box64/releases/download/v0.3.2/box64-android-arm64.tar.gz",
            installDir = "box64",
            required = true,
            version = "0.3.2"
        ),
        Component(
            id = "dxvk",
            name = "DXVK 2.3.1 (Adreno)",
            description = "DirectX → Vulkan translation — best performance on Adreno GPUs",
            downloadUrl = "https://github.com/doitsujin/dxvk/releases/download/v2.3.1/dxvk-2.3.1.tar.gz",
            installDir = "dxvk",
            required = false,
            version = "2.3.1"
        ),
        Component(
            id = "virgl",
            name = "VirGL (Mali/all GPU)",
            description = "OpenGL over Vulkan — recommended for Mali GPUs",
            downloadUrl = "https://github.com/alexvorxx/VirGL-mesa-android/releases/download/v24.1/virgl-mesa-24.1-arm64.tar.gz",
            installDir = "virgl",
            required = false,
            version = "24.1"
        )
    )

    fun findById(id: String) = all.firstOrNull { it.id == id }
}
