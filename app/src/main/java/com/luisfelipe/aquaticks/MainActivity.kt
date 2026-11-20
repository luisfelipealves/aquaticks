package com.luisfelipe.aquaticks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.luisfelipe.aquaticks.ui.AquaTicksScreen
import com.luisfelipe.aquaticks.ui.WaterViewModel

class MainActivity : ComponentActivity() {
    private lateinit var model: WaterViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[WaterViewModel::class.java]
        setContent { AquaTicksScreen(model) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        model.openHome()
    }
}
