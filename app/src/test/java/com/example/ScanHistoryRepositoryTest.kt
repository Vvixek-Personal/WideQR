package com.example.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.models.QrContentType
import com.example.models.ScanResult
import com.example.scanner.ScanResultProcessor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Local scan history: written only on explicit request and fully clearable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScanHistoryRepositoryTest {

    private lateinit var database: WideQrDatabase
    private lateinit var repository: ScanHistoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WideQrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ScanHistoryRepository { database.scanHistoryDao() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `recorded scans are returned newest first`() = runTest {
        repository.record("first", QrContentType.PLAIN_TEXT.label, "Gallery image")
        repository.record("second", QrContentType.URL.label, "Shared from another app")

        val entries = repository.history.first()
        assertEquals(2, entries.size)
        assertEquals("second", entries[0].rawValue)
        assertEquals("first", entries[1].rawValue)
    }

    @Test
    fun `clearAll removes every entry`() = runTest {
        repository.record("upi://pay?pa=someone@upi", QrContentType.UPI_PAYMENT.label, "Ultra-wide camera")
        repository.clearAll()
        assertTrue(repository.history.first().isEmpty())
    }

    @Test
    fun `shared text parses through the same pipeline as a camera scan`() {
        val result: ScanResult = ScanResultProcessor.process(
            "WIFI:T:WPA;S:MyNetwork;P:secret123;;",
            "Shared from another app"
        )
        assertEquals(QrContentType.WIFI, result.contentType)
        assertEquals("Shared from another app", result.sourceLabel)
    }
}
