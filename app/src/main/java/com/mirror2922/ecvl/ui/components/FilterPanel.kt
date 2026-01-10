package com.mirror2922.ecvl.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirror2922.ecvl.viewmodel.BeautyViewModel

@Composable
fun FilterPanel(viewModel: BeautyViewModel, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = viewModel.showFilterPanel,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Text(
                    "Video Effects",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 20.dp, bottom = 16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                // Alignment: contentPadding starts at 20.dp to match Title
                // clipToPadding is true by default, causing the items to clip AT the 20.dp mark
                LazyRow(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(viewModel.filters) { filter ->
                        FilterItem(
                            name = filter,
                            isSelected = viewModel.selectedFilter == filter,
                            onClick = { 
                                viewModel.selectedFilter = filter
                                com.mirror2922.ecvl.NativeLib().updateNativeConfig(
                                    if (viewModel.currentMode == com.mirror2922.ecvl.viewmodel.AppMode.AI) 1 
                                    else if (viewModel.currentMode == com.mirror2922.ecvl.viewmodel.AppMode.FACE) 2 
                                    else 0,
                                    filter
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (name) {
        "Normal" -> Icons.Default.Block
        "Beauty" -> Icons.Default.Face
        "Dehaze" -> Icons.Default.CloudQueue
        "Underwater" -> Icons.Default.Waves
        "Stage" -> Icons.Default.TheaterComedy
        "Gray" -> Icons.Default.FilterBAndW
        "Histogram" -> Icons.Default.Contrast
        "Binary" -> Icons.Default.InvertColors
        "Morph Open" -> Icons.Default.PhotoFilter
        "Morph Close" -> Icons.Default.Gradient
        "Blur" -> Icons.Default.BlurOn
        else -> Icons.Default.AutoFixNormal
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp) // Slightly tighter width for better density
            .clickable(onClick = onClick)
    ) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.size(56.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
