package com.example.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local-only scan history row. Stored on-device in Room; never uploaded.
 * Only written when the user opts in via Settings ("Save scan history").
 */
@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawValue: String,
    val contentTypeLabel: String,
    val sourceLabel: String,
    val scannedAtEpochMillis: Long
)
