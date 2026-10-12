package com.mintake.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mintake.shared.AppState
import com.mintake.shared.Dose
import com.mintake.shared.DoseState
import com.mintake.shared.Format
import com.mintake.shared.Medicine
import com.mintake.shared.MintakeRepository
import com.mintake.shared.RemoteOpener
import com.mintake.shared.Schedule
import com.mintake.shared.stateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(toast: String?, onAddReminder: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { MintakeRepository.get(context) }
    val state by repository.state.collectAsStateWithLifecycle()
    val now = rememberNowMillis()
    var toDelete by remember { mutableStateOf<Medicine?>(null) }
    val scope = rememberCoroutineScope()

    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val doses = remember(state.medicines, today) { Schedule.dosesOn(today, state.medicines) }
    val taken = doses.count { state.stateOf(it, now) == DoseState.TAKEN }

    Scaffold(containerColor = Ink) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Today · " + DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(today),
                    color = Muted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Medicines", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }

            WatchCard(repository = repository, now = now)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text("Today’s schedule", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("$taken of ${doses.size} taken", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(
                    progress = { if (doses.isEmpty()) 0f else taken.toFloat() / doses.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Mint,
                    trackColor = Surface2,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (doses.isEmpty()) {
                    item {
                        Text(
                            text = if (state.medicines.isEmpty())
                                "No reminders yet. Tap “Add reminder” to create your first one."
                            else "No doses scheduled for today.",
                            color = Muted,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(doses, key = { it.key }) { dose ->
                    DoseRow(dose = dose, state = state, now = now, onLongPress = { toDelete = dose.medicine })
                }
            }

            if (toast != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Mint.copy(alpha = 0.16f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
                    Text(toast, color = Mint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onAddReminder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add reminder", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    toDelete?.let { medicine ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${medicine.name}?") },
            text = { Text("This removes the reminder from your phone and your watch.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteMedicine(medicine.id) }
                    toDelete = null
                }) { Text("Delete", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
            containerColor = Surface1,
        )
    }
}

@Composable
private fun rememberNowMillis(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun WatchCard(repository: MintakeRepository, now: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lastSynced by repository.lastSyncedAt.collectAsStateWithLifecycle()
    var watchName by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        watchName = RemoteOpener.connectedNodes(context).firstOrNull()?.displayName
        checked = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface1)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Mint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .border(2.5.dp, Mint, CircleShape),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = watchName ?: if (checked) "No watch connected" else "Looking for your watch…",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = (if (watchName != null) "Connected · " else "") + "Synced ${Format.relative(lastSynced, now)}",
                    color = Muted,
                    fontSize = 14.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = {
                    if (!syncing) scope.launch {
                        syncing = true
                        message = if (repository.syncNow()) null else "Couldn’t reach your watch"
                        syncing = false
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Surface2, contentColor = TextPrimary),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (syncing) "Syncing…" else "Sync now", fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        message = if (RemoteOpener.openOnOtherDevice(context))
                            "Mintake opened on your watch"
                        else "Couldn’t reach your watch"
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Surface2, contentColor = TextPrimary),
            ) {
                Text("Open on watch", fontWeight = FontWeight.Bold)
            }
        }
        message?.let { Text(it, color = Mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DoseRow(dose: Dose, state: AppState, now: Long, onLongPress: () -> Unit) {
    val doseState = state.stateOf(dose, now)
    val (pillBg, pillFg) = when (doseState) {
        DoseState.TAKEN -> Mint.copy(alpha = 0.16f) to Mint
        DoseState.DUE -> Mint to MintInk
        DoseState.SNOOZED -> Amber.copy(alpha = 0.16f) to Amber
        DoseState.NOT_TAKEN -> Coral.copy(alpha = 0.16f) to Coral
        DoseState.UPCOMING -> Surface2 to Muted
    }
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface1)
            .then(if (doseState == DoseState.DUE) Modifier.border(2.dp, Mint, shape) else Modifier)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = Format.time(dose.time),
            color = Muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(72.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(dose.medicine.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${dose.medicine.doseLabel} · ${dose.medicine.repeatLabel}", color = Muted, fontSize = 14.sp)
        }
        Text(
            text = Format.stateLabel(doseState, dose, state.records[dose.key], now),
            color = pillFg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(pillBg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
