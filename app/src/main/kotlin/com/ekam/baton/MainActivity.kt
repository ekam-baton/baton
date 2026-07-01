package com.ekam.baton

import android.os.Bundle

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ekam.baton.core.ui.theme.BatonTheme
import com.ekam.baton.navigation.BatonBottomBar
import com.ekam.baton.navigation.BatonNavGraph

import androidx.appcompat.app.AppCompatActivity
import com.ekam.baton.ui.auth.UpgradeScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.isSystemInDarkTheme
import org.koin.androidx.compose.koinViewModel
import com.ekam.baton.ui.auth.AuthViewModel
import com.ekam.baton.ui.auth.AuthState
import com.ekam.baton.ui.auth.SignupScreen
import com.ekam.baton.ui.auth.LoginScreen

import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {

    lateinit var appLockObserver: AppLockObserver

    private val mainViewModel: MainViewModel by viewModel()

    // Holds a deep-link URL to navigate to once the nav graph is ready
    private var pendingDeepLinkUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Extract deep link URL from baton://connect?url=... intent
        pendingDeepLinkUrl = extractBatonDeepLinkUrl(intent)

        setContent {
            val accentColor by mainViewModel.accentColor.collectAsStateWithLifecycle()

            BatonTheme(
                accentColor = Color(accentColor)
            ) {
                val navController = rememberNavController()
                // Navigate to Add Agent screen when the app is opened via deep link
                androidx.compose.runtime.LaunchedEffect(pendingDeepLinkUrl) {
                    pendingDeepLinkUrl?.let { url ->
                        val encoded = android.util.Base64.encodeToString(
                            url.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                        navController.navigate("agents/add?url=$encoded")
                        pendingDeepLinkUrl = null
                    }
                }
                BatonAppShell(
                    navController = navController,
                    mainViewModel = mainViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // Handle deep link if app is already running
        pendingDeepLinkUrl = extractBatonDeepLinkUrl(intent)
    }

    private fun extractBatonDeepLinkUrl(intent: android.content.Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme == "baton" && data.host == "connect") {
            return data.getQueryParameter("url")
        }
        return null
    }
}

@Composable
internal fun BatonAppShell(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    authViewModel: AuthViewModel = koinViewModel()
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val accessGranted by mainViewModel.isAccessGranted.collectAsStateWithLifecycle()
    
    var showSplash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        showSplash = false
    }

    if (showSplash) {
        com.ekam.baton.ui.auth.BatonSplashScreen()
        return
    }

    when (authState) {
        AuthState.Unregistered -> {
            SignupScreen(
                onSignupSuccess = { email, phone ->
                    authViewModel.register(email, phone)
                }
            )
        }
        AuthState.LoggedOut -> {
            LoginScreen(
                onLoginSuccess = {
                    authViewModel.login()
                }
            )
        }
        AuthState.LoggedIn -> {
            if (!accessGranted) {
                UpgradeScreen(
                    onUpgradeSuccess = {
                        // Handled globally via preferences updates
                    }
                )
            } else {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    modifier       = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF070B14),
                    bottomBar = {
                        BatonBottomBar(
                            navController = navController,
                            currentRoute  = currentRoute,
                        )
                    },
                ) { innerPadding ->
                    BatonNavGraph(
                        navController = navController,
                        modifier      = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding),
                    )
                }
            }
        }
    }
}
