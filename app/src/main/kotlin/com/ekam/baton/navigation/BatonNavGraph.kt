package com.ekam.baton.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.ekam.baton.feature.agents.AddEditAgentScreen
import com.ekam.baton.feature.agents.AgentsScreen
import com.ekam.baton.feature.agents.creator.BotCreatorScreen
import com.ekam.baton.feature.chat.ChatScreen
import com.ekam.baton.feature.chat.ChatsListScreen
import com.ekam.baton.feature.chat.GroupChatScreen
import com.ekam.baton.feature.chat.SwarmTopologyScreen
import com.ekam.baton.feature.memory.MemoryScreen
import com.ekam.baton.feature.settings.KeyBackupScreen
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
    object Chats : Screen("chats_root")

    /** Agents tab — agent management. */
    object Agents : Screen("agents_root") // Use nested graph route

    /** A2A tab — Agent-to-Agent protocol handshake. */
    object A2A : Screen("a2a_root")

    /** Settings tab — app configuration. */
    object Settings : Screen("settings")
}

// ─── Nav Graph ───────────────────────────────────────────────────────────────

/**
 * Root navigation graph for BATON.
 *
 * Hosts all four top-level tab destinations with a symmetric fade transition.
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
        navController = navController,
        startDestination = Screen.Chats.route,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy),
                initialOffsetX = { it / 4 }
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow))
        },
        exitTransition = {
            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
        },
        popEnterTransition = {
            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy),
                targetOffsetX = { it / 4 }
            ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
        },
    ) {
        // Chats nested graph
        navigation(startDestination = "chats_list", route = Screen.Chats.route) {
            composable("chats_list") {
                ChatsListScreen(
                    onNavigateToChat = { conversationId ->
                        navController.navigate("chats/$conversationId")
                    },
                    onNavigateToSwarm = { swarmId ->
                        navController.navigate("swarms/$swarmId")
                    },
                    onNavigateToSwarmTopology = { swarmId ->
                        navController.navigate("swarms/$swarmId/topology")
                    },
                    onNavigateToAgents = {
                        navController.navigate(Screen.Agents.route)
                    }
                )
            }
            composable(
                route = "swarms/{swarmId}",
                arguments = listOf(navArgument("swarmId") { type = NavType.StringType })
            ) { backStackEntry ->
                val swarmId = backStackEntry.arguments?.getString("swarmId") ?: ""
                GroupChatScreen(
                    swarmId = swarmId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToTopology = {
                        navController.navigate("swarms/$swarmId/topology")
                    }
                )
            }
            composable(
                route = "swarms/{swarmId}/topology",
                arguments = listOf(navArgument("swarmId") { type = NavType.StringType })
            ) { backStackEntry ->
                val swarmId = backStackEntry.arguments?.getString("swarmId") ?: ""
                SwarmTopologyScreen(
                    swarmId = swarmId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAgentDetails = { agentId ->
                        navController.navigate("agents/details/$agentId")
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
                        onNavigateToAgentDetails = { agentId ->
                            navController.navigate("agents/details/$agentId")
                        }
                    )
                }
            }
            composable(
                route = "agents/details/{agentId}",
                arguments = listOf(navArgument("agentId") { type = NavType.StringType })
            ) { backStackEntry ->
                val agentId = backStackEntry.arguments?.getString("agentId")
                if (agentId != null) {
                    com.ekam.baton.feature.agents.AgentDetailsScreen(
                        agentId = agentId,
                        onNavigateBack = { navController.popBackStack() },
                        onEditClick = { id -> navController.navigate("agents/manager") },
                        onMemoryVaultClick = { id -> navController.navigate("memory") },
                        onNavigateToBotManager = { id -> navController.navigate("agents/manager") }
                    )
                }
            }
        }

        // Agents nested graph
        navigation(startDestination = "agents", route = Screen.Agents.route) {
            composable("agents") {
                AgentsScreen(
                    onAddAgentClick = { _, _, _ ->
                        navController.navigate("agents/creator")
                    },
                    onEditAgentClick = { agentId -> navController.navigate("agents/details/$agentId") },
                    onNavigateToCreator = { navController.navigate("agents/creator") },
                    onNavigateToManager = { navController.navigate("agents/manager") },
                    onNavigateToAdvanced = { navController.navigate("agents/advanced") }
                )
            }
            composable("agents/add") {
                BotCreatorScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChat = { conversationId -> navController.navigate("chats/$conversationId") },
                    onNavigateToManager = { navController.navigate("agents/manager") }
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

        composable("agents/manager") {
            com.ekam.baton.feature.agents.manager.BatonBotManagerScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { conversationId -> navController.navigate("chats/$conversationId") },
                onNavigateToCreator = { navController.navigate("agents/creator") }
            )
        }

        // Dedicated Bot Creator Screen
        composable("agents/creator") {
            BotCreatorScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { conversationId -> navController.navigate("chats/$conversationId") },
                onNavigateToManager = { navController.navigate("agents/manager") }
            )
        }

        composable("agents/botfather") {
            BotCreatorScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { conversationId -> navController.navigate("chats/$conversationId") },
                onNavigateToManager = { navController.navigate("agents/manager") }
            )
        }

        composable("agents/advanced") {
            com.ekam.baton.feature.agents.advanced.AdvancedConnectionScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTunnelSetup = { navController.navigate("agents/tunnel_setup") },
                onNavigateToAirGapQr = { navController.navigate("a2a/qr") },
                onNavigateToAirGapScanner = { navController.navigate("a2a/scanner") },
                onNavigateToA2A = { navController.navigate(Screen.A2A.route) },
                onNavigateToSwarmTopology = { swarmId -> navController.navigate("swarms/$swarmId/topology") },
                onNavigateToKeyBackup = { navController.navigate("settings/key_backup") }
            )
        }

        composable(Screen.A2A.route) {
            com.ekam.baton.feature.agents.a2a.A2AScreen(
                onNavigateToQr = { navController.navigate("a2a/qr") },
                onNavigateToScanner = { navController.navigate("a2a/scanner") },
                onNavigateToSwarm = { swarmId -> navController.navigate("swarms/$swarmId") }
            )
        }

        composable("a2a/qr") {
            com.ekam.baton.feature.agents.a2a.AirGapQrScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToScanner = { navController.navigate("a2a/scanner") }
            )
        }

        composable("a2a/scanner") {
            com.ekam.baton.feature.agents.a2a.AirGapScannerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("memory") {
            MemoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToTunnelSetup = {
                    navController.navigate("agents/tunnel_setup")
                },
                onNavigateToMemory = {
                    navController.navigate("memory")
                },
                onNavigateToKeyBackup = {
                    navController.navigate("settings/key_backup")
                },
                onNavigateToCloudBackup = {
                    navController.navigate("settings/cloud_backup")
                },
                onNavigateToExecutionLedger = {
                    navController.navigate("settings/execution_ledger")
                }
            )
        }

        composable("settings/key_backup") {
            KeyBackupScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings/cloud_backup") {
            com.ekam.baton.feature.settings.CloudBackupScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings/execution_ledger") {
            com.ekam.baton.feature.settings.ExecutionLedgerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
