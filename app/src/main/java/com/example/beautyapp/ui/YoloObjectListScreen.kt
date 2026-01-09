package com.example.beautyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.beautyapp.viewmodel.BeautyViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun YoloObjectListScreen(navController: NavController, viewModel: BeautyViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var isSuggestionsExpanded by remember { mutableStateOf(false) }

    val filteredSuggestions = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else viewModel.allCOCOClasses.filter { 
            it.contains(searchQuery, ignoreCase = true) && !viewModel.selectedYoloClasses.contains(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detection Objects") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        // 使用 LazyColumn 确保即使 Chip 非常多也能顺畅滚动
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                
                // 搜索输入框
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { 
                            searchQuery = it
                            isSuggestionsExpanded = it.isNotBlank()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search to add object") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = ""; isSuggestionsExpanded = false }) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        }
                    )

                    DropdownMenu(
                        expanded = isSuggestionsExpanded && filteredSuggestions.isNotEmpty(),
                        onDismissRequest = { isSuggestionsExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        filteredSuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = {
                                    viewModel.toggleYoloClass(suggestion)
                                    searchQuery = ""
                                    isSuggestionsExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Selected Objects (${viewModel.selectedYoloClasses.size})", style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = { 
                            viewModel.selectedYoloClasses.clear()
                            viewModel.selectedYoloClasses.addAll(viewModel.allCOCOClasses) 
                        }
                    ) {
                        Text("Reset All")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 将 FlowRow 放在一个 item 中，由于它包裹在 LazyColumn 里，可以整体滚动
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.selectedYoloClasses.forEach { item ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.toggleYoloClass(item) },
                            label = { Text(item) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}