package com.iqforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Placeholder entry point — proves the module builds and boots.
 * The real chat-feed UI (see BUILD_PLAN.md) replaces this next.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold { padding ->
                    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                        Text("iQForge", style = MaterialTheme.typography.headlineMedium)
                        Text("Clone. Code. Review. Ship. From your phone.")
                    }
                }
            }
        }
    }
}
