package app.ghostguard.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.ghostguard.data.datastore.AppPreferences
import app.ghostguard.ui.about.AboutScreen
import app.ghostguard.ui.appearance.AppearanceScreen
import app.ghostguard.ui.appmanagement.AppManagementScreen
import app.ghostguard.ui.customrules.CustomRulesScreen
import app.ghostguard.ui.data.AboutKey
import app.ghostguard.ui.data.AppManagementKey
import app.ghostguard.ui.data.AppearanceKey
import app.ghostguard.ui.browser.BrowserActivity
import app.ghostguard.ui.browser.BrowserScreen
import app.ghostguard.ui.browser.elementrules.ElementRulesScreen
import app.ghostguard.ui.data.BottomBarScreen
import app.ghostguard.ui.data.BrowserKey
import app.ghostguard.ui.data.ElementRulesKey
import app.ghostguard.ui.data.CustomRuleKey
import app.ghostguard.ui.data.DnsProviderKey
import app.ghostguard.ui.data.DomainRulesKey
import app.ghostguard.ui.data.FilterDetailKey
import app.ghostguard.ui.data.FilterKey
import app.ghostguard.ui.data.FireWallKey
import app.ghostguard.ui.data.HomeKey
import app.ghostguard.ui.data.HttpsFilteringKey
import app.ghostguard.ui.data.CertInstallationWizardKey
import app.ghostguard.ui.httpsfiltering.wizard.CertInstallationWizardScreen
import app.ghostguard.ui.data.LogsKey
import app.ghostguard.ui.data.ProfileKey
import app.ghostguard.ui.data.SettingsKey
import app.ghostguard.ui.data.StatisticsKey
import app.ghostguard.ui.data.WhiteListAppKey
import app.ghostguard.ui.data.TrustedNetworksKey
import app.ghostguard.ui.data.WireGuardEditKey
import app.ghostguard.ui.data.WireGuardImportKey
import app.ghostguard.ui.dnsprovider.DnsProviderScreen
import app.ghostguard.ui.domainrules.DomainRulesScreen
import app.ghostguard.ui.filter.FilterSetupScreen
import app.ghostguard.ui.filter.detail.FilterDetailScreen
import app.ghostguard.ui.firewall.FirewallScreen
import app.ghostguard.ui.home.HomeScreen
import app.ghostguard.ui.httpsfiltering.HttpsFilteringScreen
import app.ghostguard.ui.logs.LogsScreen
import app.ghostguard.ui.profile.ProfileScreen
import app.ghostguard.ui.settings.SettingsScreen
import app.ghostguard.ui.statistics.StatisticsScreen
import app.ghostguard.ui.whitelist.AppWhitelistScreen
import app.ghostguard.ui.wireguard.WireGuardEditScreen
import app.ghostguard.ui.wireguard.WireGuardImportScreen
import org.koin.compose.koinInject

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun HomeApp(
    onRequestVpnPermission: () -> Unit = {},
    onShowVpnConflictDialog: () -> Unit = {}
) {
    val context = LocalContext.current
    val appPrefs: AppPreferences = koinInject()
    val showBottomNavLabels by appPrefs.showBottomNavLabels.collectAsStateWithLifecycle(
        initialValue = true,
    )
    val homeStack = rememberNavBackStack(HomeKey)
    val filterStack = rememberNavBackStack(FilterKey)
    val firewallStack = rememberNavBackStack(FireWallKey)
    val domainRuleStack = rememberNavBackStack(DomainRulesKey)
    val settingsStack = rememberNavBackStack(SettingsKey)
    var currentTab by rememberSaveable { mutableStateOf(BottomBarScreen.Home) }

    val currentBackStack = when (currentTab) {
        BottomBarScreen.Home -> homeStack
        BottomBarScreen.FilterSetup -> filterStack
        BottomBarScreen.Firewall -> firewallStack
        BottomBarScreen.DomainRule -> domainRuleStack
        BottomBarScreen.Settings -> settingsStack
    }

    val bottomBarScreens = listOf(
        BottomBarScreen.Home,
        BottomBarScreen.FilterSetup,
        BottomBarScreen.Firewall,
        BottomBarScreen.DomainRule,
        BottomBarScreen.Settings
    )
    var showBottomBar by rememberSaveable { mutableStateOf(true) }
    fun safePop(stack: MutableList<*>) {
        if (stack.size > 1) {
            stack.removeLastOrNull()
        }
        showBottomBar = stack.size <= 1
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!showBottomBar) return@Scaffold
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                bottomBarScreens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentBackStack == when (screen) {
                            BottomBarScreen.Home -> homeStack
                            BottomBarScreen.FilterSetup -> filterStack
                            BottomBarScreen.Firewall -> firewallStack
                            BottomBarScreen.DomainRule -> domainRuleStack
                            BottomBarScreen.Settings -> settingsStack
                        },
                        onClick = {
                            currentTab = screen
                        },
                        icon = {
                            Icon(
                                painter = painterResource(screen.icon),
                                contentDescription = stringResource(screen.labelRes),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = if (showBottomNavLabels) {
                            {
                                Text(
                                    text = stringResource(screen.labelRes),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = LocalTextStyle.current.copy(
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        } else null,
                        alwaysShowLabel = showBottomNavLabels,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        // When on a non-Home tab root, back should switch to Home tab instead of exiting
        BackHandler(enabled = currentTab != BottomBarScreen.Home && currentBackStack.size <= 1) {
            currentTab = BottomBarScreen.Home
            showBottomBar = true
        }

        NavDisplay(
            backStack = currentBackStack,
            onBack = {
                safePop(currentBackStack)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .consumeWindowInsets(PaddingValues(bottom = innerPadding.calculateBottomPadding())),
            entryProvider = entryProvider {
                entry<HomeKey> {
                    HomeScreen(
                        onShowVpnConflictDialog = onShowVpnConflictDialog,
                        onRequestVpnPermission = onRequestVpnPermission,
                        onNavigateToLogScreen = { filterStatus ->
                            showBottomBar = false
                            homeStack.add(LogsKey(filterStatus))
                        },
                        onNavigateToStatisticsScreen = {
                            showBottomBar = false
                            homeStack.add(StatisticsKey)
                        },
                        onNavigateToProfileScreen = {
                            showBottomBar = false
                            homeStack.add(ProfileKey)
                        },
                        onNavigateToBrowser = { url ->
                            context.startActivity(BrowserActivity.createIntent(context, url))
                        }
                    )
                }
                entry<FilterKey> {
                    FilterSetupScreen(
                        onNavigateToFilterDetail = { filterId ->
                            showBottomBar = false
                            filterStack.add(FilterDetailKey(filterId))
                        },
                        onNavigateToCustomRules = {
                            showBottomBar = false
                            filterStack.add(CustomRuleKey)
                        }
                    )
                }
                entry<FireWallKey> {
                    FirewallScreen()
                }
                entry<DomainRulesKey> {
                    DomainRulesScreen()
                }
                entry<SettingsKey> {
                    SettingsScreen(
                        onNavigateToAbout = {
                            showBottomBar = false
                            settingsStack.add(AboutKey)
                        },
                        onNavigateToAppearance = {
                            showBottomBar = false
                            settingsStack.add(AppearanceKey)
                        },
                        onNavigateToAppManagement = {
                            showBottomBar = false
                            settingsStack.add(AppManagementKey)
                        },
                        onNavigateToFilterSetup = {
                            currentTab = BottomBarScreen.FilterSetup
                        },
                        onNavigateToWhitelistApps = {
                            showBottomBar = false
                            settingsStack.add(WhiteListAppKey)
                        },
                        onNavigateToTrustedNetworks = {
                            showBottomBar = false
                            settingsStack.add(TrustedNetworksKey)
                        },
                        onNavigateToWireGuardImport = {
                            showBottomBar = false
                            settingsStack.add(WireGuardImportKey)
                        },
                        onNavigateToHttpsFiltering = {
                            showBottomBar = false
                            settingsStack.add(HttpsFilteringKey)
                        },
                        onNavigateToDNSProvider = {
                            showBottomBar = false
                            settingsStack.add(DnsProviderKey)
                        }
                    )
                }
                entry<StatisticsKey> {
                    StatisticsScreen(
                        onNavigateBack = {
                            safePop(homeStack)
                        }
                    )
                }
                entry<LogsKey> {
                    LogsScreen(
                        initialFilterStatus = it.filterStatus,
                        onNavigateBack = {
                            safePop(homeStack)
                        }
                    )
                }
                entry<ProfileKey> {
                    ProfileScreen(
                        onNavigateBack = {
                            safePop(homeStack)
                        }
                    )
                }
                entry<FilterDetailKey> {
                    FilterDetailScreen(
                        filterId = it.filterId,
                        onNavigateBack = {
                            safePop(filterStack)
                        }
                    )
                }
                entry<CustomRuleKey> {
                    CustomRulesScreen(
                        onNavigateBack = {
                            safePop(filterStack)
                        }
                    )
                }
                entry<AboutKey> {
                    AboutScreen(
                        onNavigateBack = {
                            safePop(settingsStack)
                        }
                    )
                }
                entry<AppearanceKey> {
                    AppearanceScreen(
                        onNavigateBack = {
                            safePop(settingsStack)
                        }
                    )
                }
                entry<AppManagementKey> {
                    AppManagementScreen(
                        onNavigateBack = {
                            safePop(settingsStack)
                        }
                    )
                }
                entry<DnsProviderKey> {
                    DnsProviderScreen(
                        onNavigateBack = {
                            safePop(settingsStack)
                        }
                    )
                }
                entry<WhiteListAppKey> {
                    AppWhitelistScreen(
                        onNavigateBack = {
                            safePop(settingsStack)
                        }
                    )
                }
                entry<TrustedNetworksKey> {
                    app.ghostguard.ui.trustednetworks.TrustedNetworksScreen(
                        onNavigateBack = {
                            safePop(settingsStack)
                        }
                    )
                }
                entry<WireGuardImportKey> {
                    WireGuardImportScreen(
                        onNavigateBack = {
                            safePop(settingsStack)
                        },
                        onEditProfile = { profileId ->
                            settingsStack.add(WireGuardEditKey(profileId))
                        },
                    )
                }
                entry<WireGuardEditKey> { key ->
                    WireGuardEditScreen(
                        profileId = key.profileId,
                        onNavigateBack = {
                            safePop(settingsStack)
                        },
                    )
                }
                entry<HttpsFilteringKey> {
                    HttpsFilteringScreen(
                        onNavigateBack = {
                            safePop(settingsStack)
                        },
                        onNavigateToWizard = {
                            settingsStack.add(CertInstallationWizardKey)
                        }
                    )
                }
                entry<CertInstallationWizardKey> {
                    CertInstallationWizardScreen(
                        onNavigateBack = {
                            safePop(settingsStack)
                        }
                    )
                }
                entry<BrowserKey> { key ->
                    BrowserScreen(
                        initialUrl = key.initialUrl,
                        onCloseBrowser = {
                            safePop(currentBackStack)
                        },
                        onNavigateToElementRules = {
                            currentBackStack.add(ElementRulesKey)
                        }
                    )
                }
                entry<ElementRulesKey> {
                    ElementRulesScreen(
                        onNavigateBack = {
                            safePop(currentBackStack)
                        }
                    )
                }
            }
        )
    }
}