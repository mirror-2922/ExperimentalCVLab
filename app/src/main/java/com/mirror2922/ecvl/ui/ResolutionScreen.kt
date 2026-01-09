package com.mirror2922.ecvl.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun ResolutionScreen(navController: NavController, currentRes: String, onResSelected: (String) -> Unit) {
    var selectedRes by remember { mutableStateOf(currentRes) }
    val resolutions = listOf("640x480", "1280x720", "1920x1080")

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Select Resolution", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(20.dp))

        resolutions.forEach { res ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = (res == selectedRes),
                    onClick = { selectedRes = res }
                )
                Text(res)
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
        Button(onClick = {
            onResSelected(selectedRes)
            navController.popBackStack()
        }) {
            Text("Confirm")
        }
    }
}
