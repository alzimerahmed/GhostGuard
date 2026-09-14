package app.ghostguard.di

import app.ghostguard.BuildConfig
import app.ghostguard.data.AppDatabase
import app.ghostguard.data.datastore.AppPreferences
import app.ghostguard.data.entities.ProfileManager
import app.ghostguard.data.remote.FilterDownloadManager
import app.ghostguard.data.remote.api.CustomFilterApi
import app.ghostguard.data.repository.CustomFilterManager
import app.ghostguard.data.repository.FilterListRepository
import app.ghostguard.ui.appearance.AppearanceViewModel
import app.ghostguard.ui.appmanagement.AppManagementViewModel
import app.ghostguard.ui.customrules.CustomRulesViewModel
import app.ghostguard.ui.dnsprovider.DnsProviderViewModel
import app.ghostguard.ui.domainrules.DomainRulesViewModel
import app.ghostguard.ui.filter.FilterSetupViewModel
import app.ghostguard.ui.filter.detail.FilterDetailViewModel
import app.ghostguard.ui.firewall.FirewallViewModel
import app.ghostguard.ui.home.HomeViewModel
import app.ghostguard.ui.httpsfiltering.HttpsFilteringViewModel
import app.ghostguard.ui.httpsfiltering.wizard.CertInstallationWizardViewModel
import app.ghostguard.ui.logs.LogViewModel
import app.ghostguard.ui.onboarding.OnboardingViewModel
import app.ghostguard.ui.profile.ProfileViewModel
import app.ghostguard.ui.settings.SettingsViewModel
import app.ghostguard.ui.splash.SplashViewModel
import app.ghostguard.ui.statistics.StatisticsViewModel
import app.ghostguard.ui.whitelist.AppWhitelistViewModel
import app.ghostguard.ui.wireguard.WireGuardEditViewModel
import app.ghostguard.ui.wireguard.WireGuardImportViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import timber.log.Timber

