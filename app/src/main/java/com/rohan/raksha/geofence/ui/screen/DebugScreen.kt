package com.rohan.raksha.geofence.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Debug Info") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Debug info will be available here.")
        }
    }
}
