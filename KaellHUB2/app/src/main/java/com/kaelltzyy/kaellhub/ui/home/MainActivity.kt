package com.kaelltzyy.kaellhub.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.kaelltzyy.kaellhub.R
import com.kaelltzyy.kaellhub.core.gpu.GpuDetector
import com.kaelltzyy.kaellhub.core.perf.PerfMonitor
import com.kaelltzyy.kaellhub.core.wine.WineEnv
import com.kaelltzyy.kaellhub.databinding.ActivityMainBinding
import com.kaelltzyy.kaellhub.model.Game
import com.kaelltzyy.kaellhub.ui.components.ComponentsActivity
import com.kaelltzyy.kaellhub.ui.launch.LaunchActivity
import com.kaelltzyy.kaellhub.ui.settings.SettingsActivity
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: GameAdapter
    private val games = mutableListOf<Game>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)

        adapter = GameAdapter(
            onLaunch = { game ->
                startActivity(Intent(this, LaunchActivity::class.java).apply {
                    putExtra(LaunchActivity.EXTRA_GAME_ID, game.id)
                    putExtra(LaunchActivity.EXTRA_EXE_PATH, game.exePath)
                })
            },
            onSettings = { game ->
                // per-game settings — future: open GameSettingsBottomSheet
            }
        )

        b.rvGames.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
        }

        b.fabAddGame.setOnClickListener { pickExe() }
        b.btnComponents.setOnClickListener {
            startActivity(Intent(this, ComponentsActivity::class.java))
        }

        detectGpu()
        loadGames()
        startPerfMonitor()
    }

    private fun detectGpu() {
        lifecycleScope.launch {
            val gpu = GpuDetector.detect(this@MainActivity)
            b.tvGpuInfo.text = "${gpu.renderer} · ${gpu.recommendedBackend.uppercase()}"
            b.tvGpuInfo.setTextColor(
                if (gpu.supportsFrameGen) getColor(R.color.accent_green)
                else getColor(R.color.accent_purple)
            )
        }
    }

    private fun loadGames() {
        // load from SharedPreferences — simple JSON list
        val prefs = getSharedPreferences("kaellhub", MODE_PRIVATE)
        val raw = prefs.getString("games", "[]") ?: "[]"
        // parse minimal — prod would use Gson/Moshi
        b.tvEmptyState.visibility = if (games.isEmpty())
            android.view.View.VISIBLE else android.view.View.GONE
        adapter.submitList(games.toList())
    }

    private fun startPerfMonitor() {
        lifecycleScope.launch {
            PerfMonitor.snapshots(this@MainActivity, 2000L).collect { snap ->
                b.tvPerfBar.text = buildString {
                    if (snap.fpsEstimate > 0) append("${snap.fpsEstimate}fps  ")
                    append("CPU ${snap.cpuPercent}%  ")
                    append("RAM ${snap.ramUsedMb}/${snap.ramTotalMb}MB")
                    if (snap.gpuFreqMhz > 0) append("  GPU ${snap.gpuFreqMhz}MHz")
                    if (snap.cpuTempC > 0f) append("  ${snap.cpuTempC.toInt()}°C")
                }
            }
        }
    }

    private fun pickExe() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQ_PICK_EXE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_EXE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val path = uri.path ?: return
            val name = File(path).nameWithoutExtension
            val game = Game(
                id = System.currentTimeMillis().toString(),
                name = name,
                exePath = path
            )
            games.add(game)
            adapter.submitList(games.toList())
            b.tvEmptyState.visibility = android.view.View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object { const val REQ_PICK_EXE = 101 }
}
