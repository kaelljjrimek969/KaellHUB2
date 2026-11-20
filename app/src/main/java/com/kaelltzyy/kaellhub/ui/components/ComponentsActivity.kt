package com.kaelltzyy.kaellhub.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.kaelltzyy.kaellhub.core.wine.ComponentCatalog
import com.kaelltzyy.kaellhub.core.wine.DownloadService
import com.kaelltzyy.kaellhub.core.wine.WineEnv
import com.kaelltzyy.kaellhub.databinding.ActivityComponentsBinding

class ComponentsActivity : AppCompatActivity() {

    private lateinit var b: ActivityComponentsBinding
    private lateinit var adapter: ComponentAdapter

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id   = intent.getStringExtra(DownloadService.EXTRA_COMPONENT) ?: return
            val pct  = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0)
            val done = intent.getBooleanExtra(DownloadService.EXTRA_DONE, false)
            val err  = intent.getStringExtra(DownloadService.EXTRA_ERROR)
            adapter.updateProgress(id, pct, done, err)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityComponentsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val components = ComponentCatalog.all
        adapter = ComponentAdapter(
            components = components,
            isInstalled = { comp ->
                when (comp.id) {
                    "wine"  -> WineEnv.isWineInstalled(this)
                    "box64" -> WineEnv.isBox64Installed(this)
                    "dxvk"  -> WineEnv.isDxvkInstalled(this)
                    "virgl" -> WineEnv.isVirglInstalled(this)
                    else    -> false
                }
            },
            onInstall = { comp ->
                startService(Intent(this, DownloadService::class.java).apply {
                    putExtra(DownloadService.EXTRA_COMPONENT_ID, comp.id)
                })
            }
        )

        b.rvComponents.layoutManager = LinearLayoutManager(this)
        b.rvComponents.adapter = adapter

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(progressReceiver, IntentFilter(DownloadService.ACTION_PROGRESS),
                Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(progressReceiver, IntentFilter(DownloadService.ACTION_PROGRESS))
        }
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
    override fun onDestroy() { unregisterReceiver(progressReceiver); super.onDestroy() }
}
