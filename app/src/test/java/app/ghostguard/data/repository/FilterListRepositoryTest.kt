package app.ghostguard.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ghostguard.data.dao.CustomDnsRuleDao
import app.ghostguard.data.dao.FilterListDao
import app.ghostguard.data.dao.WhitelistDomainDao
import app.ghostguard.data.entities.CustomDnsRule
import app.ghostguard.data.entities.FilterList
import app.ghostguard.data.entities.RuleType
import app.ghostguard.data.entities.WhitelistDomain
import app.ghostguard.data.remote.FilterDownloadManager
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeCustomDnsRuleDao(
    var blockDomains: List<String> = emptyList(),
    var allowDomains: List<String> = emptyList(),
) : CustomDnsRuleDao {
    val rules = mutableListOf<CustomDnsRule>()

    override fun getAllFlow(): Flow<List<CustomDnsRule>> = MutableStateFlow(rules.toList())

    override suspend fun getAll(): List<CustomDnsRule> = rules

    override suspend fun getEnabledRules(): List<CustomDnsRule> = rules.filter { it.isEnabled && it.ruleType != RuleType.COMMENT }

    override suspend fun getBlockDomains(): List<String> = blockDomains

    override suspend fun getAllowDomains(): List<String> = allowDomains

    override suspend fun insert(rule: CustomDnsRule): Long {
        rules.add(rule)
        return rules.size.toLong()
    }

    override suspend fun insertAll(newRules: List<CustomDnsRule>) {
        rules.addAll(newRules)
    }

    override suspend fun update(rule: CustomDnsRule) {
        rules.replaceAll { if (it.id == rule.id) rule else it }
    }

    override suspend fun delete(rule: CustomDnsRule) {
        rules.remove(rule)
    }

    override suspend fun deleteAll() {
        rules.clear()
    }

    override suspend fun deleteBlockRuleByDomain(domain: String) {
        rules.removeAll { it.domain == domain && it.ruleType == RuleType.BLOCK }
    }

    override suspend fun getRuleCount(): Int = rules.count { it.ruleType != RuleType.COMMENT }

    override suspend fun exists(ruleText: String): Int = rules.count { it.rule == ruleText }
}

private class FakeWhitelistDomainDao(
    var domains: List<String> = emptyList(),
) : WhitelistDomainDao {
    override fun getAll(): Flow<List<WhitelistDomain>> = MutableStateFlow(emptyList())

    override suspend fun getAllDomains(): List<String> = domains

    override suspend fun insert(domain: WhitelistDomain) = Unit

    override suspend fun delete(domain: WhitelistDomain) = Unit

    override suspend fun deleteByDomain(domain: String) = Unit

    override suspend fun exists(domain: String): Int = 0
}

private class FakeFilterListDao : FilterListDao {
    val lists = mutableListOf<FilterList>()

    override suspend fun insert(filterList: FilterList): Long {
        lists.add(filterList)
        return lists.size.toLong()
    }

    override suspend fun update(filterList: FilterList) = Unit

    override suspend fun delete(filterList: FilterList) {
        lists.remove(filterList)
    }

    override fun getAll(): Flow<List<FilterList>> = MutableStateFlow(lists.toList())

    override suspend fun getEnabled(): List<FilterList> = lists.filter { it.isEnabled }

    override suspend fun count(): Int = lists.size

    override suspend fun setEnabled(
        id: Long,
        enabled: Boolean,
    ) = Unit

    override suspend fun updateStats(
        id: Long,
        count: Int,
        timestamp: Long,
    ) = Unit

    override suspend fun getByUrl(url: String): FilterList? = null

    override suspend fun getAllSync(): List<FilterList> = lists.toList()

    override suspend fun getAllUrls(): List<String> = lists.map { it.url }

    override suspend fun getById(id: Long): FilterList? = lists.firstOrNull { it.id == id }

    override fun getByIdFlow(id: Long): Flow<FilterList?> = MutableStateFlow(lists.firstOrNull { it.id == id })

    override suspend fun getByOriginalUrl(url: String): FilterList? = null

    override suspend fun getAllNonBuiltIn(): List<FilterList> = lists.filter { !it.isBuiltIn }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FilterListRepositoryTest {
    private lateinit var repository: FilterListRepository
    private lateinit var customDnsRuleDao: FakeCustomDnsRuleDao
    private lateinit var whitelistDomainDao: FakeWhitelistDomainDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        customDnsRuleDao = FakeCustomDnsRuleDao()
        whitelistDomainDao = FakeWhitelistDomainDao()
        val client = HttpClient()
        repository =
            FilterListRepository(
                context = context,
                filterListDao = FakeFilterListDao(),
                whitelistDomainDao = whitelistDomainDao,
                customDnsRuleDao = customDnsRuleDao,
                client = client,
                downloadManager = FilterDownloadManager(context, client),
            )
    }

    // ── isBlocked precedence: custom allow > custom block > whitelist ────

    @Test
    fun `custom allow overrides custom block`() =
        runTest {
            customDnsRuleDao.blockDomains = listOf("example.com")
            customDnsRuleDao.allowDomains = listOf("example.com")
            repository.loadCustomRules()

            assertFalse(repository.isBlocked("example.com"))
            assertEquals("", repository.getBlockReason("example.com"))
        }

    @Test
    fun `custom block rule blocks domain`() =
        runTest {
            customDnsRuleDao.blockDomains = listOf("ads.example.com")
            repository.loadCustomRules()

            assertFalse(!repository.isBlocked("ads.example.com"))
            assertEquals(FilterListRepository.BLOCK_REASON_CUSTOM_RULE, repository.getBlockReason("ads.example.com"))
            assertEquals(1L, repository.hasCustomRule("ads.example.com"))
        }

    @Test
    fun `whitelisted domain is not blocked`() =
        runTest {
            whitelistDomainDao.domains = listOf("trusted.example")
            repository.loadWhitelist()

            assertFalse(repository.isBlocked("trusted.example"))
            assertEquals(0L, repository.hasCustomRule("trusted.example"))
        }

    @Test
    fun `no rule means not blocked and hasCustomRule is -1`() =
        runTest {
            repository.loadCustomRules()
            repository.loadWhitelist()

            assertFalse(repository.isBlocked("unknown.example"))
            assertEquals(-1L, repository.hasCustomRule("unknown.example"))
        }

    // ── Parent-domain matching ───────────────────────────────────────────

    @Test
    fun `block on parent domain matches subdomain`() =
        runTest {
            customDnsRuleDao.blockDomains = listOf("example.com")
            repository.loadCustomRules()

            assert(repository.isBlocked("ads.sub.example.com"))
            assert(repository.isBlocked("sub.example.com"))
        }

    @Test
    fun `allow on parent domain overrides block on subdomain`() =
        runTest {
            customDnsRuleDao.blockDomains = listOf("ads.example.com")
            customDnsRuleDao.allowDomains = listOf("example.com")
            repository.loadCustomRules()

            assertFalse(repository.isBlocked("ads.example.com"))
        }

    @Test
    fun `wildcard parent entry matches subdomain`() =
        runTest {
            customDnsRuleDao.blockDomains = listOf("*.example.com")
            repository.loadCustomRules()

            assert(repository.isBlocked("sub.example.com"))
            assertFalse(repository.isBlocked("example.com"))
        }

    // ── Normalization ────────────────────────────────────────────────────

    @Test
    fun `loaded domains are lowercased`() =
        runTest {
            customDnsRuleDao.blockDomains = listOf("ADS.Example.COM")
            repository.loadCustomRules()

            assert(repository.isBlocked("ads.example.com"))
        }
}
