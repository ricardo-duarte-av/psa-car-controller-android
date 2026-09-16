package pt.aguiarvieira.psacc.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import pt.aguiarvieira.psacc.ui.feature.connect.ConnectScreen
import pt.aguiarvieira.psacc.ui.feature.home.HomeShell
import pt.aguiarvieira.psacc.ui.feature.settings.AboutScreen
import pt.aguiarvieira.psacc.ui.feature.settings.ChangelogScreen
import pt.aguiarvieira.psacc.ui.feature.settings.SettingsScreen

@Composable
fun AppNavHost(
    startAtHome: Boolean,
    navController: NavHostController,
    requestedTab: String? = null,
    onRequestedTabShown: () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = if (startAtHome) Routes.Home else Routes.Connect,
    ) {
        composable<Routes.Connect> {
            ConnectScreen(
                onConnected = {
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Connect) { inclusive = true }
                    }
                },
            )
        }

        composable<Routes.Home> {
            HomeShell(
                onOpenSettings = { navController.navigateSingleTop(Routes.Settings) },
                requestedTab = requestedTab,
                onRequestedTabShown = onRequestedTabShown,
            )
        }

        composable<Routes.Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenChangelog = { navController.navigateSingleTop(Routes.Changelog) },
                onOpenAbout = { navController.navigateSingleTop(Routes.About) },
            )
        }

        composable<Routes.Changelog> {
            ChangelogScreen(onBack = { navController.popBackStack() })
        }

        composable<Routes.About> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** Collapses rapid double-taps onto the existing entry instead of stacking duplicates. */
private fun NavHostController.navigateSingleTop(route: Any) {
    navigate(route) { launchSingleTop = true }
}
