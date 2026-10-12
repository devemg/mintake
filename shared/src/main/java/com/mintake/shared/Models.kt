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

/** A medicine reminder. Created on the phone, synced to the watch. */
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

/** What the user did with one scheduled dose (set on the watch). */
@Serializable
data class DoseRecord(
    val doseKey: String,
    /** ISO date (yyyy-MM-dd) of the dose, used to drop old records. */
    val date: String,
    val status: DoseStatus,
    val snoozedUntil: Long? = null,
    val updatedAt: Long,
)

/** Everything the two devices keep in sync. */
@Serializable
data class AppState(
    val medicines: List<Medicine> = emptyList(),
    val medicinesUpdatedAt: Long = 0L,
    val records: Map<String, DoseRecord> = emptyMap(),
)

object DataLayer {
    const val STATE_PATH = "/mintake/state"
    const val KEY_JSON = "json"
    const val KEY_TIMESTAMP = "ts"

    /** Deep link both apps answer to, used to open the app on the other device. */
    const val OPEN_URI = "mintake://open"
}
