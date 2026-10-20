package com.luisfelipe.aquaticks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.luisfelipe.aquaticks.ui.AquaTicksScreen
import com.luisfelipe.aquaticks.ui.WaterViewModel

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[WaterViewModel::class.java] }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AquaTicksScreen(model) }
        if (savedInstanceState == null) consumeIntent(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }
    private fun consumeIntent(intent: Intent) {
        // Only a tile's explicit EXTRA_ADD request records water. Opening the app is read-only.
        if (intent.getBooleanExtra(EXTRA_ADD, false)) {
            intent.removeExtra(EXTRA_ADD)
            val operation = intent.getStringExtra(EXTRA_OPERATION) ?: java.util.UUID.randomUUID().toString()
            val amount = intent.getIntExtra(EXTRA_AMOUNT, 250)
            model.add(amount, operation)
        }
    }
    companion object {
        const val EXTRA_ADD = "add_water"
        const val EXTRA_OPERATION = "operation_id"
        const val EXTRA_AMOUNT = "amount"
    }
}
