package com.ekam.baton.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.ekam.baton.feature.agents.AgentsScreen
import com.ekam.baton.feature.agents.AddEditAgentScreen
import com.ekam.baton.feature.chat.ChatsListScreen
import com.ekam.baton.feature.chat.ChatScreen
import com.ekam.baton.feature.chat.CallScreen
import com.ekam.baton.feature.memory.MemoryScreen
import com.ekam.baton.feature.settings.SettingsScreen

// ─── Route Definitions ───────────────────────────────────────────────────────

/**
 * Sealed class representing every top-level destination in BATON.
 *
 * Each [Screen] maps to a composable destination in [BatonNavGraph].
 * As feature screens are implemented, the placeholder composable inside the
 * corresponding [NavHost] composable block is swapped for the real screen.
 */
sealed class Screen(val route: String) {
    /** Chats tab — the default landing screen. */
    object Chats    : Screen("chats_root")
    /** Agents tab — agent management. */
    object Agents   : Screen("agents_root") // Use nested graph route
    /** A2A tab — Agent-to-Agent protocol handshake. */
    object A2A      : Screen("a2a_root")
    /** Settings tab — app configuration. */
    object Settings : Screen("settings")
}

// ─── Animation Constants ─────────────────────────────────────────────────────

/** Duration for cross-fade transitions between top-level destinations. */
private const val TAB_FADE_MS = 200

// ─── Nav Graph ───────────────────────────────────────────────────────────────

/**
 * Root navigation graph for BATON.
 *
 * Hosts all four top-level tab destinations with a symmetric fade transition.
 * No slide animations are used between tabs — fades feel natural for a
 * bottom-navigation paradigm.
 *
 * @param navController  The [NavHostController] owned by [MainActivity].
 * @param modifier       Applied to the [NavHost] itself; typically carries
 *                       [Scaffold] inner padding so content doesn't hide
 *                       behind the navigation bar.
 */
@Composable
fun BatonNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Chats.route,
        modifier         = modifier,
        enterTransition    = { fadeIn(animationSpec  = tween(TAB_FADE_MS)) },
        exitTransition     = { fadeOut(animationSpec = tween(TAB_FADE_MS)) },
        popEnterTransition = { fadeIn(animationSpec  = tween(TAB_FADE_MS)) },
        popExitTransition  = { fadeOut(animationSpec = tween(TAB_FADE_MS)) },
    ) {
        // Chats nested graph
        navigation(startDestination = "chats_list", route = Screen.Chats.route) {
            composable("chats_list") {
                ChatsListScreen(
                    onNavigateToChat = { conversationId -> 
                        navController.navigate("chats/$conversationId") 
                    },
                    onNavigateToCall = { agentName ->
                        navController.navigate("call/$agentName")
                    }
                )
            }
            composable(
                route = "chats/{conversationId}",
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getString("conversationId")
                if (conversationId != null) {
                    ChatScreen(
                        conversationId = conversationId,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToMemory = { agentId -> 
                            navController.navigate("memory")
                        },
                        onNavigateToCall = { agentName ->
                            navController.navigate("call/$agentName")
                        }
                    )
                }
            }
            composable(
                route = "call/{agentName}",
                arguments = listOf(navArgument("agentName") { type = NavType.StringType })
            ) { backStackEntry ->
                val agentName = backStackEntry.arguments?.getString("agentName") ?: "Agent"
                CallScreen(
                    agentName = agentName,
                    onEndCall = { navController.popBackStack() }
                )
            }
        }
        
        // Agents nested graph
        navigation(startDestination = "agents", route = Screen.Agents.route) {
            composable("agents") {
                AgentsScreen(
                    onAddAgentClick = { url, name -> 
                        if (url != null && name != null) {
                            val encodedUrl = android.util.Base64.encodeToString(url.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                            val encodedName = android.util.Base64.encodeToString(name.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                            navController.navigate("agents/add?url=$encodedUrl&name=$encodedName")
                        } else {
                            navController.navigate("agents/add")
                        }
                    },
                    onEditAgentClick = { agentId -> navController.navigate("agents/edit/$agentId") }
                )
            }
            composable(
                route = "agents/add?url={url}&name={name}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { backStackEntry ->
                val discoveredUrl = backStackEntry.arguments?.getString("url")?.let { 
                    String(android.util.Base64.decode(it, android.util.Base64.URL_SAFE)) 
                }
                val discoveredName = backStackEntry.arguments?.getString("name")?.let { 
                    String(android.util.Base64.decode(it, android.util.Base64.URL_SAFE)) 
                }
                AddEditAgentScreen(
                    agentId = null,
                    discoveredUrl = discoveredUrl,
                    discoveredName = discoveredName,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "agents/edit/{agentId}",
                arguments = listOf(navArgument("agentId") { type = NavType.StringType })
            ) { backStackEntry ->
                val agentId = backStackEntry.arguments?.getString("agentId")
                AddEditAgentScreen(
                    agentId = agentId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        
        composable("agents/tunnel_setup") {
            com.ekam.baton.feature.agents.tunnel.TunnelSetupGuideScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.A2A.route) { 
            com.ekam.baton.feature.agents.a2a.A2AScreen() 
        }
        
        composable("memory") { MemoryScreen() }
        
        composable(Screen.Settings.route) { 
            SettingsScreen(
                onNavigateToTunnelSetup = {
                    navController.navigate("agents/tunnel_setup")
                },
                onNavigateToMemory = {
                    navController.navigate("memory")
                }
            ) 
        }
    }
}
