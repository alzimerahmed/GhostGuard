package app.ghostguard.ui.setup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.ghostguard.data.datastore.AppPreferences
import app.ghostguard.ui.setup.data.FilterPack
import app.ghostguard.ui.setup.data.FilterPackApplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeFilterPackApplier : FilterPackApplier {
    val enabledNames = mutableSetOf<String>()
    var availableNames: Set<String> = emptySet()
    var shouldFail = false

    override suspend fun syncAndGetAvailableListNames(): Set<String> = availableNames

    override suspend fun applyPack(pack: FilterPack) {
        if (shouldFail) throw java.io.IOException("simulated failure")
        enabledNames.clear()
        enabledNames.addAll(pack.filterListNames)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SetupWizardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appPrefs: AppPreferences
    private lateinit var packApplier: FakeFilterPackApplier
    private lateinit var viewModel: SetupWizardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        appPrefs = AppPreferences(ApplicationProvider.getApplicationContext())
        packApplier =
            FakeFilterPackApplier().apply {
                availableNames = FilterPack.entries.flatMap { it.filterListNames }.toSet()
            }
        viewModel = SetupWizardViewModel(appPrefs, packApplier)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsWelcomeWithBalancedPack() {
        assertEquals(SetupWizardStep.WELCOME, viewModel.state.value.step)
        assertEquals(FilterPack.BALANCED, viewModel.state.value.selectedPack)
    }

    @Test
    fun nextStepAdvancesThroughAllSteps() {
        viewModel.nextStep()
        assertEquals(SetupWizardStep.FILTER_PACK, viewModel.state.value.step)
        viewModel.nextStep()
        assertEquals(SetupWizardStep.FINISH, viewModel.state.value.step)
        viewModel.nextStep()
        assertEquals(SetupWizardStep.FINISH, viewModel.state.value.step)
    }

    @Test
    fun previousStepGoesBack() {
        viewModel.nextStep()
        viewModel.previousStep()
        assertEquals(SetupWizardStep.WELCOME, viewModel.state.value.step)
    }

    @Test
    fun selectPackUpdatesState() {
        viewModel.selectPack(FilterPack.STRICT)
        assertEquals(FilterPack.STRICT, viewModel.state.value.selectedPack)
    }

    @Test
    fun matchedCountCountsOnlyAvailableLists() {
        val available = setOf("EasyList", "EasyPrivacy")
        assertEquals(1, FilterPack.matchedCount(FilterPack.MINIMAL, setOf("Hagezi Light")))
        assertEquals(2, FilterPack.matchedCount(FilterPack.BALANCED, available))
        assertEquals(0, FilterPack.matchedCount(FilterPack.MINIMAL, emptySet()))
    }

    @Test
    fun fromIdFallsBackToBalanced() {
        assertEquals(FilterPack.STRICT, FilterPack.fromId("strict"))
        assertEquals(FilterPack.BALANCED, FilterPack.fromId("unknown"))
        assertEquals(FilterPack.BALANCED, FilterPack.fromId(""))
    }

    @Test
    fun presetsHaveDistinctCuratedLists() {
        assertTrue(FilterPack.MINIMAL.filterListNames.size < FilterPack.BALANCED.filterListNames.size)
        assertTrue(FilterPack.BALANCED.filterListNames.size < FilterPack.STRICT.filterListNames.size)
        assertFalse(FilterPack.MINIMAL.filterListNames == FilterPack.STRICT.filterListNames)
    }

    @Test
    fun applyAndFinishEnablesPresetListsAndPersists() =
        runTest(testDispatcher.scheduler) {
            viewModel.selectPack(FilterPack.MINIMAL)
            viewModel.applyAndFinish()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(FilterPack.MINIMAL.filterListNames, packApplier.enabledNames)
            assertTrue(appPrefs.setupWizardCompleted.first { it })
            assertEquals(FilterPack.MINIMAL.id, appPrefs.filterPack.first { it.isNotEmpty() })
        }

    @Test
    fun applyAndFinishEmitsFailedEventOnFailure() =
        runTest(testDispatcher.scheduler) {
            packApplier.shouldFail = true
            val collected = mutableListOf<SetupWizardEvent>()
            val job =
                launch {
                    viewModel.events.collect { collected.add(it) }
                }
            viewModel.applyAndFinish()
            testDispatcher.scheduler.advanceUntilIdle()
            job.cancel()

            assertEquals(SetupWizardEvent.Failed, collected.first())
            assertFalse(appPrefs.setupWizardCompleted.first())
        }
}
