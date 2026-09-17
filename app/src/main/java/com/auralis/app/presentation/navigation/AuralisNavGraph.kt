package com.auralis.app.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import com.auralis.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuralisNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isFullPlayer = currentRoute == "player"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BabyPinkBackgroundBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (!isFullPlayer) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MiniPlayer(
                            onNavigateToFullPlayer = { navController.navigate("player") }
                        )

                        // Floating Frosted Glass Pill Bottom Navigation Bar (Section 7 Spec)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp, top = 2.dp)
                                .glassPanel(cornerRadius = 28.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isHome = currentRoute == "home" || currentRoute == null
                                val isExplore = currentRoute == "explore"
                                val isLibrary = currentRoute == "library"

                                NavPillItem(
                                    icon = Icons.Default.Home,
                                    label = "Home",
                                    isSelected = isHome,
                                    onClick = {
                                        if (currentRoute != "home") {
                                            navController.navigate("home") {
                                                popUpTo("home") { inclusive = true }
                                            }
                                        }
                                    }
                                )

                                NavPillItem(
                                    icon = Icons.Default.Explore,
                                    label = "Explore",
                                    isSelected = isExplore,
                                    onClick = {
                                        if (currentRoute != "explore") {
                                            navController.navigate("explore") {
                                                popUpTo("home")
                                            }
                                        }
                                    }
                                )

                                NavPillItem(
                                    icon = Icons.Default.LibraryMusic,
                                    label = "Library",
                                    isSelected = isLibrary,
                                    onClick = {
                                        if (currentRoute != "library") {
                                            navController.navigate("library") {
                                                popUpTo("home")
                                            }
                                        }
                                    }
                                )
                            }
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
}

@Composable
private fun NavPillItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .hapticPress(scaleDown = 0.90f)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) BabyPinkPrimary else BabyPinkTextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) BabyPinkPrimary else BabyPinkTextSecondary
        )
        Spacer(modifier = Modifier.height(3.dp))
        // Active indicator dot (Section 7 Spec)
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (isSelected) BabyPinkPrimary else Color.Transparent)
        )
    }
}
