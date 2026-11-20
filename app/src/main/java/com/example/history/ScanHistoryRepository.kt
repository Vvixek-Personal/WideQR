package com.example.history

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ScanHistoryRepository(
    private val daoProvider: () -> ScanHistoryDao
) {

    constructor(context: Context) : this({
        WideQrDatabase.getInstance(context).scanHistoryDao()
    })

    // Resolved on first use so app start is never blocked by database creation,
    // and nothing touches Room unless the user opted in to history.
    private val dao: ScanHistoryDao by lazy(daoProvider)

    val history: Flow<List<ScanHistoryEntity>>
        get() = dao.observeAll()

    suspend fun record(rawValue: String, contentTypeLabel: String, sourceLabel: String) {
        dao.insert(
            ScanHistoryEntity(
                rawValue = rawValue,
                contentTypeLabel = contentTypeLabel,
                sourceLabel = sourceLabel,
                scannedAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
