package com.auralis.app.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.auralis.app.presentation.home.HomeScreen
import com.auralis.app.presentation.home.HomeTab
import com.auralis.app.presentation.home.HomeViewModel
import com.auralis.app.presentation.player.FullPlayerScreen
import com.auralis.app.presentation.player.MiniPlayer
import com.auralis.app.ui.theme.YtMusicBlack
import com.auralis.app.ui.theme.YtMusicTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuralisNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isFullPlayer = currentRoute == "player"

    Scaffold(
        containerColor = YtMusicBlack,
        bottomBar = {
            if (!isFullPlayer) {
                Column(modifier = Modifier.background(YtMusicBlack)) {
                    MiniPlayer(
                        onNavigateToFullPlayer = { navController.navigate("player") }
                    )
                    NavigationBar(
                        containerColor = YtMusicBlack,
                        tonalElevation = 0.dp
                    ) {
                        val isHome = currentRoute == "home" || currentRoute == null
                        val isExplore = currentRoute == "explore"
                        val isLibrary = currentRoute == "library"

                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home", fontSize = 11.sp) },
                            selected = isHome,
                            onClick = {
                                if (currentRoute != "home") {
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = YtMusicTextSecondary,
                                unselectedTextColor = YtMusicTextSecondary,
                                indicatorColor = Color.Transparent
                            )
                        )

                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Explore, contentDescription = "Explore") },
                            label = { Text("Explore", fontSize = 11.sp) },
                            selected = isExplore,
                            onClick = {
                                if (currentRoute != "explore") {
                                    navController.navigate("explore") {
                                        popUpTo("home")
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = YtMusicTextSecondary,
                                unselectedTextColor = YtMusicTextSecondary,
                                indicatorColor = Color.Transparent
                            )
                        )

                        NavigationBarItem(
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                            label = { Text("Library", fontSize = 11.sp) },
                            selected = isLibrary,
                            onClick = {
                                if (currentRoute != "library") {
                                    navController.navigate("library") {
                                        popUpTo("home")
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = YtMusicTextSecondary,
                                unselectedTextColor = YtMusicTextSecondary,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("home") {
                val viewModel: HomeViewModel = hiltViewModel()
                HomeScreen(
                    viewModel = viewModel,
                    onTrackClick = { track ->
                        viewModel.playTrack(track)
                    }
                )
            }

            composable("explore") {
                val viewModel: HomeViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    viewModel.selectMood("Energize")
                }
                HomeScreen(
                    viewModel = viewModel,
                    onTrackClick = { track ->
                        viewModel.playTrack(track)
                    }
                )
            }

            composable("library") {
                val viewModel: HomeViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    viewModel.selectTab(HomeTab.Downloaded)
                }
                HomeScreen(
                    viewModel = viewModel,
                    onTrackClick = { track ->
                        viewModel.playTrack(track)
                    }
                )
            }

            composable("player") {
                FullPlayerScreen(
                    onNavigateUp = { navController.popBackStack() }
                )
            }
        }
    }
}
