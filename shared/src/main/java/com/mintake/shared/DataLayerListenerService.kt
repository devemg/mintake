package com.mintake.shared

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/** Merges state sent by the other device. Runs on a background thread. */
class DataLayerListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // Copy what we need now; the buffer is released when this method returns.
        val payloads = dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == DataLayer.STATE_PATH }
            .map { it.dataItem.uri.host to DataMapItem.fromDataItem(it.dataItem).dataMap.getString(DataLayer.KEY_JSON) }
        if (payloads.isEmpty()) return

        runBlocking {
            val localId = runCatching {
                Wearable.getNodeClient(this@DataLayerListenerService).localNode.await().id
            }.getOrNull()
            val repository = MintakeRepository.get(this@DataLayerListenerService)
            payloads
                .filter { (host, _) -> host != localId }
                .forEach { (_, raw) -> if (raw != null) repository.mergeRemoteJson(raw) }
        }
    }
}
