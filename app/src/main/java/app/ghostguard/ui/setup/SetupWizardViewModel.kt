package app.ghostguard.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ghostguard.data.datastore.AppPreferences
import app.ghostguard.ui.setup.data.FilterPack
import app.ghostguard.ui.setup.data.FilterPackApplier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Steps of the one-tap setup wizard. */
enum class SetupWizardStep {
    WELCOME,
    FILTER_PACK,
    FINISH,
}

data class SetupWizardState(
    val step: SetupWizardStep = SetupWizardStep.WELCOME,
    val selectedPack: FilterPack = FilterPack.BALANCED,
    val availableListNames: Set<String> = emptySet(),
    val isApplying: Boolean = false,
)

class SetupWizardViewModel(
    private val appPrefs: AppPreferences,
    private val packApplier: FilterPackApplier,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupWizardState())
    val state: StateFlow<SetupWizardState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SetupWizardEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SetupWizardEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            // Make sure the built-in catalog is seeded so preset counts are accurate
            val names = packApplier.syncAndGetAvailableListNames()
            _state.value = _state.value.copy(availableListNames = names)
        }
    }

    fun selectPack(pack: FilterPack) {
        _state.value = _state.value.copy(selectedPack = pack)
    }

    fun nextStep() {
        val next =
            when (_state.value.step) {
                SetupWizardStep.WELCOME -> SetupWizardStep.FILTER_PACK
                SetupWizardStep.FILTER_PACK -> SetupWizardStep.FINISH
                SetupWizardStep.FINISH -> return
            }
        _state.value = _state.value.copy(step = next)
    }

    fun previousStep() {
        val prev =
            when (_state.value.step) {
                SetupWizardStep.FILTER_PACK -> SetupWizardStep.WELCOME
                SetupWizardStep.FINISH -> SetupWizardStep.FILTER_PACK
                else -> return
            }
        _state.value = _state.value.copy(step = prev)
    }

    /**
     * Applies the selected preset (enabling its lists, disabling other built-ins),
     * reloads the filter engine and persists the choice.
     */
    fun applyAndFinish() {
        if (_state.value.isApplying) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isApplying = true)
            try {
                val pack = _state.value.selectedPack
                packApplier.applyPack(pack)

                appPrefs.setFilterPack(pack.id)
                appPrefs.setSetupWizardCompleted(true)
                appPrefs.setOnboardingCompleted(true)

                _state.value = _state.value.copy(isApplying = false)
                _events.emit(SetupWizardEvent.Completed)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isApplying = false)
                _events.emit(SetupWizardEvent.Failed)
            }
        }
    }
}

sealed interface SetupWizardEvent {
    data object Completed : SetupWizardEvent

    data object Failed : SetupWizardEvent
}
