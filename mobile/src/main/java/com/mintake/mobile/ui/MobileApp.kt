package com.mintake.mobile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mintake.shared.Medicine
import kotlinx.coroutines.delay

/** Screens reachable from the bottom bar. */
private enum class Tab(val label: String, val icon: ImageVector) {
    Today("Today", TodayIcon),
    Reminders("Reminders", RemindersIcon),
    Settings("Settings", SettingsIcon),
}

/** A reminder being added (null) or edited, shown over the current tab. */
private data class Editing(val medicine: Medicine?)

@Composable
fun MobileApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var toast by rememberSaveable { mutableStateOf<String?>(null) }

    RequestNotificationPermission()

    fun openEditor(medicine: Medicine?) {
        toast = null
        editing = Editing(medicine)
    }

    BackHandler(enabled = editing != null || tab != Tab.Today) {
        if (editing != null) editing = null else tab = Tab.Today
    }

    val current = editing
    if (current != null) {
        AddReminderScreen(
            medicineToEdit = current.medicine,
            onCancel = { editing = null },
            onSaved = { name, isEdit ->
                editing = null
                toast = if (isEdit) "$name updated" else "$name added"
            },
            onDeleted = { name ->
                editing = null
                toast = "$name deleted"
            },
        )
        return
    }

    AppShell(
        tab = tab,
        onTabSelected = { tab = it },
        onAddReminder = { openEditor(null) },
        toast = toast,
        onDismissToast = { toast = null },
    ) {
        when (tab) {
            Tab.Today -> HomeScreen()
            Tab.Reminders -> RemindersScreen(onEditReminder = { openEditor(it) })
            Tab.Settings -> SettingsScreen(onMessage = { toast = it })
        }
    }
}

/** Layout shared by every tab: content, toast, bottom bar and the add reminder button. */
@Composable
private fun AppShell(
    tab: Tab,
    onTabSelected: (Tab) -> Unit,
    onAddReminder: () -> Unit,
    toast: String?,
    onDismissToast: () -> Unit,
    content: @Composable () -> Unit,
) {
    // Toast auto-fade after 3 seconds
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3000L)
            onDismissToast()
        }
    }

    Scaffold(
        containerColor = Ink,
        bottomBar = { BottomBar(tab = tab, onTabSelected = onTabSelected) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddReminder,
                shape = RoundedCornerShape(22.dp),
                containerColor = Mint,
                contentColor = MintInk,
                // Scaffold already leaves 16dp; the design sits 20dp from the edge and the bar.
                modifier = Modifier
                    .padding(end = 4.dp, bottom = 4.dp)
                    .size(64.dp),
            ) {
                Icon(AddIcon, contentDescription = "Add reminder", modifier = Modifier.size(28.dp))
            }
        },
        // The toast goes in the snackbar slot so the Scaffold places it above the add button.
        snackbarHost = {
            AnimatedVisibility(
                visible = toast != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
            ) {
                if (toast != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
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
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            content()
        }
    }
}

/** Bottom bar from the Mintake phone design: equal-width tabs, a tinted pill behind the selected icon. */
@Composable
private fun BottomBar(tab: Tab, onTabSelected: (Tab) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavSurface)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(thickness = 1.dp, color = Surface2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .selectableGroup()
                .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab.entries.forEach { item ->
                val selected = item == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(item) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (selected) Mint.copy(alpha = 0.16f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (selected) Mint else Muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = item.label,
                        color = if (selected) TextPrimary else Muted,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
