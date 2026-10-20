package com.luisfelipe.aquaticks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.luisfelipe.aquaticks.ui.AquaTicksScreen
import com.luisfelipe.aquaticks.ui.WaterViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this)[WaterViewModel::class.java]
        setContent { AquaTicksScreen(model) }
    }
}
