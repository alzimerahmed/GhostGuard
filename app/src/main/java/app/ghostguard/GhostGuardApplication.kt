package app.ghostguard

import android.app.Application
import app.ghostguard.data.datastore.AppPreferences
import app.ghostguard.di.appModule
import app.ghostguard.utils.CrashReportingManager
import app.ghostguard.utils.FileLoggingTree
import app.ghostguard.worker.DailySummaryScheduler
import app.ghostguard.worker.FilterUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber
import timber.log.Timber.DebugTree

class GhostGuardApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@GhostGuardApplication)
            modules(appModule)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }

        // Plant File logging tree for all builds to allow log export
        Timber.plant(FileLoggingTree(this))

        // Schedule auto-update for filter lists after Koin is initialized
        val appPreferences: AppPreferences by inject()
        applicationScope.launch {
            // Restore Crash Reporting state dynamically
            val isCrashReportingEnabled = appPreferences.crashReportingEnabled.first()
            CrashReportingManager.toggleSentry(this@GhostGuardApplication, isCrashReportingEnabled)

            // Move v6.3.0 single-config users onto the multi-profile schema.
            appPreferences.migrateLegacyWgConfigIfNeeded()

            FilterUpdateScheduler.scheduleFilterUpdate(this@GhostGuardApplication, appPreferences)
            app.ghostguard.ui.browser.rules.BrowserRuleUpdateWorker.schedule(this@GhostGuardApplication)

            // Schedule daily summary only if enabled
            if (appPreferences.dailySummaryEnabled.first()) {
                DailySummaryScheduler.scheduleDailySummary(this@GhostGuardApplication)
            }
        }

        // Trusted Wi-Fi networks (#197): auto-pause/resume on SSID change.
        app.ghostguard.service.TrustedNetworkManager(this, appPreferences).start()
    }
}
