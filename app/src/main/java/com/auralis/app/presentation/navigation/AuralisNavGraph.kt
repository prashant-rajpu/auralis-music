package com.auralis.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.auralis.app.presentation.home.HomeScreen

@Composable
fun AuralisNavGraph() {
    val navController = rememberNavController()
    
    androidx.compose.material3.Scaffold(
        bottomBar = {
            com.auralis.app.presentation.player.MiniPlayer(
                onNavigateToFullPlayer = { navController.navigate("player") }
            )
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = androidx.compose.foundation.layout.padding(paddingValues)
        ) {
            composable("home") {
                val viewModel: com.auralis.app.presentation.home.HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                HomeScreen(
                    viewModel = viewModel,
                    onTrackClick = { track ->
                        viewModel.playTrack(track)
                    }
                )
            }
            
            composable("player") {
                com.auralis.app.presentation.player.FullPlayerScreen(
                    onNavigateUp = { navController.popBackStack() }
                )
            }
        }
    }
}
