package com.mintake.shared

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private val Context.mintakeStore: DataStore<Preferences> by preferencesDataStore(name = "mintake")

/** Single source of truth for reminders and dose records, saved locally with DataStore. */
class MintakeRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    private val exportFormat = Json {
        encodeDefaults = true
        prettyPrint = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()

    @Volatile
    internal var hasLoadFailed: Boolean = false
        private set

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        scope.launch {
            try {
                val stored = dataStore.data.first()[STATE_KEY]
                if (stored != null) {
                    val decoded = decode(stored)
                    if (decoded != null) {
                        _state.value = migrateIfNeeded(decoded)
                    } else {
                        hasLoadFailed = true
                        val backupKey = stringPreferencesKey("state_backup_${System.currentTimeMillis()}")
                        runCatching {
                            dataStore.edit { it[backupKey] = stored }
                        }
                        Log.e(TAG, "Saved state could not be decoded; backed up raw string to key before continuing")
                    }
                }
            } catch (e: Exception) {
                hasLoadFailed = true
                Log.e(TAG, "Could not read saved state from DataStore", e)
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    suspend fun awaitLoaded() = loaded.await()

    suspend fun addMedicine(medicine: Medicine) = saveMedicine(medicine)

    suspend fun saveMedicine(medicine: Medicine) = update { state ->
        val exists = state.medicines.any { it.id == medicine.id }
        val updatedList = if (exists) {
            state.medicines.map { if (it.id == medicine.id) medicine else it }
        } else {
            state.medicines + medicine
        }
        state.copy(medicines = updatedList, medicinesUpdatedAt = System.currentTimeMillis())
    }

    suspend fun toggleMedicineEnabled(id: String, enabled: Boolean) = update { state ->
        state.copy(
            medicines = state.medicines.map { if (it.id == id) it.copy(enabled = enabled) else it },
            medicinesUpdatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun deleteMedicine(id: String) = update { state ->
        state.copy(
            medicines = state.medicines.filterNot { it.id == id },
            medicinesUpdatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun setDoseStatus(doseKey: String, status: DoseStatus, snoozedUntil: Long? = null) =
        update { state ->
            val record = DoseRecord(
                doseKey = doseKey,
                date = Schedule.dateOfKey(doseKey),
                status = status,
                snoozedUntil = snoozedUntil,
                updatedAt = System.currentTimeMillis(),
            )
            state.copy(records = state.records + (doseKey to record))
        }

    /** Reminders and dose history as a readable JSON document. */
    suspend fun exportJson(): String {
        awaitLoaded()
        val current = _state.value
        val export = ExportData(
            reminders = current.medicines,
            history = current.records.values.sortedBy { it.updatedAt },
        )
        return exportFormat.encodeToString(ExportData.serializer(), export)
    }

    /** Reset state explicitly if load failed and user wants a fresh start. */
    suspend fun resetState() {
        awaitLoaded()
        mutex.withLock {
            hasLoadFailed = false
            val fresh = AppState()
            _state.value = fresh
            dataStore.edit { it[STATE_KEY] = json.encodeToString(AppState.serializer(), fresh) }
        }
    }

    private suspend fun update(transform: (AppState) -> AppState) {
        awaitLoaded()
        if (hasLoadFailed) {
            Log.e(TAG, "Skipping state update because initial state load failed. Refusing to overwrite stored data.")
            throw IllegalStateException("Cannot update repository state when initial load failed; refusing to overwrite stored data.")
        }
        mutex.withLock {
            val updated = transform(_state.value)
            _state.value = updated
            dataStore.edit { it[STATE_KEY] = json.encodeToString(AppState.serializer(), updated) }
        }
    }

    private fun decode(raw: String): AppState? =
        runCatching { json.decodeFromString(AppState.serializer(), raw) }.getOrNull()

    private fun migrateIfNeeded(state: AppState): AppState {
        var current = state
        if (current.schemaVersion < AppState.CURRENT_SCHEMA_VERSION) {
            current = current.copy(schemaVersion = AppState.CURRENT_SCHEMA_VERSION)
        }
        return current
    }

    companion object {
        private const val TAG = "MintakeRepository"
        internal val STATE_KEY = stringPreferencesKey("state")

        @Volatile
        private var instance: MintakeRepository? = null

        fun get(context: Context): MintakeRepository =
            instance ?: synchronized(this) {
                instance ?: MintakeRepository(context.applicationContext.mintakeStore).also { instance = it }
            }

        fun createForTest(dataStore: DataStore<Preferences>): MintakeRepository =
            MintakeRepository(dataStore)
    }
}
