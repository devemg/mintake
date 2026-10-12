package com.mintake.wear.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.mintake.shared.AppState
import com.mintake.shared.Dose
import com.mintake.shared.DoseState
import com.mintake.shared.DoseStatus
import com.mintake.shared.Format
import com.mintake.shared.MintakeRepository
import com.mintake.shared.RemoteOpener
import com.mintake.shared.Schedule
import com.mintake.shared.stateOf
import com.mintake.wear.DoseActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private sealed interface WearScreen {
    data object Today : WearScreen
    data class Alert(val doseKey: String) : WearScreen
    data class Confirm(val status: DoseStatus, val name: String, val snoozedUntil: Long?) :
        WearScreen
}

@Composable
fun WearApp(pendingDoseKey: String?, onDoseKeyConsumed: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { MintakeRepository.get(context) }
    val state by repository.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<WearScreen>(WearScreen.Today) }

    RequestNotificationPermission()

    LaunchedEffect(pendingDoseKey) {
        if (pendingDoseKey != null) {
            screen = WearScreen.Alert(pendingDoseKey)
            onDoseKeyConsumed()
        }
    }

    BackHandler(enabled = screen != WearScreen.Today) { screen = WearScreen.Today }

    when (val current = screen) {
        WearScreen.Today -> TodayScreen(
            repository = repository,
            state = state,
            now = now,
            onOpenDose = { screen = WearScreen.Alert(it.key) },
        )

        is WearScreen.Alert -> {
            val dose = Schedule.findDose(current.doseKey, state.medicines)
            if (dose == null) {
                LaunchedEffect(current) { screen = WearScreen.Today }
            } else {
                AlertScreen(dose = dose, state = state) { status ->
                    scope.launch {
                        DoseActions.apply(context, dose.key, status)
                        val record = repository.state.value.records[dose.key]
                        screen =
                            WearScreen.Confirm(status, dose.medicine.name, record?.snoozedUntil)
                    }
                }
            }
        }

        is WearScreen.Confirm -> ConfirmScreen(current) { screen = WearScreen.Today }
    }
}

@Composable
private fun rememberNow(): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now.longValue = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

// ---------- Today ----------

