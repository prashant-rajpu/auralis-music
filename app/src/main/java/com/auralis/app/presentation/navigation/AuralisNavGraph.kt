package com.auralis.app.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.auralis.app.presentation.artist.ArtistProfileScreen
import com.auralis.app.presentation.explore.ExploreScreen
import com.auralis.app.presentation.home.HomeScreen
import com.auralis.app.presentation.library.LibraryScreen
import com.auralis.app.presentation.player.MiniPlayer
import com.auralis.app.presentation.player.PlayerSheet
import com.auralis.app.presentation.player.PlayerSheetValue
import com.auralis.app.presentation.player.PlayerViewModel
import com.auralis.app.presentation.player.rememberExpandPlayer
import com.auralis.app.presentation.player.rememberPlayerSheetState
import com.auralis.app.presentation.settings.SettingsScreen
import com.auralis.app.presentation.call.CallOverlay
import com.auralis.app.presentation.together.TogetherScreen
import com.auralis.app.together.Invite
import com.auralis.app.ui.theme.*

/** Heights reserved under the tab content so the bottom chrome never covers the end of a list. */
private val ChromeInsetWithPlayer = 152.dp
private val ChromeInsetBarOnly = 76.dp

@Composable
fun AuralisNavGraph(
    pendingInvite: Invite? = null,
    onInviteHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Settings and the artist page are their own full-screen destinations with their own back
    // affordance; the bottom chrome would only get in their way.
    val showsBottomChrome = currentRoute == null ||
        currentRoute == "home" || currentRoute == "explore" ||
        currentRoute == "library" || currentRoute == "together"

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val currentTrack by playerViewModel.currentTrack.collectAsState()
    val hasTrack = currentTrack != null

    val sheetState = rememberPlayerSheetState()
    val expandPlayer = rememberExpandPlayer(sheetState)

    // Leaving the tabs removes the sheet from the tree, which would otherwise strand it half open.
    LaunchedEffect(showsBottomChrome) {
        if (!showsBottomChrome && sheetState.anchors.size > 0) {
            sheetState.snapTo(PlayerSheetValue.Collapsed)
        }
    }
    // A tapped invite should land on the session, not wherever the app happened to be.
    LaunchedEffect(pendingInvite) {
        if (pendingInvite != null && currentRoute != "together") {
            navController.navigate("together") { launchSingleTop = true }
        }
    }

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBrush)
    ) {
        val containerHeightPx = with(density) { maxHeight.toPx() }

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    bottom = when {
                        !showsBottomChrome -> 0.dp
                        hasTrack -> ChromeInsetWithPlayer
                        else -> ChromeInsetBarOnly
                    }
                )
        ) {
            composable("home") {
                HomeScreen(
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToArtist = { artist -> navController.navigate("artist/${Uri.encode(artist)}") }
                )
            }

            composable("explore") {
                ExploreScreen(
                    onNavigateToArtist = { artist -> navController.navigate("artist/${Uri.encode(artist)}") }
                )
            }

            composable("library") {
                LibraryScreen(
                    onNavigateToArtist = { artist -> navController.navigate("artist/${Uri.encode(artist)}") }
                )
            }

            composable("together") {
                TogetherScreen(
                    pendingInvite = pendingInvite,
                    onInviteHandled = onInviteHandled,
                )
            }

            composable("settings") {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "artist/{artistName}",
                arguments = listOf(navArgument("artistName") { type = NavType.StringType })
            ) {
                ArtistProfileScreen(onNavigateBack = { navController.popBackStack() })
            }
        }

        if (showsBottomChrome) {
            PlayerSheet(
                state = sheetState,
                containerHeightPx = containerHeightPx,
                hasTrack = hasTrack,
                onNavigateToArtist = { artist -> navController.navigate("artist/${Uri.encode(artist)}") }
            ) { dragModifier ->
                MiniPlayer(
                    modifier = dragModifier,
                    viewModel = playerViewModel,
                    onNavigateToFullPlayer = expandPlayer
                )

                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (currentRoute != route) {
                            navController.navigate(route) {
                                popUpTo("home") { inclusive = route == "home" }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }

        // Last, so it is over everything including the player sheet. A call is the one thing in
        // this app that should not be behind anything else. It draws nothing when idle.
        CallOverlay()
    }
}

@Composable
private fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp, top = 2.dp)
            .surfacePanel(cornerRadius = 28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavPillItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = currentRoute == "home" || currentRoute == null,
                onClick = { onNavigate("home") }
            )
            NavPillItem(
                icon = Icons.Default.Explore,
                label = "Explore",
                isSelected = currentRoute == "explore",
                onClick = { onNavigate("explore") }
            )
            NavPillItem(
                icon = Icons.Default.LibraryMusic,
                label = "Library",
                isSelected = currentRoute == "library",
                onClick = { onNavigate("library") }
            )
            NavPillItem(
                icon = Icons.Default.Favorite,
                label = "Together",
                isSelected = currentRoute == "together",
                onClick = { onNavigate("together") }
            )
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
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) AccentColor else TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) AccentColor else TextSecondary
        )
        Spacer(modifier = Modifier.height(3.dp))
        // Active indicator dot
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (isSelected) AccentColor else Color.Transparent)
        )
    }
}
