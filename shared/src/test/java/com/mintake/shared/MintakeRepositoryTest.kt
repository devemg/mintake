package com.mintake.shared

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MintakeRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Before
    fun setUp() {
        tmpFolder.create()
    }

    private fun createDataStore(testScope: TestScope, file: File): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { file }
        )
    }

    @Test
    fun decode_validState_loadsSuccessfully() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val file = tmpFolder.newFile("test_ds_1.preferences_pb")
        val dataStore = createDataStore(testScope, file)

        val repository = MintakeRepository.createForTest(dataStore)
        repository.awaitLoaded()

        assertFalse(repository.hasLoadFailed)

        val med = Medicine(id = "med_1", name = "Aspirin")
        repository.saveMedicine(med)

        val currentState = repository.state.value
        assertEquals(1, currentState.medicines.size)
        assertEquals("Aspirin", currentState.medicines[0].name)
        assertEquals(1, currentState.schemaVersion)
    }

    @Test
    fun decode_corruptState_backsUpRawStringAndSetsFailClosedFlag() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val file = tmpFolder.newFile("test_ds_2.preferences_pb")
        val dataStore = createDataStore(testScope, file)

        // Seed invalid JSON in DataStore
        val corruptJson = "{ invalid_json_content: 123 "
        dataStore.edit { it[MintakeRepository.STATE_KEY] = corruptJson }

        val repository = MintakeRepository.createForTest(dataStore)
        repository.awaitLoaded()

        assertTrue(repository.hasLoadFailed)

        // Check that backup key was created
        val preferences = dataStore.data.first()
        val backupKeyFound = preferences.asMap().keys.any { it.name.startsWith("state_backup_") }
        assertTrue("Corrupted JSON should be backed up to a state_backup key", backupKeyFound)

        // Ensure update refuses to run and throws
        try {
            repository.saveMedicine(Medicine(id = "med_2", name = "Ibuprofen"))
            fail("Expected IllegalStateException when trying to update corrupt repository state")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("initial load failed") == true)
        }

        // Verify DataStore main state was NOT overwritten with empty AppState
        assertEquals(corruptJson, preferences[MintakeRepository.STATE_KEY])
    }

    @Test
    fun deleteMedicine_keepsHistoricalDoseRecords() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val file = tmpFolder.newFile("test_ds_3.preferences_pb")
        val dataStore = createDataStore(testScope, file)

        val repository = MintakeRepository.createForTest(dataStore)
        repository.awaitLoaded()

        val medId = "med_hist"
        val med = Medicine(id = medId, name = "Vitamin D")
        repository.saveMedicine(med)

        val doseKey = "$medId|2026-10-12|08:00"
        repository.setDoseStatus(doseKey, DoseStatus.TAKEN)

        assertEquals(1, repository.state.value.records.size)

        // Delete the medicine
        repository.deleteMedicine(medId)

        val stateAfterDelete = repository.state.value
        assertEquals(0, stateAfterDelete.medicines.size)
        // History record should still exist!
        assertEquals(1, stateAfterDelete.records.size)
        assertNotNull(stateAfterDelete.records[doseKey])
    }

    @Test
    fun setDoseStatus_retainsHistoryIndefinitelyWithoutPruning() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val file = tmpFolder.newFile("test_ds_4.preferences_pb")
        val dataStore = createDataStore(testScope, file)

        val repository = MintakeRepository.createForTest(dataStore)
        repository.awaitLoaded()

        val today = LocalDate.now()
        val recentDate = today.minusDays(30).toString()
        val veryOldDate = today.minusDays(500).toString()

        val recentKey = "med1|$recentDate|08:00"
        val veryOldKey = "med1|$veryOldDate|08:00"

        repository.setDoseStatus(recentKey, DoseStatus.TAKEN)
        repository.setDoseStatus(veryOldKey, DoseStatus.TAKEN)

        val state = repository.state.value
        // All records should be kept indefinitely without automatic pruning
        assertEquals(2, state.records.size)
        assertNotNull(state.records[recentKey])
        assertNotNull(state.records[veryOldKey])
    }
}
