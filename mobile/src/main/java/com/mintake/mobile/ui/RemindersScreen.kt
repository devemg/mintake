package com.mintake.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.mintake.shared.Format
import com.mintake.shared.Medicine
import com.mintake.shared.MintakeRepository
import kotlinx.coroutines.launch

@Composable
fun RemindersScreen(onEditReminder: (Medicine) -> Unit) {
    val context = LocalContext.current
    val repository = remember { MintakeRepository.get(context) }
    val state by repository.state.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Medicine?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Your Reminders", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "${state.medicines.count { it.enabled }} active · ${state.medicines.size} total",
                color = Muted,
                fontSize = 14.sp,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // Leaves room so the add button does not cover the last row.
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            if (state.medicines.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No reminders created yet.",
                            fontSize = 16.sp,
                            color = Muted,
                        )
                        Text(
                            text = "Tap + to set up your first medicine reminder.",
                            fontSize = 14.sp,
                            color = Muted,
                        )
                    }
                }
            } else {
                items(state.medicines, key = { it.id }) { medicine ->
                    MedicineRow(
                        medicine = medicine,
                        onToggle = { enabled ->
                            scope.launch { repository.toggleMedicineEnabled(medicine.id, enabled) }
                        },
                        onEdit = { onEditReminder(medicine) },
                    )
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
private fun MedicineRow(
    medicine: Medicine,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface1)
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = medicine.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (medicine.enabled) TextPrimary else Muted,
                )
                if (!medicine.enabled) {
                    Text(
                        text = "Paused",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface2)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = "${Format.minutes(medicine.startMinutes)} · ${medicine.doseLabel} · ${medicine.repeatLabel}",
                color = Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit reminder",
                tint = TextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }

        Switch(
            checked = medicine.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = Mint,
                uncheckedBorderColor = Outline,
            ),
        )
    }
}
