package app.ghostguard.ui.setup.data

import android.content.Context
import app.ghostguard.data.dao.FilterListDao
import app.ghostguard.data.repository.FilterListRepository
import app.ghostguard.service.ServiceController
import timber.log.Timber

/**
 * Applies a curated [FilterPack] to the filter-list database and reloads the engine.
 * Abstracted so the setup ViewModel can be unit-tested without network access.
 */
interface FilterPackApplier {
    /** Seeds the built-in catalog and returns all list names currently in the DB. */
    suspend fun syncAndGetAvailableListNames(): Set<String>

    /** Enables exactly the preset's built-in lists and reloads the filter engine. */
    suspend fun applyPack(pack: FilterPack)
}

class DefaultFilterPackApplier(
    private val context: Context,
    private val filterListDao: app.ghostguard.data.dao.FilterListDao,
    private val filterRepo: app.ghostguard.data.repository.FilterListRepository,
) : FilterPackApplier {
    override suspend fun syncAndGetAvailableListNames(): Set<String> {
        filterRepo.seedDefaultsIfNeeded()
        return filterListDao.getAllSync().map { it.name }.toSet()
    }

    override suspend fun applyPack(pack: FilterPack) {
        val all = filterListDao.getAllSync()
        for (filter in all) {
            if (!filter.isBuiltIn) continue
            val shouldBeEnabled = filter.name in pack.filterListNames
            if (filter.isEnabled != shouldBeEnabled) {
                filterListDao.setEnabled(filter.id, shouldBeEnabled)
            }
        }
        filterRepo.loadAllEnabledFilters()
        ServiceController.requestRestart(context)
        Timber.d("Applied filter pack ${pack.id}")
    }
}
