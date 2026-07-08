package com.ekam.baton.feature.agents.a2a

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ekam.baton.core.data.repository.WorldRoomRepository
import com.ekam.baton.feature.agents.a2a.world.WorldCanvas
import org.koin.compose.koinInject

/**
 * Hosts the full-screen 3D isometric world for the agents.
 * 
 * Provides an overlay for debugging or adding new agents to the world manually.
 */
@Composable
fun WorldScreen(
    viewModel: A2AViewModel,
    repository: WorldRoomRepository = koinInject(),
    modifier: Modifier = Modifier,
    onAddAgentClick: () -> Unit = {}
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0D0D14), // Dark terminal background
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddAgentClick,
                containerColor = Color(0xFF333344),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Person, contentDescription = "Add Agent to World")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            WorldCanvas(
                repository = repository,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
