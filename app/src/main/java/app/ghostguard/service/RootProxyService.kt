package app.ghostguard.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.work.OneTimeWorkRequestBuilder
import app.ghostguard.data.dao.DnsLogDao
import app.ghostguard.data.dao.FirewallRuleDao
import app.ghostguard.data.datastore.AppPreferences
import app.ghostguard.data.repository.FilterListRepository
import app.ghostguard.utils.AppNameResolver
import app.ghostguard.utils.startOfDayMillis
import app.ghostguard.worker.RootProxyResumeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.getKoin
import timber.log.Timber

class RootProxyService : Service() {
    companion object {
        const val ACTION_START = "app.ghostguard.ROOT_START"
        const val ACTION_STOP = "app.ghostguard.ROOT_STOP"
        const val ACTION_RESTART = "app.ghostguard.ROOT_RESTART"
        const val ACTION_PAUSE_1H = "app.ghostguard.ROOT_PAUSE_1H"
        const val EXTRA_STARTED_FROM_BOOT = "extra_started_from_boot"

        private val _state = kotlinx.coroutines.flow.MutableStateFlow(VpnState.STOPPED)
        val state: kotlinx.coroutines.flow.StateFlow<VpnState> = _state.asStateFlow()

        val isRunning: Boolean get() = _state.value == VpnState.RUNNING

        @Volatile
        var startTimestamp: Long = 0L
            private set

        fun start(context: Context) {
            val intent =
                Intent(context, RootProxyService::class.java).apply {
                    action = ACTION_START
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent =
                Intent(context, RootProxyService::class.java).apply {
                    action = ACTION_STOP
                }
            context.startService(intent)
        }

        fun requestRestart(context: Context) {
            val s = _state.value
            if (s == VpnState.RUNNING || s == VpnState.RESTARTING) {
                val intent =
                    Intent(context, RootProxyService::class.java).apply {
                        action = ACTION_RESTART
                    }
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchdogJob: Job? = null
    private var notificationUpdateJob: Job? = null
    private var retryManager = VpnRetryManager(maxRetries = 10, maxDelayMs = 60000L)
    private lateinit var notificationManager: RootProxyNotificationManager

    @Volatile
    private var todayBlockedCount: Int = 0
    private lateinit var appPrefs: AppPreferences
    private lateinit var filterRepo: FilterListRepository
    private lateinit var dnsLogDao: DnsLogDao
    private lateinit var firewallRuleDao: FirewallRuleDao
    private lateinit var appNameResolver: AppNameResolver
    private lateinit var goTunnelAdapter: GoTunnelAdapter

    @Volatile
    private var firewallManager: FirewallManager? = null

    @Volatile
    private var preserveUptimeOnRestart = false

    @Volatile
    private var whitelistedUids: List<Int> = emptyList()

    @Volatile
    private var isRecordDnsLogsEnabled = true

    override fun onCreate() {
        super.onCreate()
        val koin = getKoin()
        appPrefs = koin.get()
        filterRepo = koin.get()
        dnsLogDao = koin.get()
        firewallRuleDao = koin.get()
        notificationManager = RootProxyNotificationManager(this)

        serviceScope.launch {
            appPrefs.recordDnsLogs.collect { enabled ->
                isRecordDnsLogsEnabled = enabled
            }
        }

        appNameResolver = AppNameResolver(this)
        goTunnelAdapter =
            GoTunnelAdapter(
                context = this,
                filterRepo = filterRepo,
                dnsLogDao = dnsLogDao,
                scope = serviceScope,
                appNameResolver = appNameResolver,
                firewallManagerProvider = { firewallManager },
                recordLogProvider = { isRecordDnsLogsEnabled },
            )
        Timber.d("RootProxyService created")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val startedFromBoot = intent?.getBooleanExtra(EXTRA_STARTED_FROM_BOOT, false) ?: false

        when (intent?.action) {
            ACTION_STOP -> {
                stopProxy()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_1H -> {
                pauseProxy()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                restartProxy()
                return START_STICKY
            }
            else -> {
                startProxy(startedFromBoot)
                return START_STICKY
            }
        }
    }

    private fun startProxy(startedFromBoot: Boolean = false) {
        if (_state.value == VpnState.RUNNING || _state.value == VpnState.STARTING) {
            Timber.d("RootProxyService already running/starting")
            return
        }

        _state.value = VpnState.STARTING

        notificationManager.createChannel()
        startForeground(
            RootProxyNotificationManager.NOTIFICATION_ID,
            notificationManager.buildNotification(false, 0, 0L),
        )

        retryManager =
            VpnRetryManager(
                maxRetries = if (startedFromBoot) 30 else 10,
                maxDelayMs = 60000L,
            )

        serviceScope.launch {
            try {
                filterRepo.loadWhitelist()
                filterRepo.loadCustomRules()
                filterRepo.seedDefaultsIfNeeded()
                filterRepo.fetchAndSyncRemoteFilterLists()
                val result = filterRepo.loadAllEnabledFilters()
                Timber.d("Filters loaded for Root Proxy mode: ${result.getOrDefault(0)} domains")

                val protocol = appPrefs.dnsProtocol.first().name
                val primary = appPrefs.upstreamDns.first()
                val fallback = appPrefs.fallbackDns.first()
                val dohUrl = appPrefs.dohUrl.first()
                val safeSearch = appPrefs.safeSearchEnabled.first()
                val youtubeSafe = appPrefs.youtubeRestrictedMode.first()
                val responseType = appPrefs.dnsResponseType.first()

                goTunnelAdapter.configureDns(protocol, primary, fallback, dohUrl)
                goTunnelAdapter.configureSafeSearch(safeSearch, youtubeSafe)
                goTunnelAdapter.setBlockResponseType(responseType)

                val firewallEnabled = appPrefs.firewallEnabled.first()
                if (firewallEnabled) {
                    val fwManager = FirewallManager(this@RootProxyService, firewallRuleDao)
                    fwManager.loadRules()
                    firewallManager = fwManager
                    Timber.d("Firewall enabled for Root Proxy, rules loaded")
                } else {
                    firewallManager = null
                }

                whitelistedUids =
                    appPrefs.getWhitelistedAppsSnapshot().mapNotNull { pkg ->
                        try {
                            packageManager.getApplicationInfo(pkg, 0).uid
                        } catch (e: Exception) {
                            Timber.w("Whitelisted app not found, skipping: $pkg")
                            null
                        }
                    }.distinct()

                var proxyStarted = false
                while (!proxyStarted && retryManager.shouldRetry()) {
                    if (!IptablesManager.ensureRootShell()) {
                        Timber.w("Root shell not available yet")
                    } else {
                        val engineStarted = goTunnelAdapter.startStandalone(port = 15353)
                        if (engineStarted) {
                            if (IptablesManager.setupRules(this@RootProxyService, whitelistUids = whitelistedUids)) {
                                proxyStarted = true
                            } else {
                                goTunnelAdapter.stop()
                            }
                        }
                    }

                    if (!proxyStarted && retryManager.shouldRetry()) {
                        Timber.w(
                            "Root Proxy establishment failed, retrying... (${retryManager.getRetryCount()}/${retryManager.getMaxRetries()})",
                        )
                        retryManager.waitForRetry()
                    }
                }

                if (!proxyStarted) {
                    Timber.e("Failed to start Root Proxy after ${retryManager.getMaxRetries()} attempts")
                    stopProxy()
                    notificationManager.showStartFailedNotification()
                    return@launch
                }

                retryManager.reset()

                appNameResolver.startSnapshotter(serviceScope)

                _state.value = VpnState.RUNNING
                appPrefs.setVpnEnabled(true)
                if (!preserveUptimeOnRestart || startTimestamp == 0L) {
                    startTimestamp = System.currentTimeMillis()
                }
                preserveUptimeOnRestart = false
                Timber.d("Root Proxy mode active — DNS traffic redirected to :15353")

                updateNotification()
                startNotificationUpdates()
                startWatchdog()
            } catch (e: Exception) {
                Timber.e(e, "Failed to start Root Proxy mode")
                IptablesManager.teardownRules()
                stopSelf()
            }
        }
    }

    private fun stopProxy(showPausedNotification: Boolean = false) {
        Timber.d("Stopping Root Proxy mode")
        _state.value = VpnState.STOPPING
        watchdogJob?.cancel()
        stopNotificationUpdates()
        appNameResolver.stopSnapshotter()

        IptablesManager.teardownRules()
        goTunnelAdapter.stop()

        _state.value = VpnState.STOPPED
        serviceScope.launch {
            appPrefs.setVpnEnabled(false)
        }
        startTimestamp = 0L
        if (showPausedNotification) {
            stopForeground(STOP_FOREGROUND_DETACH)
            notificationManager.showPausedNotification()
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    private fun pauseProxy() {
        Timber.d("Pausing Root Proxy for 1 hour")

        val resumeWork =
            OneTimeWorkRequestBuilder<RootProxyResumeWorker>()
                .setInitialDelay(1, java.util.concurrent.TimeUnit.HOURS)
                .build()
        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
            RootProxyResumeWorker.WORK_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            resumeWork,
        )

        stopProxy(showPausedNotification = true)
    }

    private fun restartProxy() {
        if (_state.value == VpnState.RESTARTING) return
        val s = _state.value
        if (s != VpnState.RUNNING && s != VpnState.STARTING) return

        _state.value = VpnState.RESTARTING
        Timber.d("Restarting Root Proxy to apply new settings")

        watchdogJob?.cancel()
        stopNotificationUpdates()
        appNameResolver.stopSnapshotter()

        serviceScope.launch(Dispatchers.IO) {
            goTunnelAdapter.stop()
            IptablesManager.teardownRules()
            delay(1000L)
            preserveUptimeOnRestart = true
            _state.value = VpnState.STOPPED
            startProxy()
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob =
            serviceScope.launch {
                while (isActive && _state.value == VpnState.RUNNING) {
                    delay(10_000)
                    if (!IptablesManager.isActive()) {
                        Timber.w("iptables rules disappeared — re-applying")
                        IptablesManager.setupRules(this@RootProxyService, whitelistUids = whitelistedUids)
                    }
                }
            }
    }

    override fun onDestroy() {
        Timber.d("RootProxyService onDestroy — teardown iptables")
        _state.value = VpnState.STOPPED
        startTimestamp = 0L
        watchdogJob?.cancel()
        stopNotificationUpdates()
        if (::appNameResolver.isInitialized) appNameResolver.stopSnapshotter()
        IptablesManager.teardownRules()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.d("RootProxyService onTaskRemoved — teardown iptables")
        IptablesManager.teardownRules()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startNotificationUpdates() {
        notificationUpdateJob?.cancel()

        notificationUpdateJob =
            serviceScope.launch {
                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                while (isActive && isRunning) {
                    try {
                        todayBlockedCount = dnsLogDao.getBlockedCountSinceSync(startOfDayMillis())
                        delay(30_000L)
                        if (isRunning && powerManager.isInteractive) {
                            updateNotification()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error updating notification in RootProxyService")
                        break
                    }
                }
            }
    }

    private fun stopNotificationUpdates() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
    }

    private fun updateNotification() {
        val notification =
            notificationManager.buildNotification(
                isRunning = isRunning,
                todayBlockedCount = todayBlockedCount,
                startTimestamp = startTimestamp,
            )
        notificationManager.updateNotification(notification)
    }
}
