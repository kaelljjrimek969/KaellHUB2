package com.kaelltzyy.kaellhub.ui.launch

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kaelltzyy.kaellhub.core.wine.WineLauncher
import com.kaelltzyy.kaellhub.databinding.ActivityLaunchBinding
import com.kaelltzyy.kaellhub.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LaunchActivity : AppCompatActivity() {

    private lateinit var b: ActivityLaunchBinding

    companion object {
        const val EXTRA_GAME_ID   = "game_id"
        const val EXTRA_EXE_PATH  = "exe_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLaunchBinding.inflate(layoutInflater)
        setContentView(b.root)

        val exePath = intent.getStringExtra(EXTRA_EXE_PATH) ?: run { finish(); return }
        val gameId  = intent.getStringExtra(EXTRA_GAME_ID) ?: "unknown"

        b.tvStatus.text = "Launching..."
        b.progressBar.isIndeterminate = true

        lifecycleScope.launch {
            val game = Game(id = gameId, name = "Game", exePath = exePath)
            val result = withContext(Dispatchers.IO) {
                WineLauncher.launch(this@LaunchActivity, game)
            }
            if (result.error != null) {
                b.tvStatus.text = "Error: ${result.error}"
                b.progressBar.isIndeterminate = false
                Toast.makeText(this@LaunchActivity, result.error, Toast.LENGTH_LONG).show()
            } else {
                b.tvStatus.text = "Running (PID ${result.pid})"
                b.progressBar.isIndeterminate = false
                b.progressBar.progress = 100
            }
        }
    }
}
