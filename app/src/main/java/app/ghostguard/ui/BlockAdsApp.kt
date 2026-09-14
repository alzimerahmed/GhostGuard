package app.ghostguard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.ghostguard.ui.data.HomeAppKey
import app.ghostguard.ui.data.OnboardingKey
import app.ghostguard.ui.data.SplashKey
import app.ghostguard.ui.dialog.VPNConflictDialog
import app.ghostguard.ui.onboarding.OnboardingScreen
import app.ghostguard.ui.splash.SplashScreen


@Composable
fun BlockAdsApp(
    modifier: Modifier = Modifier,
    onRequestVpnPermission: () -> Unit,
    showVpnConflictDialog: Boolean = false,
    onDismissVpnConflictDialog: () -> Unit = {},
    onShowVpnConflictDialog: () -> Unit = {},
) {

    if (showVpnConflictDialog) {
        VPNConflictDialog(
            onDismissVpnConflictDialog = onDismissVpnConflictDialog,
        )
    }

    val backStack = rememberNavBackStack(SplashKey)
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        modifier = modifier,
        entryProvider = entryProvider {
            entry<SplashKey> {
                SplashScreen(
                    onNavigateToHome = {
                        backStack.add(HomeAppKey)
                        backStack.remove(SplashKey)
                    },
                    onNavigateToOnboarding = {
                        backStack.add(OnboardingKey)
                        backStack.remove(SplashKey)
                    }
                )
            }
            entry<OnboardingKey> {
                OnboardingScreen(
                    onNavigateToHome = {
                        backStack.add(HomeAppKey)
                        backStack.remove(OnboardingKey)
                    }
                )
            }
            entry<HomeAppKey> {
                HomeApp(
                    onRequestVpnPermission = onRequestVpnPermission,
                    onShowVpnConflictDialog = onShowVpnConflictDialog
                )
            }

        }
    )
}
