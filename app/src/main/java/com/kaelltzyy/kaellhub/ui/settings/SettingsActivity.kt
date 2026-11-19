package com.kaelltzyy.kaellhub.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kaelltzyy.kaellhub.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySettingsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
