package pt.aguiarvieira.psacc

import android.os.Bundle
import androidx.activity.ComponentActivity
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
                    )
                }
            }
        }
    }
}
