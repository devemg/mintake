package com.mintake.shared

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.concurrent.futures.await
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/** Opens Mintake on the paired device (watch -> phone, or phone -> watch). */
object RemoteOpener {
    private const val TAG = "RemoteOpener"

    suspend fun connectedNodes(context: Context): List<Node> = try {
        Wearable.getNodeClient(context).connectedNodes.await()
    } catch (e: Exception) {
        Log.w(TAG, "Could not list connected devices", e)
        emptyList()
    }

    suspend fun openOnOtherDevice(context: Context): Boolean {
        val nodes = connectedNodes(context)
        if (nodes.isEmpty()) return false

        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(DataLayer.OPEN_URI))
        val helper = RemoteActivityHelper(context)

        var opened = false
        for (node in nodes) {
            try {
                helper.startRemoteActivity(intent, node.id).await()
                opened = true
            } catch (e: Exception) {
                Log.w(TAG, "Could not open Mintake on ${node.displayName}", e)
            }
        }
        return opened
    }
}