@Composable
private fun TodayScreen(
    repository: MintakeRepository,
    state: AppState,
    now: Long,
    onOpenDose: (Dose) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val doses = remember(state.medicines, today) { Schedule.dosesOn(today, state.medicines) }
    val taken = doses.count { state.stateOf(it, now) == DoseState.TAKEN }
    val progress = if (doses.isEmpty()) 0f else taken.toFloat() / doses.size
    val listState = rememberScalingLazyListState()

    val lastSynced by repository.lastSyncedAt.collectAsStateWithLifecycle()
    var connected by remember { mutableStateOf<Boolean?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var syncNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        connected = RemoteOpener.connectedNodes(context).isNotEmpty()
    }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        Box(Modifier.fillMaxSize()) {
            // Daily progress ring around the bezel (gap at the top for the clock).
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.dp),
                startAngle = 300f,
                endAngle = 240f,
                indicatorColor = Mint,
                trackColor = Track,
                strokeWidth = 4.dp,
            )
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                state = listState,
                autoCentering = AutoCenteringParams(itemIndex = 0),
            ) {
                item {
                    ListHeader {
                        Text("Today", style = MaterialTheme.typography.title2)
                    }
                }
                item {
                    Text(
                        text = if (doses.isEmpty()) "No doses today" else "$taken of ${doses.size} taken",
                        style = MaterialTheme.typography.caption1,
                        color = Muted,
                    )
                }
                if (state.medicines.isEmpty()) {
                    item {
                        Text(
                            text = "Add reminders in the Mintake phone app.",
                            style = MaterialTheme.typography.body2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(doses) { dose ->
                    DoseChip(dose = dose, state = state, now = now, onClick = { onOpenDose(dose) })
                }
                item {
                    Spacer(Modifier.height(4.dp))
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (connected) {
                                            true -> Mint
                                            false -> Coral
                                            null -> Muted
                                        }
                                    )
                            )
                            Text(
                                text = when (connected) {
                                    true -> "Phone connected"
                                    false -> "Not connected"
                                    null -> "Checking…"
                                },
                                style = MaterialTheme.typography.caption1,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = syncNote ?: if (lastSynced != null) "Synced ${
                                Format.relative(
                                    lastSynced,
                                    now
                                )
                            }" else "Not synced",
                            style = MaterialTheme.typography.caption2,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                if (!syncing) {
                                    scope.launch {
                                        syncing = true
                                        val ok = repository.syncNow()
                                        connected =
                                            RemoteOpener.connectedNodes(context).isNotEmpty()
                                        syncNote = if (ok) "Up to date" else "Couldn’t reach phone"
                                        syncing = false
                                    }
                                }
                            },
                            modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                            colors = ButtonDefaults.secondaryButtonColors(),
                        ) {
                            if (syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    indicatorColor = TextPrimary,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Sync now",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DoseChip(dose: Dose, state: AppState, now: Long, onClick: () -> Unit) {
    val doseState = state.stateOf(dose, now)
    val color = stateColor(doseState)
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(dose.medicine.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = {
            Text(
                text = "${Format.time(dose.time)} · " +
                        Format.stateLabel(doseState, dose, state.records[dose.key], now),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        icon = {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        },
        colors = ChipDefaults.chipColors(
            backgroundColor = Surface1,
            contentColor = TextPrimary,
            secondaryContentColor = color,
            iconColor = color,
        ),
    )
}

// ---------- Time for your medicine ----------

@Composable
private fun AlertScreen(dose: Dose, state: AppState, onAction: (DoseStatus) -> Unit) {
    val record = state.records[dose.key]
    val snoozedUntil: Long? =
        if (record != null && record.status == DoseStatus.SNOOZED) record.snoozedUntil else null
    val timeLabel = if (snoozedUntil != null) Format.time(snoozedUntil) else Format.time(dose.time)

    Scaffold(timeText = { TimeText() }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text(
                    "Time for your medicine",
                    style = MaterialTheme.typography.caption2,
                    color = Muted
                )
                Text(
                    text = dose.medicine.name,
                    style = MaterialTheme.typography.title2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "${dose.medicine.doseLabel} · $timeLabel",
                    style = MaterialTheme.typography.caption1,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(
                    label = "Not taken",
                    size = ButtonDefaults.SmallButtonSize,
                    background = Surface2,
                    content = Coral,
                    onClick = { onAction(DoseStatus.NOT_TAKEN) },
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Not taken",
                        modifier = Modifier.size(18.dp)
                    )
                }
                ActionButton(
                    label = "Done",
                    size = ButtonDefaults.DefaultButtonSize,
                    background = Mint,
                    content = MintInk,
                    onClick = { onAction(DoseStatus.TAKEN) },
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Done",
                        modifier = Modifier.size(22.dp)
                    )
                }
                ActionButton(
                    label = "Snooze",
                    size = ButtonDefaults.SmallButtonSize,
                    background = Surface2,
                    content = Amber,
                    onClick = { onAction(DoseStatus.SNOOZED) },
                ) {
                    Text(
                        "30m",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.caption2
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    size: androidx.compose.ui.unit.Dp,
    background: Color,
    content: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(size),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = background,
                contentColor = content
            ),
        ) { icon() }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.caption3, color = Muted, maxLines = 1)
    }
}

// ---------- Confirmation ----------

@Composable
private fun ConfirmScreen(confirm: WearScreen.Confirm, onDone: () -> Unit) {
    LaunchedEffect(confirm) {
        delay(3_000L)
        onDone()
    }
    val (title, color) = when (confirm.status) {
        DoseStatus.TAKEN -> "Marked as taken" to Mint
        DoseStatus.NOT_TAKEN -> "Marked as not taken" to Coral
        DoseStatus.SNOOZED -> "Snoozed 30 min" to Amber
    }
    val subtitle = when (confirm.status) {
        DoseStatus.SNOOZED -> "${confirm.name}\nAgain at ${Format.time(confirm.snoozedUntil ?: System.currentTimeMillis())}"
        else -> "${confirm.name}\nSynced to phone"
    }

    Scaffold(timeText = { TimeText() }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                when (confirm.status) {
                    DoseStatus.TAKEN -> Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = color
                    )

                    DoseStatus.NOT_TAKEN -> Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = color
                    )

                    DoseStatus.SNOOZED -> Text("30m", color = color, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.caption1,
                color = Muted,
                textAlign = TextAlign.Center
            )
        }
    }
}
