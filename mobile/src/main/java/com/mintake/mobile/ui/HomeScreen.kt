package com.mintake.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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

/** Vector image for watch/clock icon */
private val WatchClockIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "WatchClock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(9f, 2f)
            lineTo(15f, 2f)
            lineTo(15f, 4.5f)
            lineTo(9f, 4.5f)
            close()
        }
        path(fill = SolidColor(Color.White)) {
            moveTo(9f, 19.5f)
            lineTo(15f, 19.5f)
            lineTo(15f, 22f)
            lineTo(9f, 22f)
            close()
        }
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 4f)
            curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
            curveTo(4f, 16.42f, 7.58f, 20f, 12f, 20f)
            curveTo(16.42f, 20f, 20f, 16.42f, 20f, 12f)
            curveTo(20f, 7.58f, 16.42f, 4f, 12f, 4f)
            close()
            moveTo(12f, 18f)
            curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
            curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
            curveTo(15.31f, 6f, 18f, 8.69f, 18f, 12f)
            curveTo(18f, 15.31f, 15.31f, 18f, 12f, 18f)
            close()
        }
        path(fill = SolidColor(Color.White)) {
            moveTo(11.25f, 7.5f)
            lineTo(11.25f, 12.5f)
            lineTo(15f, 14.25f)
            lineTo(15.75f, 13f)
            lineTo(12.5f, 11.5f)
            lineTo(12.5f, 7.5f)
            close()
        }
    }.build()
}

@Composable
fun HomeScreen(toast: String?, onAddReminder: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { MintakeRepository.get(context) }
    val state by repository.state.collectAsStateWithLifecycle()
    val now = rememberNowMillis()
    var toDelete by remember { mutableStateOf<Medicine?>(null) }
    val scope = rememberCoroutineScope()

    var connected by remember { mutableStateOf<Boolean?>(null) }
    var retrying by remember { mutableStateOf(false) }

    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val doses = remember(state.medicines, today) { Schedule.dosesOn(today, state.medicines) }
    val taken = doses.count { state.stateOf(it, now) == DoseState.TAKEN }

    fun refreshConnection() {
        if (!retrying) {
            scope.launch {
                retrying = true
                repository.syncNow()
                connected = RemoteOpener.connectedNodes(context).isNotEmpty()
                retrying = false
            }
        }
    }

    LaunchedEffect(Unit) {
        connected = RemoteOpener.connectedNodes(context).isNotEmpty()
    }

    Scaffold(containerColor = Ink) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Top Header: App Title & Date on Left, Watch Status & Retry on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = "Today · " + DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(today),
                        color = Muted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("MinTake", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Show Retry button if watch is disconnected
                    if (connected == false) {
                        FilledTonalButton(
                            onClick = { refreshConnection() },
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Surface2,
                                contentColor = TextPrimary,
                            ),
                        ) {
                            if (retrying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = TextPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Retry connection",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("Retry", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Watch / Clock Icon with Red or Green connection status indicator
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Surface1)
                            .clickable { refreshConnection() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = WatchClockIcon,
                            contentDescription = when (connected) {
                                true -> "Watch connected"
                                false -> "Watch disconnected"
                                null -> "Checking watch connection"
                            },
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp),
                        )

                        // Red/Green/Gray connection dot
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connected) {
                                        true -> Mint       // Green
                                        false -> Coral     // Red
                                        null -> Muted      // Gray/checking
                                    }
                                )
                                .border(1.5.dp, Surface1, CircleShape),
                        )
                    }
                }
            }

            // Schedule Progress Card
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

            // Doses List
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
