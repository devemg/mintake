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
import java.time.LocalDate

private val Context.mintakeStore: DataStore<Preferences> by preferencesDataStore(name = "mintake")

/** Single source of truth for reminders and dose records, saved locally with DataStore. */
class MintakeRepository private constructor(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val exportFormat = Json {
        encodeDefaults = true
        prettyPrint = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        scope.launch {
            try {
                val stored = context.mintakeStore.data.first()[STATE_KEY]
                if (stored != null) decode(stored)?.let { _state.value = it }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read saved state", e)
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
            records = state.records.filterValues { !it.doseKey.startsWith("$id|") },
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
            state.copy(records = prune(state.records + (doseKey to record)))
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

    private suspend fun update(transform: (AppState) -> AppState) {
        awaitLoaded()
        mutex.withLock {
            val updated = transform(_state.value)
            _state.value = updated
            context.mintakeStore.edit { it[STATE_KEY] = json.encodeToString(AppState.serializer(), updated) }
        }
    }

    private fun decode(raw: String): AppState? =
        runCatching { json.decodeFromString(AppState.serializer(), raw) }.getOrNull()

    /** Keeps only the last few days of dose records. */
    private fun prune(records: Map<String, DoseRecord>): Map<String, DoseRecord> {
        val cutoff = LocalDate.now().minusDays(3).toString()
        return records.filterValues { it.date >= cutoff }
    }

    companion object {
        private const val TAG = "MintakeRepository"
        private val STATE_KEY = stringPreferencesKey("state")

        @Volatile
        private var instance: MintakeRepository? = null

        fun get(context: Context): MintakeRepository =
            instance ?: synchronized(this) {
                instance ?: MintakeRepository(context.applicationContext).also { instance = it }
            }
    }
}
