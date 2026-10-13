package com.mintake.mobile.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mintake.shared.DoseUnit
import com.mintake.shared.Format
import com.mintake.shared.Medicine
import com.mintake.shared.MintakeRepository
import com.mintake.shared.RepeatType
import com.mintake.shared.Schedule
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@Composable
fun AddReminderScreen(
    medicineToEdit: Medicine? = null,
    onCancel: () -> Unit,
    onSaved: (name: String, isEdit: Boolean) -> Unit,
    onDeleted: ((name: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository = remember { MintakeRepository.get(context) }
    val scope = rememberCoroutineScope()

    val isEdit = medicineToEdit != null

    var name by rememberSaveable { mutableStateOf(medicineToEdit?.name ?: "") }
    var strength by rememberSaveable { mutableStateOf(medicineToEdit?.strength ?: "") }
    var count by rememberSaveable { mutableIntStateOf(medicineToEdit?.doseCount ?: 1) }
    var unit by rememberSaveable { mutableStateOf(medicineToEdit?.unit ?: DoseUnit.TABLET) }
    var minutes by rememberSaveable { mutableIntStateOf(medicineToEdit?.startMinutes ?: (8 * 60)) }
    var repeat by rememberSaveable { mutableStateOf(medicineToEdit?.repeat ?: RepeatType.DAILY) }
    var hours by rememberSaveable { mutableIntStateOf(medicineToEdit?.intervalHours ?: 8) }
    var enabled by rememberSaveable { mutableStateOf(medicineToEdit?.enabled ?: true) }
    var saving by remember { mutableStateOf(false) }

    val draft = Medicine(
        id = medicineToEdit?.id ?: "draft",
        name = name.trim(),
        strength = strength.trim(),
        doseCount = count,
        unit = unit,
        startMinutes = minutes,
        repeat = repeat,
        intervalHours = hours,
        enabled = enabled,
    )
    val canSave = name.isNotBlank() && !saving

    Scaffold(containerColor = Ink) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(
                        onClick = onCancel,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Surface1, contentColor = TextPrimary),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                    Text(if (isEdit) "Edit reminder" else "New reminder", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }

                if (isEdit && onDeleted != null && medicineToEdit != null) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                repository.deleteMedicine(medicineToEdit.id)
                                onDeleted(medicineToEdit.name)
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Surface2, contentColor = Coral),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete reminder")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Field(label = "Medicine name") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("e.g. Amoxicillin") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Field(label = "Strength (optional)") {
                    OutlinedTextField(
                        value = strength,
                        onValueChange = { strength = it },
                        placeholder = { Text("e.g. 250 mg") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Field(label = "Dose") {
                    Stepper(
                        value = "$count ${unit.label}${if (count > 1) "s" else ""}",
                        onMinus = { count = (count - 1).coerceAtLeast(1) },
                        onPlus = { count = (count + 1).coerceAtMost(10) },
                        minusLabel = "Fewer",
                        plusLabel = "More",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DoseUnit.entries.forEach { option ->
                            Choice(
                                label = option.label.replaceFirstChar { it.uppercase() },
                                selected = unit == option,
                                onClick = { unit = option },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Field(label = "Time") {
                    Stepper(
                        value = Format.minutes(minutes),
                        onMinus = { minutes = (minutes - 15 + 1440) % 1440 },
                        onPlus = { minutes = (minutes + 15) % 1440 },
                        minusLabel = "15 minutes earlier",
                        plusLabel = "15 minutes later",
                        onValueClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m -> minutes = h * 60 + m },
                                minutes / 60,
                                minutes % 60,
                                false,
                            ).show()
                        },
                    )
                }

                Field(label = "Repeat") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Choice("Daily", repeat == RepeatType.DAILY, { repeat = RepeatType.DAILY }, Modifier.weight(1f))
                        Choice("Weekdays", repeat == RepeatType.WEEKDAYS, { repeat = RepeatType.WEEKDAYS }, Modifier.weight(1f))
                        Choice("Every $hours h", repeat == RepeatType.INTERVAL, { repeat = RepeatType.INTERVAL }, Modifier.weight(1f))
                    }
                    if (repeat == RepeatType.INTERVAL) {
                        Stepper(
                            value = "Every $hours ${if (hours == 1) "hour" else "hours"}",
                            onMinus = { hours = (hours - 1).coerceAtLeast(1) },
                            onPlus = { hours = (hours + 1).coerceAtMost(24) },
                            minusLabel = "Fewer hours",
                            plusLabel = "More hours",
                        )
                        val times = Schedule.dosesOn(LocalDate.now(), listOf(draft.copy(enabled = true)))
                            .sortedBy { (it.time.hour * 60 + it.time.minute - minutes + 1440) % 1440 }
                            .map { Format.time(it.time) }
                        Text(
                            text = "Reminders at " + times.take(6).joinToString(" · ") + if (times.size > 6) " …" else "",
                            color = Muted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Field(label = "Status") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { enabled = !enabled }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = if (enabled) "Active (reminders enabled)" else "Inactive (reminders paused)",
                            fontSize = 16.sp,
                            color = if (enabled) TextPrimary else Muted,
                            fontWeight = FontWeight.Medium,
                        )
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink,
                                checkedTrackColor = Mint,
                                uncheckedBorderColor = Outline,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        val finalMedicine = if (isEdit && medicineToEdit != null) {
                            draft.copy(id = medicineToEdit.id)
                        } else {
                            draft.copy(id = UUID.randomUUID().toString())
                        }
                        repository.saveMedicine(finalMedicine)
                        onSaved(finalMedicine.name, isEdit)
                    }
                },
                enabled = canSave,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(if (isEdit) "Save changes" else "Save reminder", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Surface1,
    unfocusedContainerColor = Surface1,
    focusedBorderColor = Mint,
    unfocusedBorderColor = Outline,
    cursorColor = Mint,
)

@Composable
private fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusLabel: String,
    plusLabel: String,
    onValueClick: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        FilledTonalIconButton(
            onClick = onMinus,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = minusLabel },
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Surface2, contentColor = TextPrimary),
        ) { Text("−", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .then(if (onValueClick != null) Modifier.clickable(onClick = onValueClick) else Modifier),
        )
        FilledTonalIconButton(
            onClick = onPlus,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = plusLabel },
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Surface2, contentColor = TextPrimary),
        ) { Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.SemiBold,
            )
        },
        shape = RoundedCornerShape(22.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Surface2,
            labelColor = TextPrimary,
            selectedContainerColor = Mint,
            selectedLabelColor = MintInk,
        ),
        border = null,
        modifier = modifier.height(44.dp),
    )
}
