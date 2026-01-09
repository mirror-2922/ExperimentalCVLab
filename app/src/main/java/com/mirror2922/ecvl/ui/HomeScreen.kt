package com.mirror2922.ecvl.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun HomeScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("BeautyApp Main Menu")
        Spacer(modifier = Modifier.height(20.dp))
        
        Button(onClick = { navController.navigate("camera") }) {
            Text("Open Camera & Process")
        }
        Spacer(modifier = Modifier.height(10.dp))
        
        Button(onClick = { navController.navigate("resolution") }) {
            Text("Settings (Resolution)")
        }
        Spacer(modifier = Modifier.height(10.dp))

        Button(onClick = { /* TODO: Implement other screens */ }) {
            Text("AI Features (Coming Soon)")
        }
    }
}
