package com.vinay.dayflow.ui

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.vinay.dayflow.data.DaySummary
import com.vinay.dayflow.data.Fmt
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InsightsScreen(vm: AppVm) {
    val s = vm.insight
    val date = vm.insightDate

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Summary", "What you did, what you didn't, and how to improve") }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shiftInsight(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.dayLabel(date, vm.today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(Fmt.longDate(date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.shiftInsight(1) }, enabled = date.isBefore(vm.today)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                }
            }
        }

        if (s == null || s.date != date) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            return@LazyColumn
        }

        // ----- Score -----
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(score = s.score, diameter = 88.dp, label = "/ 100")
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(verdict(s.score), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (s.isToday) "Day in progress — this updates as you go." else "Final summary for this day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ----- Numbers vs goals -----
        item {
            AppCard {
                CardTitle("Your numbers")
                GoalRow(
                    "Tasks",
                    if (s.tasks.isEmpty()) "none planned" else "${s.done.size}/${s.tasks.size} · ${s.completion}%",
                    if (s.tasks.isEmpty()) null else s.done.size.toFloat() / s.tasks.size,
                    Accent.good,
                )
                GoalRow(
                    "Steps",
                    s.steps?.let { "${Fmt.num(it)} / ${Fmt.num(s.stepGoal)}" } ?: "not tracked",
                    s.steps?.let { it.toFloat() / s.stepGoal },
                    if ((s.steps ?: 0) >= s.stepGoal) Accent.good else MaterialTheme.colorScheme.primary,
                )
                GoalRow(
                    "Sleep",
                    s.sleepMin?.let { m ->
                        Fmt.mins(m) + (s.sleep?.let { "  (${Fmt.clock(it.start)} – ${Fmt.clock(it.end)})" } ?: "")
                    } ?: "not tracked",
                    s.sleepMin?.let { it.toFloat() / s.sleepGoal },
                    if ((s.sleepMin ?: 0) >= s.sleepGoal - 30) Accent.good else Accent.warn,
                )
                GoalRow(
                    "Screen time",
                    s.screenMin?.let { "${Fmt.mins(it)} / ${Fmt.mins(s.screenLimit)} limit" } ?: "not tracked",
                    s.screenMin?.let { it.toFloat() / s.screenLimit },
                    if ((s.screenMin ?: 0) > s.screenLimit) Accent.bad else MaterialTheme.colorScheme.secondary,
                )
                s.unlocks?.let { GoalRow("Phone unlocks", "$it times", null, Accent.muted) }
                Text(
                    "Sleep is estimated from the longest time your phone stayed untouched overnight.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // ----- Did / didn't -----
        item {
            AppCard {
                CardTitle("✅  What you did (${s.done.size})")
                if (s.done.isEmpty()) {
                    Muted("No tasks completed.")
                } else {
                    s.done.forEach { t -> Bullet(t.title, Accent.good) }
                }
            }
        }
        item {
            AppCard {
                CardTitle(if (s.isToday) "⏳  Still to do (${s.missed.size})" else "❌  What you didn't do (${s.missed.size})")
                if (s.missed.isEmpty()) {
                    Muted(if (s.tasks.isEmpty()) "Nothing was planned." else "Nothing left — everything done!")
                } else {
                    s.missed.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Bullet(t.title, priorityColor(t.priority)) }
                            if (!s.isToday && !t.isRepeating) {
                                TextButton(onClick = { vm.moveToToday(t) }) { Text("→ Today") }
                            }
                        }
                    }
                }
            }
        }

        // ----- How to improve -----
        item {
            AppCard {
                CardTitle("💡  How to improve")
                s.tips.forEachIndexed { i, tip ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("${i + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
                        Text(tip, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // ----- Reflection -----
        item(key = "reflect_${s.date}") { ReflectionCard(vm, s) }

        // ----- Week -----
        if (vm.week.isNotEmpty()) {
            item { WeekCard(vm.week) }
        }

        // ----- Apps -----
        if (s.apps.isNotEmpty()) {
            item { AppsCard(s) }
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Bullet(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GoalRow(label: String, value: String, fraction: Float?, color: Color) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (fraction != null) {
            Spacer(Modifier.height(6.dp))
            Bar(fraction, color)
        }
    }
}

@Composable
private fun ReflectionCard(vm: AppVm, s: DaySummary) {
    val ctx = LocalContext.current
    var well by remember(s.date) { mutableStateOf(s.log?.wentWell ?: "") }
    var improve by remember(s.date) { mutableStateOf(s.log?.improve ?: "") }
    AppCard {
        CardTitle("📝  Reflection")
        OutlinedTextField(
            value = well,
            onValueChange = { well = it },
            label = { Text("What went well?") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = improve,
            onValueChange = { improve = it },
            label = { Text("What will I do better tomorrow?") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = {
            vm.saveReflection(well, improve)
            Toast.makeText(ctx, "Reflection saved", Toast.LENGTH_SHORT).show()
        }) { Text("Save") }
    }
}

@Composable
private fun WeekCard(week: List<DaySummary>) {
    AppCard {
        CardTitle("📊  Last 7 days")
        Row(
            Modifier.fillMaxWidth().height(150.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            week.forEach { d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (d.hasData) "${d.score}" else "–", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .width(20.dp)
                            .height((d.score.coerceAtLeast(3) * 1.0).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (d.hasData) scoreColor(d.score) else MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (d.isToday) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        val stepDays = week.mapNotNull { it.steps }
        val sleepDays = week.mapNotNull { it.sleepMin }
        val screenDays = week.mapNotNull { it.screenMin }
        val planned = week.sumOf { it.tasks.size }
        val finished = week.sumOf { it.done.size }
        Row(Modifier.fillMaxWidth()) {
            WeekStat("Tasks", if (planned == 0) "—" else "${finished * 100 / planned}%", Modifier.weight(1f))
            WeekStat("Avg steps", if (stepDays.isEmpty()) "—" else Fmt.num(stepDays.average().toInt()), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            WeekStat("Avg sleep", if (sleepDays.isEmpty()) "—" else Fmt.mins(sleepDays.average().toInt()), Modifier.weight(1f))
            WeekStat("Avg screen", if (screenDays.isEmpty()) "—" else Fmt.mins(screenDays.average().toInt()), Modifier.weight(1f))
        }
        val scored = week.filter { it.hasData }
        if (scored.size >= 2) {
            val best = scored.maxBy { it.score }
            val worst = scored.minBy { it.score }
            Spacer(Modifier.height(10.dp))
            Text(
                "Best: ${Fmt.dayLabel(best.date)} (${best.score})   ·   Weakest: ${Fmt.dayLabel(worst.date)} (${worst.score})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AppsCard(s: DaySummary) {
    val top = s.apps.take(10)
    val max = top.maxOf { it.minutes }.coerceAtLeast(1)
    AppCard {
        CardTitle("📱  App usage")
        top.forEach { a ->
            Column(Modifier.padding(vertical = 5.dp)) {
                Row {
                    Text(a.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Fmt.mins(a.minutes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                Bar(a.minutes.toFloat() / max, MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
