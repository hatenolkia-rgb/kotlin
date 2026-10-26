package com.vinay.dayflow.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinay.dayflow.data.DaySummary
import com.vinay.dayflow.data.Db
import com.vinay.dayflow.data.Fmt
import com.vinay.dayflow.data.Prefs
import com.vinay.dayflow.data.SummaryEngine
import com.vinay.dayflow.data.Task
import com.vinay.dayflow.reminders.Reminders
import com.vinay.dayflow.tracking.PermState
import com.vinay.dayflow.tracking.StepService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

enum class TaskFilter(val label: String) { TODAY("Today"), UPCOMING("Next 7 days"), ALL("All tasks") }

data class TaskSection(val title: String, val date: LocalDate?, val tasks: List<Task>)

private data class Loaded(
    val todaySummary: DaySummary,
    val overdue: List<Task>,
    val sections: List<TaskSection>,
    val monthStats: Map<LocalDate, Pair<Int, Int>>,
    val dayTasks: List<Task>,
    val insight: DaySummary,
)

class AppVm(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication<Application>()
    private val db: Db get() = Db.get(ctx)
    val prefs = Prefs(app)

    var today by mutableStateOf(LocalDate.now())
        private set
    var todaySummary by mutableStateOf<DaySummary?>(null)
        private set
    var overdue by mutableStateOf<List<Task>>(emptyList())
        private set

    var filter by mutableStateOf(TaskFilter.TODAY)
        private set
    var sections by mutableStateOf<List<TaskSection>>(emptyList())
        private set

    var month by mutableStateOf(YearMonth.now())
        private set
    var selectedDay by mutableStateOf(LocalDate.now())
        private set
    var monthStats by mutableStateOf<Map<LocalDate, Pair<Int, Int>>>(emptyMap())
        private set
    var dayTasks by mutableStateOf<List<Task>>(emptyList())
        private set

    var insightDate by mutableStateOf(LocalDate.now())
        private set
    var insight by mutableStateOf<DaySummary?>(null)
        private set
    var week by mutableStateOf<List<DaySummary>>(emptyList())
        private set

    var perms by mutableStateOf(PermState())
        private set
    var name by mutableStateOf(prefs.name)
        private set
    var stepGoal by mutableIntStateOf(prefs.stepGoal)
        private set
    var sleepGoal by mutableIntStateOf(prefs.sleepGoalMin)
        private set
    var screenLimit by mutableIntStateOf(prefs.screenLimitMin)
        private set

    private var job: Job? = null
    private var weekJob: Job? = null

    fun onResume() {
        val now = LocalDate.now()
        if (now != today) {
            today = now
            selectedDay = now
            insightDate = now
            month = YearMonth.from(now)
        }
        perms = PermState.read(ctx)
        StepService.start(ctx)
        refresh(withWeek = true)
        viewModelScope.launch(Dispatchers.IO) { Reminders.scheduleAll(ctx) }
    }

    fun refresh(withWeek: Boolean = false) {
        job?.cancel()
        val f = filter
        val m = month
        val sel = selectedDay
        val ins = insightDate
        val t = today
        job = viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                val todaySum = SummaryEngine.build(ctx, t)
                Loaded(
                    todaySummary = todaySum,
                    overdue = db.overdue(t),
                    sections = buildSections(f, t),
                    monthStats = buildMonth(m),
                    dayTasks = db.tasksFor(sel),
                    insight = if (ins == t) todaySum else SummaryEngine.build(ctx, ins),
                )
            }
            todaySummary = r.todaySummary
            overdue = r.overdue
            sections = r.sections
            monthStats = r.monthStats
            dayTasks = r.dayTasks
            insight = r.insight
        }
        if (withWeek) loadWeek()
    }

    private fun loadWeek() {
        weekJob?.cancel()
        val t = today
        weekJob = viewModelScope.launch {
            week = withContext(Dispatchers.IO) { SummaryEngine.week(ctx, t) }
        }
    }

    private fun buildSections(f: TaskFilter, t: LocalDate): List<TaskSection> = when (f) {
        TaskFilter.TODAY -> {
            val od = db.overdue(t)
            listOfNotNull(
                if (od.isNotEmpty()) TaskSection("Overdue", null, od) else null,
                TaskSection("Today", t, db.tasksFor(t)),
            )
        }
        TaskFilter.UPCOMING -> (1L..7L)
            .map { t.plusDays(it) }
            .map { d -> TaskSection(Fmt.dayLabel(d, t), d, db.tasksFor(d)) }
            .filter { it.tasks.isNotEmpty() }
        TaskFilter.ALL -> {
            val all = db.allTasks().map { if (it.isRepeating) it.copy(done = db.isDone(it.id, t)) else it }
            val repeating = all.filter { it.isRepeating }
            val open = all.filter { !it.isRepeating && !it.done }.sortedBy { it.date }
            val finished = all.filter { !it.isRepeating && it.done }
            listOf(
                TaskSection("Repeating (ticks count for today)", null, repeating),
                TaskSection("To do", null, open),
                TaskSection("Completed", null, finished),
            ).filter { it.tasks.isNotEmpty() }
        }
    }

    private fun buildMonth(m: YearMonth): Map<LocalDate, Pair<Int, Int>> =
        (1..m.lengthOfMonth()).associate { day ->
            val d = m.atDay(day)
            val ts = db.tasksFor(d)
            d to (ts.count { it.done } to ts.size)
        }

    // ---------- Actions ----------

    private fun io(withWeek: Boolean = true, block: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            refresh(withWeek)
        }
    }

    /** [date] = the day the tick applies to; null means the task's own date (or today for repeating tasks). */
    fun toggle(task: Task, date: LocalDate?) = io {
        val d = date ?: if (task.isRepeating) today else task.localDate
        db.setDone(task.id, d, !task.done)
        Reminders.scheduleAll(ctx)
    }

    fun save(task: Task) = io {
        db.saveTask(task)
        Reminders.scheduleAll(ctx)
    }

    fun delete(task: Task) = io {
        Reminders.cancel(ctx, task.id)
        db.deleteTask(task.id)
    }

    fun quickAdd(title: String, date: LocalDate = today) {
        if (title.isBlank()) return
        save(Task(title = title.trim(), date = date.toString()))
    }

    fun moveToToday(task: Task) = io {
        db.saveTask(task.copy(date = today.toString()))
        Reminders.scheduleAll(ctx)
    }

    fun changeFilter(f: TaskFilter) {
        filter = f
        refresh()
    }

    fun shiftMonth(delta: Long) {
        month = month.plusMonths(delta)
        refresh()
    }

    fun selectDay(d: LocalDate) {
        selectedDay = d
        val ym = YearMonth.from(d)
        if (ym != month) month = ym
        refresh()
    }

    fun shiftInsight(delta: Long) {
        val n = insightDate.plusDays(delta)
        if (n.isAfter(today)) return
        insight = null
        insightDate = n
        refresh()
    }

    fun saveReflection(wentWell: String, improve: String) = io(withWeek = false) {
        db.saveReflection(insightDate.toString(), wentWell, improve)
    }

    fun updateName(v: String) {
        name = v
        prefs.name = v
    }

    fun updateGoals(steps: Int = stepGoal, sleep: Int = sleepGoal, screen: Int = screenLimit) {
        stepGoal = steps; prefs.stepGoal = steps
        sleepGoal = sleep; prefs.sleepGoalMin = sleep
        screenLimit = screen; prefs.screenLimitMin = screen
        refresh(withWeek = true)
    }

    fun recheckPermissions() {
        perms = PermState.read(ctx)
        StepService.start(ctx)
        refresh(withWeek = true)
    }
}
