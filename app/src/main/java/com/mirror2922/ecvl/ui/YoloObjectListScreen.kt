package com.mirror2922.ecvl.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mirror2922.ecvl.viewmodel.BeautyViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun YoloObjectListScreen(navController: NavController, viewModel: BeautyViewModel) {
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detection Classes") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Classes") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Active Categories", style = MaterialTheme.typography.titleMedium)
            
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.selectedYoloClasses.forEach { className ->
                    InputChip(
                        selected = true,
                        onClick = { viewModel.toggleYoloClass(className) },
                        label = { Text(className) },
                        trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            val filteredClasses = viewModel.allCOCOClasses.filter { 
                it.contains(searchQuery, ignoreCase = true) && !viewModel.selectedYoloClasses.contains(it) 
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filteredClasses) { className ->
                    ListItem(
                        headlineContent = { Text(className) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingContent = {
                            Button(onClick = { viewModel.toggleYoloClass(className) }) {
                                Text("Add")
                            }
                        }
                    )
                }
            }
        }
    }
}
