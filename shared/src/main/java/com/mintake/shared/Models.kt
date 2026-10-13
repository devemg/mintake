package com.mintake.shared

import kotlinx.serialization.Serializable

@Serializable
enum class DoseUnit(val label: String) {
    TABLET("tablet"),
    CAPSULE("capsule"),
    SOFTGEL("softgel"),
}

@Serializable
enum class RepeatType { DAILY, WEEKDAYS, INTERVAL }

/** A medicine reminder. */
@Serializable
data class Medicine(
    val id: String,
    val name: String,
    val strength: String = "",
    val doseCount: Int = 1,
    val unit: DoseUnit = DoseUnit.TABLET,
    /** Time of the first dose of the day, in minutes after midnight. */
    val startMinutes: Int = 8 * 60,
    val repeat: RepeatType = RepeatType.DAILY,
    /** Hours between doses when [repeat] is [RepeatType.INTERVAL]. */
    val intervalHours: Int = 8,
    /** Whether reminders for this medicine are active. */
    val enabled: Boolean = true,
) {
    val doseLabel: String
        get() {
            val amount = "$doseCount ${unit.label}${if (doseCount > 1) "s" else ""}"
            return if (strength.isBlank()) amount else "${strength.trim()} · $amount"
        }

    val repeatLabel: String
        get() = when (repeat) {
            RepeatType.DAILY -> "Daily"
            RepeatType.WEEKDAYS -> "Weekdays"
            RepeatType.INTERVAL -> "Every $intervalHours h"
        }
}

@Serializable
enum class DoseStatus { TAKEN, NOT_TAKEN, SNOOZED }

/** What the user did with one scheduled dose (set from the notification). */
@Serializable
data class DoseRecord(
    val doseKey: String,
    /** ISO date (yyyy-MM-dd) of the dose, used to drop old records. */
    val date: String,
    val status: DoseStatus,
    val snoozedUntil: Long? = null,
    val updatedAt: Long,
)

/** Everything the app stores. */
@Serializable
data class AppState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val medicines: List<Medicine> = emptyList(),
    val medicinesUpdatedAt: Long = 0L,
    val records: Map<String, DoseRecord> = emptyMap(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** Data the user can download from the phone's settings. */
@Serializable
data class ExportData(
    val reminders: List<Medicine>,
    val history: List<DoseRecord>,
)
