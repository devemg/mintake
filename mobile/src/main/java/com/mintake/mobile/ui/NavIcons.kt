package com.mintake.mobile.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

// Line icons for the bottom bar, from the Mintake phone design. Tinted by `Icon`.

private fun lineIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

private fun ImageVector.Builder.stroke(pathData: String, width: Float = 2.2f) {
    addPath(
        pathData = addPathNodes(pathData),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

/** Calendar. */
val TodayIcon: ImageVector by lazy {
    lineIcon("Today") {
        stroke("M7,5 H17 A3,3 0 0 1 20,8 V17 A3,3 0 0 1 17,20 H7 A3,3 0 0 1 4,17 V8 A3,3 0 0 1 7,5 Z")
        stroke("M4,10 H20 M9,3 V7 M15,3 V7")
    }
}

/** Capsule. */
val RemindersIcon: ImageVector by lazy {
    lineIcon("Reminders") {
        group(rotate = -45f, pivotX = 12f, pivotY = 12f) {
            stroke("M7,8.5 H17 A3.5,3.5 0 0 1 17,15.5 H7 A3.5,3.5 0 0 1 7,8.5 Z")
        }
        stroke("M9.2,9.2 L14.8,14.8")
    }
}

/** Gear. */
val SettingsIcon: ImageVector by lazy {
    lineIcon("Settings") {
        stroke("M15,12 A3,3 0 1 1 9,12 A3,3 0 1 1 15,12 Z")
        stroke(
            "M12,2.5 V5.5 M12,18.5 V21.5 M2.5,12 H5.5 M18.5,12 H21.5 " +
                "M5.3,5.3 L7.4,7.4 M16.6,16.6 L18.7,18.7 M5.3,18.7 L7.4,16.6 M16.6,7.4 L18.7,5.3",
        )
    }
}

/** Plus, for the add reminder button. */
val AddIcon: ImageVector by lazy {
    lineIcon("Add") {
        stroke("M12,5 V19 M5,12 H19", width = 2.6f)
    }
}
