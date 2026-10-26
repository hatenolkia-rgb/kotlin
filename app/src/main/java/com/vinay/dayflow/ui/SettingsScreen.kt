package com.vinay.dayflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vinay.dayflow.data.Fmt
import com.vinay.dayflow.tracking.Perms

@Composable
fun SettingsScreen(vm: AppVm, onRequestActivity: () -> Unit, onRequestNotifications: () -> Unit) {
    val ctx = LocalContext.current
    val p = vm.perms

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Settings") }

        item {
            AppCard {
                Label("Your name")
                OutlinedTextField(
                    value = vm.name,
                    onValueChange = { vm.updateName(it) },
                    placeholder = { Text("Used in the greeting") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            AppCard {
                Label("Daily goals")
                Stepper("Steps", Fmt.num(vm.stepGoal),
                    onMinus = { vm.updateGoals(steps = (vm.stepGoal - 500).coerceAtLeast(1000)) },
                    onPlus = { vm.updateGoals(steps = (vm.stepGoal + 500).coerceAtMost(30000)) })
                Stepper("Sleep", Fmt.mins(vm.sleepGoal),
                    onMinus = { vm.updateGoals(sleep = (vm.sleepGoal - 15).coerceAtLeast(240)) },
                    onPlus = { vm.updateGoals(sleep = (vm.sleepGoal + 15).coerceAtMost(720)) })
                Stepper("Screen-time limit", Fmt.mins(vm.screenLimit),
                    onMinus = { vm.updateGoals(screen = (vm.screenLimit - 15).coerceAtLeast(15)) },
                    onPlus = { vm.updateGoals(screen = (vm.screenLimit + 15).coerceAtMost(720)) })
            }
        }

        item {
            AppCard {
                Label("Permissions")
                PermRow(
                    "Physical activity", "Counts your steps",
                    granted = p.activity && p.sensor,
                    fixLabel = if (!p.sensor) null else "Allow",
                    note = if (!p.sensor) "This phone has no step sensor" else null,
                    onFix = onRequestActivity,
                )
                PermRow("Usage access", "Screen time, app usage, unlocks, sleep", p.usage, "Open") { Perms.openUsageSettings(ctx) }
                PermRow("Notifications", "Task reminders", p.notifications, "Allow", onFix = onRequestNotifications)
                PermRow(
                    "No battery restriction", "Keeps step counting running all day",
                    p.battery, "Open",
                ) { Perms.openBatterySettings(ctx) }
                TextButton(onClick = { vm.recheckPermissions() }) { Text("Re-check permissions") }
            }
        }

        item {
            AppCard {
                Label("How DayFlow works")
                Text(
                    "• Steps come from your phone's built-in step sensor; counting runs in the background with a small notification.\n" +
                        "• Sleep is estimated from the longest stretch your phone stayed untouched between 8 PM and noon.\n" +
                        "• Screen time and app usage come from Android's usage data (the same source as Digital Wellbeing).\n" +
                        "• Your day score: tasks 40%, steps 20%, sleep 20%, screen time 20%.\n" +
                        "• Everything is stored only on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onMinus) { Text("−", style = MaterialTheme.typography.titleMedium) }
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(78.dp),
        )
        FilledTonalIconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
    }
}

@Composable
private fun PermRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    fixLabel: String?,
    note: String? = null,
    onFix: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (granted) Accent.good else Accent.warn,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(note ?: subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!granted && fixLabel != null) {
            TextButton(onClick = onFix) { Text(fixLabel) }
        }
    }
    Spacer(Modifier.height(2.dp))
}