val appModule =
    module {

        // HTTP Client
        single {
            HttpClient(CIO) {
                engine {
                    requestTimeout = 60_000
                    endpoint {
                        connectTimeout = 30_000
                    }
                }

                install(Logging) {
                    logger =
                        object : Logger {
                            override fun log(message: String) {
                                Timber.d(message)
                            }
                        }
                    val logLevel = if (BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE
                    level = logLevel
                }

                install(HttpTimeout) {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 30_000
                }
            }
        }

        // DNS Clients (Removed - now handled by Go tunnel)

        // Database
        single { AppDatabase.getInstance(androidContext()) }
        single { get<AppDatabase>().dnsLogDao() }
        single { get<AppDatabase>().filterListDao() }
        single { get<AppDatabase>().whitelistDomainDao() }
        single { get<AppDatabase>().dnsErrorDao() }
        single { get<AppDatabase>().customDnsRuleDao() }
        single { get<AppDatabase>().protectionProfileDao() }
        single { get<AppDatabase>().firewallRuleDao() }
        single { get<AppDatabase>().elementRuleDao() }

        // Preferences
        single { AppPreferences(androidContext()) }

        // Repository
        single { FilterDownloadManager(androidContext(), get()) }
        single {
            FilterListRepository(
                context = androidContext(),
                filterListDao = get(),
                whitelistDomainDao = get(),
                customDnsRuleDao = get(),
                client = get(),
                downloadManager = get(),
            )
        }
        single { CustomFilterApi(get()) }
        single {
            CustomFilterManager(
                context = androidContext(),
                client = get(),
                filterListDao = get(),
                customFilterApi = get(),
            )
        }

        // Browser Dynamic Rules & Search Suggestions
        single {
            app.ghostguard.ui.browser.rules
                .BrowserRuleStorage(androidContext())
        }
        single<app.ghostguard.ui.browser.rules.BrowserRuleRepository> {
            app.ghostguard.ui.browser.rules.BrowserRuleRepositoryImpl(
                storage = get(),
                client = get(),
            )
        }
        single<app.ghostguard.ui.browser.data.SearchSuggestionRepository> {
            app.ghostguard.ui.browser.data.SearchSuggestionRepositoryImpl(
                client = get(),
            )
        }

        // Profile Manager
        single {
            ProfileManager(
                profileDao = get(),
                filterListDao = get(),
                appPrefs = get(),
                filterRepo = get(),
            )
        }

        // ViewModels
        viewModel {
            HomeViewModel(
                appPrefs = get(),
                dnsLogDao = get(),
                filterRepo = get(),
                profileDao = get(),
                filterListDao = get(),
            )
        }
        viewModel { StatisticsViewModel(dnsLogDao = get()) }
        viewModel {
            LogViewModel(
                dnsLogDao = get(),
                filterListDao = get(),
                whitelistDomainDao = get(),
                customDnsRuleDao = get(),
                filterListRepository = get(),
                appPrefs = get(),
                application = androidApplication(),
            )
        }
        viewModel {
            SettingsViewModel(
                appPrefs = get(),
                filterRepo = get(),
                dnsLogDao = get(),
                whitelistDomainDao = get(),
                filterListDao = get(),
                customDnsRuleDao = get(),
                profileDao = get(),
                profileManager = get(),
                firewallRuleDao = get(),
                application = androidApplication(),
            )
        }
        viewModel {
            FilterSetupViewModel(
                filterRepo = get(),
                filterListDao = get(),
                customFilterManager = get(),
                profileManager = get(),
                application = androidApplication(),
            )
        }
        viewModel { (filterId: Long) ->
            FilterDetailViewModel(
                filterId = filterId,
                filterListDao = get(),
                dnsLogDao = get(),
                filterRepo = get(),
                profileManager = get(),
                application = androidApplication(),
                customFilterManager = get(),
            )
        }
        viewModel {
            AppWhitelistViewModel(
                appPrefs = get(),
                application = androidApplication(),
            )
        }
        viewModel {
            app.ghostguard.ui.trustednetworks.TrustedNetworksViewModel(
                appPrefs = get(),
                application = androidApplication(),
            )
        }
        viewModel {
            CustomRulesViewModel(
                customDnsRuleDao = get(),
                filterListRepository = get(),
                application = androidApplication(),
            )
        }
        viewModel {
            DnsProviderViewModel(
                appPrefs = get(),
                application = androidApplication(),
            )
        }
        viewModel {
            AppManagementViewModel(
                appPrefs = get(),
                dnsLogDao = get(),
                application = androidApplication(),
            )
        }
        viewModel {
            OnboardingViewModel(
                appPrefs = get(),
                application = androidApplication(),
            )
        }
        viewModel {
            ProfileViewModel(
                profileManager = get(),
                profileDao = get(),
                filterListDao = get(),
                application = androidApplication(),
            )
        }
        viewModel {
            FirewallViewModel(
                appPrefs = get(),
                firewallRuleDao = get(),
                application = androidApplication(),
            )
        }
        viewModel {
            AppearanceViewModel(
                appPrefs = get(),
                application = androidApplication(),
            )
        }
        viewModel {
            SplashViewModel(
                appPrefs = get(),
            )
        }
        single<app.ghostguard.ui.setup.data.FilterPackApplier> {
            app.ghostguard.ui.setup.data.DefaultFilterPackApplier(
                context = androidContext(),
                filterListDao = get(),
                filterRepo = get(),
            )
        }
        viewModel {
            app.ghostguard.ui.setup.SetupWizardViewModel(
                appPrefs = get(),
                packApplier = get(),
            )
        }
        viewModel {
            DomainRulesViewModel(
                whitelistDomainDao = get(),
                customDnsRuleDao = get(),
                application = androidApplication(),
            )
        }
        viewModel {
            WireGuardImportViewModel(
                application = androidApplication(),
            )
        }
        viewModel {
            WireGuardEditViewModel(
                application = androidApplication(),
            )
        }
        viewModel {
            HttpsFilteringViewModel(
                application = androidApplication(),
            )
        }
        viewModel {
            CertInstallationWizardViewModel(
                application = androidApplication(),
            )
        }
        viewModel {
            app.ghostguard.ui.browser.BrowserViewModel(
                application = androidApplication(),
                ruleRepository = get(),
                suggestionRepository = get(),
                elementRuleDao = get(),
            )
        }
        viewModel {
            app.ghostguard.ui.browser.elementrules.ElementRulesViewModel(
                elementRuleDao = get(),
            )
        }
    }
