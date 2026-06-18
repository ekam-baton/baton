package com.ekam.baton

import android.os.Bundle

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ekam.baton.core.ui.theme.BatonTheme
import com.ekam.baton.navigation.BatonBottomBar
import com.ekam.baton.navigation.BatonNavGraph
import dagger.hilt.android.AndroidEntryPoint

/**
 * BATON's single Activity.
 *
 * Responsibilities:
 * - Enable edge-to-edge display via [enableEdgeToEdge] (sets
 *   [WindowCompat.setDecorFitsSystemWindows] = false and makes status/nav bars
 *   transparent internally — no manual WindowCompat call needed).
 * - Bootstrap Compose content inside [BatonTheme].
 * - Host the [Scaffold] that wires [BatonBottomBar] and [BatonNavGraph].
 *
 * The [Scaffold] automatically distributes [WindowInsets] between the bottom bar
 * (which claims the system navigation bar inset) and the content area (which
 * receives the remaining top inset). Content is padded with [innerPadding] so
 * nothing hides behind either bar.
 */
import androidx.appcompat.app.AppCompatActivity
import com.ekam.baton.core.data.preferences.AppPreferences
import javax.inject.Inject
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * BATON's single Activity.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var appLockObserver: AppLockObserver

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by appPreferences.themeMode.collectAsState(initial = "system")
            val accentColor by appPreferences.accentColor.collectAsState(initial = 0xFF9D65FF)
            
            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            BatonTheme(
                darkTheme = isDark,
                accentColor = Color(accentColor)
            ) {
                val navController = rememberNavController()
                BatonAppShell(navController = navController)
            }
        }
    }
}

/**
 * Root composable that assembles the Scaffold shell.
 *
 * Separated from [MainActivity.onCreate] so it can be previewed and tested
 * independently without a real Activity context.
 *
 * @param navController  The app-level [NavHostController] shared between
 *                       [BatonBottomBar] and [BatonNavGraph].
 */
@Composable
internal fun BatonAppShell(navController: NavHostController) {
    // Observe the active back-stack entry to derive the current route.
    // Recomposition is triggered automatically whenever the destination changes.
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier       = Modifier.fillMaxSize(),
        containerColor = Color(0xFF070B14), // BatonBackground — no recomposition cost
        bottomBar = {
            BatonBottomBar(
                navController = navController,
                currentRoute  = currentRoute,
            )
        },
    ) { innerPadding ->
        // innerPadding accounts for the top status bar + bottom nav bar height
        // (including system gesture insets). Passing it to BatonNavGraph ensures
        // no content is clipped behind either bar.
        BatonNavGraph(
            navController = navController,
            modifier      = Modifier.padding(innerPadding),
        )
    }
}
