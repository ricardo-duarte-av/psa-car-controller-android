package pt.aguiarvieira.psacc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import pt.aguiarvieira.psacc.ui.app.AppViewModel
import pt.aguiarvieira.psacc.ui.app.StartState
import pt.aguiarvieira.psacc.ui.navigation.AppNavHost
import pt.aguiarvieira.psacc.ui.navigation.Routes
import pt.aguiarvieira.psacc.ui.theme.PsaccTheme
import pt.aguiarvieira.psacc.update.AppUpdates
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    @Inject
    lateinit var appUpdates: AppUpdates

    // Play's update screens report back here; a cancelled or failed one is offered again from the home banner.
    private val updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh launch: after a config change the intent is the same and was already handled.
        if (savedInstanceState == null) handleIntent(intent)
        appUpdates.attach(updateLauncher)
        setContent {
            PsaccTheme {
                val startState by appViewModel.startState.collectAsStateWithLifecycle()
                if (startState == StartState.Loading) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Box(contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                } else {
                    val navController = rememberNavController()
                    val config by appViewModel.config.collectAsStateWithLifecycle()
                    val requestedTab by appViewModel.requestedTab.collectAsStateWithLifecycle()
                    // A notification tap while e.g. Settings is open: return to the tabs first.
                    LaunchedEffect(requestedTab) {
                        if (requestedTab != null && config != null) {
                            navController.popBackStack(Routes.Home, inclusive = false)
                        }
                    }
                    // Disconnected from Settings → back to onboarding with a clean back stack.
                    LaunchedEffect(config, startState) {
                        if (config == null && navController.currentDestination?.hasRoute(Routes.Connect::class) == false) {
                            navController.navigate(Routes.Connect) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        }
                    }
                    AppNavHost(
                        startAtHome = startState == StartState.Home,
                        navController = navController,
                        requestedTab = requestedTab,
                        onRequestedTabShown = appViewModel::consumeRequestedTab,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appUpdates.refresh()
    }

    override fun onDestroy() {
        appUpdates.detach(updateLauncher)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN_TAB)?.let(appViewModel::requestTab)
    }

    companion object {
        const val EXTRA_OPEN_TAB = "pt.aguiarvieira.psacc.OPEN_TAB"
        const val TAB_CAR = "CAR"
        const val TAB_TRIPS = "TRIPS"
        const val TAB_CHARGING = "CHARGING"
    }
}
