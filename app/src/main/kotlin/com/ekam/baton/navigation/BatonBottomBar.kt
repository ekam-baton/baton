package com.ekam.baton.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navOptions
import com.ekam.baton.ui.walkthrough.WalkthroughState

// ─── Model ───────────────────────────────────────────────────────────────────

/**
 * Describes a single item in the BATON bottom navigation bar.
 *
 * @param route              Navigation route matching a [Screen] constant.
 * @param icon               Outlined icon vector for this tab.
 * @param contentDescription Accessibility label (tab name) — no visible label is rendered.
 */
data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val contentDescription: String,
)

/** Ordered list of bottom-nav tabs (left → right). */
val bottomNavItems: List<BottomNavItem> = listOf(
    BottomNavItem(
        route = Screen.Chats.route,
        icon = Icons.Outlined.Forum,
        contentDescription = "Chats",
    ),
    BottomNavItem(
        route = Screen.Agents.route,
        icon = Icons.Outlined.SmartToy,
        contentDescription = "Agents",
    ),
    BottomNavItem(
        route = Screen.A2A.route,
        icon = Icons.AutoMirrored.Outlined.CompareArrows,
        contentDescription = "A2A Handshake",
    ),
    BottomNavItem(
        route = Screen.Settings.route,
        icon = Icons.Outlined.Settings,
        contentDescription = "Settings",
    ),
)

// ─── Colors ──────────────────────────────────────────────────────────────────

private val NavBarContainer = Color(0xFF0F1623) // BatonSurface
private val NavBarSelected = Color(0xFF3D8EFF) // BatonElectric
private val NavBarIndicator = Color(0xFF3D8EFF).copy(alpha = 0.16f) // subtle pill glow
private val NavBarUnselected = Color.White.copy(alpha = 0.60f) // 60% white

// ─── Composable ──────────────────────────────────────────────────────────────

/**
 * BATON bottom navigation bar.
 *
 * Renders 4 icon-only tabs (no visible labels). The active tab's icon is
 * colored [NavBarSelected] (#3D8EFF) inside a translucent electric-blue
 * indicator pill. Inactive icons are 60% white. Tab presses navigate with
 * [launchSingleTop] and back-stack state save/restore so scroll positions
 * are preserved when returning to a tab.
 *
 * The [NavigationBar] draws behind the system navigation bar automatically via
 * [NavigationBarDefaults.windowInsets] — no extra padding needed here.
 *
 * @param navController  App-level nav controller.
 */
@Composable
fun BatonBottomBar(
    navController: NavController,
    walkthroughState: WalkthroughState? = null
) {
    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = NavBarContainer,
        tonalElevation = 0.dp, // suppress M3 tonal surface overlay; color is explicit
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true

            NavigationBarItem(
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    walkthroughState?.updateElementBounds(item.contentDescription, coordinates.boundsInWindow())
                },
                selected = selected,
                onClick = {
                    // Guard against re-navigating to the already-visible destination
                    if (!selected) {
                        navController.navigate(
                            route = item.route,
                            navOptions = navOptions {
                                // Pop back to start destination so tab switches never
                                // accumulate a deep back stack.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            },
                        )
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.contentDescription,
                    )
                },
                // No visible label — contentDescription handles accessibility
                label = null,
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NavBarSelected,
                    unselectedIconColor = NavBarUnselected,
                    indicatorColor = NavBarIndicator,
                ),
            )
        }
    }
}
