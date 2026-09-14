package app.ghostguard.ui.customrules

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.ghostguard.data.dao.CustomDnsRuleDao
import app.ghostguard.data.entities.CustomDnsRule
import app.ghostguard.data.entities.RuleType
import app.ghostguard.data.remote.FilterDownloadManager
import app.ghostguard.data.repository.FilterListRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CustomRulesViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CustomRulesViewModel
    private lateinit var dao: FakeDao

    private class FakeFilterListDaoStub : app.ghostguard.data.dao.FilterListDao {
        override suspend fun insert(filterList: app.ghostguard.data.entities.FilterList): Long = 0

        override suspend fun update(filterList: app.ghostguard.data.entities.FilterList) = Unit

        override suspend fun delete(filterList: app.ghostguard.data.entities.FilterList) = Unit

        override fun getAll() = kotlinx.coroutines.flow.MutableStateFlow<List<app.ghostguard.data.entities.FilterList>>(emptyList())

        override suspend fun getEnabled(): List<app.ghostguard.data.entities.FilterList> = emptyList()

        override suspend fun count(): Int = 0

        override suspend fun setEnabled(
            id: Long,
            enabled: Boolean,
        ) = Unit

        override suspend fun updateStats(
            id: Long,
            count: Int,
            timestamp: Long,
        ) = Unit

        override suspend fun getByUrl(url: String): app.ghostguard.data.entities.FilterList? = null

        override suspend fun getAllSync(): List<app.ghostguard.data.entities.FilterList> = emptyList()

        override suspend fun getAllUrls(): List<String> = emptyList()

        override suspend fun getById(id: Long): app.ghostguard.data.entities.FilterList? = null

        override fun getByIdFlow(id: Long) = kotlinx.coroutines.flow.MutableStateFlow<app.ghostguard.data.entities.FilterList?>(null)

        override suspend fun getByOriginalUrl(url: String): app.ghostguard.data.entities.FilterList? = null

        override suspend fun getAllNonBuiltIn(): List<app.ghostguard.data.entities.FilterList> = emptyList()
    }

    private class FakeWhitelistDaoStub : app.ghostguard.data.dao.WhitelistDomainDao {
        override fun getAll() = kotlinx.coroutines.flow.MutableStateFlow<List<app.ghostguard.data.entities.WhitelistDomain>>(emptyList())

        override suspend fun getAllDomains(): List<String> = emptyList()

        override suspend fun insert(domain: app.ghostguard.data.entities.WhitelistDomain) = Unit

        override suspend fun delete(domain: app.ghostguard.data.entities.WhitelistDomain) = Unit

        override suspend fun deleteByDomain(domain: String) = Unit

        override suspend fun exists(domain: String): Int = 0
    }

    private class FakeDao : CustomDnsRuleDao {
        val rules = mutableListOf<CustomDnsRule>()
        private val flow = kotlinx.coroutines.flow.MutableStateFlow<List<CustomDnsRule>>(emptyList())

        private fun publish() {
            flow.value = rules.toList()
        }

        override fun getAllFlow() = flow

        override suspend fun getAll(): List<CustomDnsRule> = rules.toList()

        override suspend fun getEnabledRules(): List<CustomDnsRule> = rules.filter { it.isEnabled && it.ruleType != RuleType.COMMENT }

        override suspend fun getBlockDomains(): List<String> = rules.filter { it.isEnabled && it.ruleType == RuleType.BLOCK }.map { it.domain }

        override suspend fun getAllowDomains(): List<String> = rules.filter { it.isEnabled && it.ruleType == RuleType.ALLOW }.map { it.domain }

        override suspend fun insert(rule: CustomDnsRule): Long {
            rules.add(rule)
            publish()
            return rules.size.toLong()
        }

        override suspend fun insertAll(newRules: List<CustomDnsRule>) {
            rules.addAll(newRules)
            publish()
        }

        override suspend fun update(rule: CustomDnsRule) {
            rules.replaceAll { if (it.id == rule.id) rule else it }
            publish()
        }

        override suspend fun delete(rule: CustomDnsRule) {
            rules.remove(rule)
            publish()
        }

        override suspend fun deleteAll() {
            rules.clear()
            publish()
        }

        override suspend fun deleteBlockRuleByDomain(domain: String) {
            rules.removeAll { it.domain == domain && it.ruleType == RuleType.BLOCK }
            publish()
        }

        override suspend fun getRuleCount(): Int = rules.count { it.ruleType != RuleType.COMMENT }

        override suspend fun exists(ruleText: String): Int = rules.count { it.rule == ruleText }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeDao()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val client = HttpClient()
        val repository =
            FilterListRepository(
                context = context,
                filterListDao = FakeFilterListDaoStub(),
                whitelistDomainDao = FakeWhitelistDaoStub(),
                customDnsRuleDao = dao,
                client = client,
                downloadManager = FilterDownloadManager(context, client),
            )
        viewModel = CustomRulesViewModel(dao, repository, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun advance() = testDispatcher.scheduler.advanceUntilIdle()

    /** Wait for callbacks that hop through the real Dispatchers.IO. */
    private fun awaitUntil(
        timeoutMs: Long = 5000,
        condition: () -> Boolean,
    ) {
        val start = System.currentTimeMillis()
        while (!condition() && System.currentTimeMillis() - start < timeoutMs) {
            Thread.sleep(50)
            testDispatcher.scheduler.advanceUntilIdle()
        }
    }

    // ── Export ───────────────────────────────────────────────────────────

    @Test
    fun `exportRules joins rule text with newlines`() =
        runTest {
            dao.insert(CustomDnsRule(rule = "||a.com^", ruleType = RuleType.BLOCK, domain = "a.com", isEnabled = true))
            dao.insert(CustomDnsRule(rule = "@@||b.com^", ruleType = RuleType.ALLOW, domain = "b.com", isEnabled = true))
            advance()
            assertEquals("||a.com^\n@@||b.com^", viewModel.exportRules())
        }

    @Test
    fun `exportRules on empty rules returns empty string`() =
        runTest {
            advance()
            assertEquals("", viewModel.exportRules())
        }

    // ── Import (text) with dedup ─────────────────────────────────────────

    @Test
    fun `importRules inserts only new rules and reports count`() =
        runTest {
            dao.rules.add(CustomDnsRule(rule = "||existing.com^", ruleType = RuleType.BLOCK, domain = "existing.com", isEnabled = true))
            advance()

            var inserted = -1
            var error: String? = null
            viewModel.importRules(
                "||existing.com^\n||new.com^\n! comment\n@@allow.com",
                onSuccess = { inserted = it },
                onError = { error = it },
            )
            advance()

            assertEquals(null, error)
            // existing.com duplicate, comment dedups only against comments, so 3 new rules
            assertEquals(3, inserted)
            assertEquals(4, dao.rules.size)
        }

    @Test
    fun `importRules with all duplicates reports error`() =
        runTest {
            dao.rules.add(CustomDnsRule(rule = "||a.com^", ruleType = RuleType.BLOCK, domain = "a.com", isEnabled = true))
            advance()

            var error: String? = null
            viewModel.importRules("||a.com^", onSuccess = {}, onError = { error = it })
            advance()

            assertEquals("Rules already exist", error)
            assertEquals(1, dao.rules.size)
        }

    @Test
    fun `importRules with no valid rules reports error`() =
        runTest {
            var error: String? = null
            viewModel.importRules("not a domain", onSuccess = {}, onError = { error = it })
            advance()
            assertEquals("No valid rules found", error)
        }

    // ── JSON import round-trip via SAF URI ───────────────────────────────

    @Test
    fun `importRulesFromUri parses JSON export and dedups`() =
        runTest {
            dao.rules.add(CustomDnsRule(rule = "||a.com^", ruleType = RuleType.BLOCK, domain = "a.com", isEnabled = true))
            advance()

            val json =
                """
                [
                  {"rule":"||a.com^","ruleType":"BLOCK","domain":"a.com","isEnabled":true},
                  {"rule":"||b.com^","ruleType":"BLOCK","domain":"b.com","isEnabled":true}
                ]
                """.trimIndent()
            val file =
                File(
                    java.nio.file.Files
                        .createTempDirectory("rules")
                        .toFile(),
                    "rules.json",
                )
            file.writeText(json)

            var inserted = -1
            viewModel.importRulesFromUri(
                android.net.Uri.fromFile(file),
                onSuccess = { inserted = it },
                onError = {},
            )
            awaitUntil { inserted != -1 }

            assertEquals(1, inserted)
            assertEquals(2, dao.rules.size)
            assertEquals("b.com", dao.rules.last().domain)
            file.delete()
        }

    @Test
    fun `importRulesFromUri with empty file reports error`() =
        runTest {
            val file =
                File(
                    java.nio.file.Files
                        .createTempDirectory("rules")
                        .toFile(),
                    "empty.txt",
                )
            file.writeText("   ")

            var error: String? = null
            viewModel.importRulesFromUri(android.net.Uri.fromFile(file), onSuccess = {}, onError = { error = it })
            awaitUntil { error != null }

            assertEquals("File is empty", error)
            assertTrue(dao.rules.isEmpty())
            file.delete()
        }

    // ── Add rule ─────────────────────────────────────────────────────────

    @Test
    fun `addRule inserts valid rule`() =
        runTest {
            var success = false
            viewModel.addRule("||ads.com^", onSuccess = { success = true })
            advance()
            assertTrue(success)
            assertEquals(1, dao.rules.size)
            assertEquals(RuleType.BLOCK, dao.rules.first().ruleType)
        }

    @Test
    fun `addRule rejects duplicate`() =
        runTest {
            viewModel.addRule("||ads.com^")
            advance()
            var error: String? = null
            viewModel.addRule("||ads.com^", onError = { error = it })
            advance()
            assertEquals("Rule already exists", error)
            assertEquals(1, dao.rules.size)
        }

    @Test
    fun `addRule rejects invalid rule`() =
        runTest {
            var error: String? = null
            viewModel.addRule("bad rule!!", onError = { error = it })
            advance()
            assertEquals("Invalid rule format", error)
            assertTrue(dao.rules.isEmpty())
        }
}
