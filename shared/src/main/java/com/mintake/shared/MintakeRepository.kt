package com.mintake.shared

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
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
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.mintakeStore: DataStore<Preferences> by preferencesDataStore(name = "mintake")

/**
 * Single source of truth on each device. Saves state locally and pushes it to the
 * other device through the Wear OS Data Layer. Changes from the other device arrive
 * through [DataLayerListenerService] and are merged in.
 *
 * Merge rules: the medicine list with the newest edit wins (only the phone edits it);
 * dose records are merged one by one, newest wins.
 */
class MintakeRepository private constructor(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow<Long?>(null)
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

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

    suspend fun addMedicine(medicine: Medicine) = update(push = true) {
        it.copy(medicines = it.medicines + medicine, medicinesUpdatedAt = System.currentTimeMillis())
    }

    suspend fun deleteMedicine(id: String) = update(push = true) { state ->
        state.copy(
            medicines = state.medicines.filterNot { it.id == id },
            medicinesUpdatedAt = System.currentTimeMillis(),
            records = state.records.filterValues { !it.doseKey.startsWith("$id|") },
        )
    }

    suspend fun setDoseStatus(doseKey: String, status: DoseStatus, snoozedUntil: Long? = null) =
        update(push = true) { state ->
            val record = DoseRecord(
                doseKey = doseKey,
                date = Schedule.dateOfKey(doseKey),
                status = status,
                snoozedUntil = snoozedUntil,
                updatedAt = System.currentTimeMillis(),
            )
            state.copy(records = prune(state.records + (doseKey to record)))
        }

    suspend fun mergeRemoteJson(raw: String) {
        val remote = decode(raw) ?: return
        update(push = false) { merge(it, remote) }
        _lastSyncedAt.value = System.currentTimeMillis()
    }

    /** Pulls the other device's latest copy, merges it, then sends ours back. */
    suspend fun syncNow(): Boolean {
        return try {
            awaitLoaded()
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) return false

            val localId = nodeClient.localNode.await().id
            val uri = Uri.Builder()
                .scheme(PutDataRequest.WEAR_URI_SCHEME)
                .path(DataLayer.STATE_PATH)
                .build()
            val buffer = Wearable.getDataClient(context).getDataItems(uri).await()
            val remotes = try {
                buffer.filter { it.uri.host != localId }
                    .mapNotNull { DataMapItem.fromDataItem(it).dataMap.getString(DataLayer.KEY_JSON) }
            } finally {
                buffer.release()
            }
            remotes.forEach { mergeRemoteJson(it) }
            push(_state.value)
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed", e)
            false
        }
    }

    private suspend fun update(push: Boolean, transform: (AppState) -> AppState) {
        awaitLoaded()
        val next = mutex.withLock {
            val updated = transform(_state.value)
            _state.value = updated
            context.mintakeStore.edit { it[STATE_KEY] = json.encodeToString(AppState.serializer(), updated) }
            updated
        }
        if (push) push(next)
    }

    private suspend fun push(state: AppState): Boolean = try {
        val request = PutDataMapRequest.create(DataLayer.STATE_PATH).apply {
            dataMap.putString(DataLayer.KEY_JSON, json.encodeToString(AppState.serializer(), state))
            dataMap.putLong(DataLayer.KEY_TIMESTAMP, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
        _lastSyncedAt.value = System.currentTimeMillis()
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not send state to the other device", e)
        false
    }

    private fun decode(raw: String): AppState? =
        runCatching { json.decodeFromString(AppState.serializer(), raw) }.getOrNull()

    private fun merge(local: AppState, remote: AppState): AppState {
        val remoteMedsNewer = remote.medicinesUpdatedAt > local.medicinesUpdatedAt
        val records = (local.records.keys + remote.records.keys).associateWith { key ->
            listOfNotNull(local.records[key], remote.records[key]).maxBy { it.updatedAt }
        }
        return AppState(
            medicines = if (remoteMedsNewer) remote.medicines else local.medicines,
            medicinesUpdatedAt = maxOf(local.medicinesUpdatedAt, remote.medicinesUpdatedAt),
            records = prune(records),
        )
    }

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
