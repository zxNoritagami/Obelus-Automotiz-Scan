package com.obelus.presentation.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.presentation.viewmodel.SignalViewModel

@Composable
fun SignalListScreen(
    viewModel: SignalViewModel = hiltViewModel()
) {
    val allSignals by viewModel.allSignals.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                viewModel.searchSignals(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            label = { Text("Search Signals") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )

        // List
        LazyColumn {
            items(allSignals) { signal ->
                ListItem(
                    headlineContent = { Text(signal.name) },
                    supportingContent = { Text("ID: ${signal.canId}") },
                    trailingContent = {
                        IconButton(onClick = { viewModel.toggleFavorite(signal.id) }) {
                            Icon(
                                imageVector = if (signal.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (signal.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.clickable { /* Handle selection */ }
                )
                Divider()
            }
        }
    }
}
