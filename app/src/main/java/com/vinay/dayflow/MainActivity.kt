package com.vinay.dayflow

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vinay.dayflow.data.Task
import com.vinay.dayflow.tracking.Notifs
import com.vinay.dayflow.tracking.Perms
import com.vinay.dayflow.ui.AppVm
import com.vinay.dayflow.ui.CalendarScreen
import com.vinay.dayflow.ui.DayFlowTheme
import com.vinay.dayflow.ui.InsightsScreen
import com.vinay.dayflow.ui.SettingsScreen
import com.vinay.dayflow.ui.TaskEditor
import com.vinay.dayflow.ui.TasksScreen
import com.vinay.dayflow.ui.TodayScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifs.ensureChannels(this)
        setContent {
            DayFlowTheme { AppRoot() }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Today", Icons.Filled.Home),
    Tab("To-do", Icons.Filled.CheckCircle),
    Tab("Calendar", Icons.Filled.DateRange),
    Tab("Summary", Icons.Filled.Star),
    Tab("Settings", Icons.Filled.Settings),
)

@Composable
fun AppRoot(vm: AppVm = viewModel()) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Task?>(null) }

    // Refresh whenever the app comes to the foreground (new day, new steps, permissions changed).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.onResume() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.recheckPermissions()
    }
    val multiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.recheckPermissions()
    }

    // Ask for permission; if Android won't show the dialog any more, open the app's settings page instead.
    fun request(permission: String) {
        val act = ctx as? Activity
        val canAsk = act != null &&
            (!vm.prefs.wasAsked(permission) || ActivityCompat.shouldShowRequestPermissionRationale(act, permission))
        if (canAsk) {
            vm.prefs.markAsked(permission)
            singleLauncher.launch(permission)
        } else {
            Perms.openAppSettings(ctx)
        }
    }

    val requestActivity: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 29) request(Manifest.permission.ACTIVITY_RECOGNITION)
    }
    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33) request(Manifest.permission.POST_NOTIFICATIONS)
    }

    // First launch: ask for steps + notifications together.
    LaunchedEffect(Unit) {
        if (!vm.prefs.firstRunDone) {
            vm.prefs.firstRunDone = true
            val list = buildList {
                if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            list.forEach { vm.prefs.markAsked(it) }
            if (list.isNotEmpty()) multiLauncher.launch(list.toTypedArray())
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab in 0..2) {
                FloatingActionButton(onClick = {
                    val d = if (tab == 2) vm.selectedDay else vm.today
                    editing = Task(title = "", date = d.toString())
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add task")
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> TodayScreen(
                    vm = vm,
                    onEdit = { editing = it },
                    onRequestActivity = requestActivity,
                    onRequestNotifications = requestNotifications,
                    onOpenInsights = { tab = 3 },
                )
                1 -> TasksScreen(vm = vm, onEdit = { editing = it })
                2 -> CalendarScreen(
                    vm = vm,
                    onEdit = { editing = it },
                    onAdd = { d -> editing = Task(title = "", date = d.toString()) },
                )
                3 -> InsightsScreen(vm = vm)
                else -> SettingsScreen(
                    vm = vm,
                    onRequestActivity = requestActivity,
                    onRequestNotifications = requestNotifications,
                )
            }
        }
    }

    val current = editing
    if (current != null) {
        key(current.id, current.date) {
            TaskEditor(
                initial = current,
                onDismiss = { editing = null },
                onSave = {
                    vm.save(it)
                    editing = null
                },
                onDelete = if (current.id != 0L) {
                    {
                        vm.delete(current)
                        editing = null
                    }
                } else null,
            )
        }
    }
}
