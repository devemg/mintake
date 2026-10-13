package com.mintake.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import com.mintake.shared.Schedule
import com.mintake.shared.stateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repository = remember { MintakeRepository.get(context) }
    val state by repository.state.collectAsStateWithLifecycle()
    val now = rememberNowMillis()
    var toDelete by remember { mutableStateOf<Medicine?>(null) }
    val scope = rememberCoroutineScope()

    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val doses = remember(state.medicines, today) { Schedule.dosesOn(today, state.medicines) }
    val taken = doses.count { state.stateOf(it, now) == DoseState.TAKEN }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top Header: App Title & Date
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Today · " + DateTimeFormatter.ofPattern(
                    "EEE, MMM d",
                    Locale.getDefault()
                ).format(today),
                color = Muted,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("MinTake", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // Leaves room so the add button does not cover the last row.
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            // Progress Bar Card Item
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text("Today’s schedule", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "$taken of ${doses.size} taken",
                            color = Muted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
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
            }

            // Doses List
            if (doses.isEmpty()) {
                item {
                    Text(
                        text = if (state.medicines.isEmpty())
                            "No reminders yet. Tap + to create your first one."
                        else "No active doses scheduled for today.",
                        color = Muted,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(doses, key = { it.key }) { dose ->
                    DoseRow(
                        dose = dose,
                        state = state,
                        now = now,
                        onLongPress = { toDelete = dose.medicine })
                }
            }
        }
    }

    toDelete?.let { medicine ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${medicine.name}?") },
            text = { Text("This removes the reminder and its notifications.") },
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
            Text(
                dose.medicine.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${dose.medicine.doseLabel} · ${dose.medicine.repeatLabel}",
                color = Muted,
                fontSize = 14.sp
            )
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
