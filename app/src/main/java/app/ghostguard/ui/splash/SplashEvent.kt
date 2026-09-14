package app.ghostguard.ui.splash

sealed interface SplashEvent {
    data object Home : SplashEvent

    data object Onboarding : SplashEvent

    data object SetupWizard : SplashEvent
}
