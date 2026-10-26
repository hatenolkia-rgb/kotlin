package com.vinay.dayflow.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/**
 * A task. One-off tasks live on [date]. Repeating tasks start on [date] and recur by [repeat].
 * Completion is stored per day in the completions table, so a daily task can be ticked every day.
 */
data class Task(
    val id: Long = 0,
    val title: String,
    val notes: String = "",
    val date: String,
    val time: String = "",          // "HH:mm" or blank
    val priority: Int = 1,          // 0 low, 1 medium, 2 high
    val category: String = "Personal",
    val repeat: String = REPEAT_NONE,
    val created: Long = System.currentTimeMillis(),
    val done: Boolean = false,
) {
    val localDate: LocalDate get() = LocalDate.parse(date)
    val isRepeating: Boolean get() = repeat != REPEAT_NONE

    fun occursOn(d: LocalDate): Boolean {
        val start = localDate
        if (d.isBefore(start)) return false
        return when (repeat) {
            REPEAT_DAILY -> true
            REPEAT_WEEKDAYS -> d.dayOfWeek.value <= 5
            REPEAT_WEEKLY -> d.dayOfWeek == start.dayOfWeek
            else -> d == start
        }
    }

    companion object {
        const val REPEAT_NONE = "none"
        const val REPEAT_DAILY = "daily"
        const val REPEAT_WEEKDAYS = "weekdays"
        const val REPEAT_WEEKLY = "weekly"
        val REPEATS = listOf(
            REPEAT_NONE to "Once",
            REPEAT_DAILY to "Daily",
            REPEAT_WEEKDAYS to "Mon–Fri",
            REPEAT_WEEKLY to "Weekly",
        )
        val CATEGORIES = listOf("Work", "Personal", "Health", "Learning", "Errands", "Other")
        fun repeatLabel(r: String): String? =
            if (r == REPEAT_NONE) null else REPEATS.firstOrNull { it.first == r }?.second
    }
}

/** Saved numbers + reflection for one day. -1 means "not measured". */
data class DayLog(
    val date: String,
    val steps: Int = -1,
    val sleepMin: Int = -1,
    val screenMin: Int = -1,
    val unlocks: Int = -1,
    val wentWell: String = "",
    val improve: String = "",
)

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "dayflow.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, " +
                "notes TEXT NOT NULL DEFAULT '', due_date TEXT NOT NULL, due_time TEXT NOT NULL DEFAULT '', " +
                "priority INTEGER NOT NULL DEFAULT 1, category TEXT NOT NULL DEFAULT 'Personal', " +
                "rpt TEXT NOT NULL DEFAULT 'none', created INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE completions (task_id INTEGER NOT NULL, day TEXT NOT NULL, at INTEGER NOT NULL, " +
                "PRIMARY KEY(task_id, day))"
        )
        db.execSQL(
            "CREATE TABLE daylog (day TEXT PRIMARY KEY, steps INTEGER NOT NULL DEFAULT -1, " +
                "sleep_min INTEGER NOT NULL DEFAULT -1, screen_min INTEGER NOT NULL DEFAULT -1, " +
                "unlocks INTEGER NOT NULL DEFAULT -1, went_well TEXT NOT NULL DEFAULT '', " +
                "improve TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL("CREATE INDEX idx_tasks_date ON tasks(due_date)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun Cursor.str(col: String): String = getString(getColumnIndexOrThrow(col)) ?: ""
    private fun Cursor.int(col: String): Int = getInt(getColumnIndexOrThrow(col))
    private fun Cursor.long(col: String): Long = getLong(getColumnIndexOrThrow(col))

    private fun readTasks(c: Cursor): List<Task> = c.use { cur ->
        val dnIdx = cur.getColumnIndex("dn")
        val out = ArrayList<Task>()
        while (cur.moveToNext()) {
            out.add(
                Task(
                    id = cur.long("id"),
                    title = cur.str("title"),
                    notes = cur.str("notes"),
                    date = cur.str("due_date"),
                    time = cur.str("due_time"),
                    priority = cur.int("priority"),
                    category = cur.str("category"),
                    repeat = cur.str("rpt"),
                    created = cur.long("created"),
                    done = dnIdx >= 0 && cur.getInt(dnIdx) > 0,
                )
            )
        }
        out
    }

    // ---------- Tasks ----------

    fun saveTask(t: Task): Long {
        val cv = ContentValues().apply {
            put("title", t.title.trim())
            put("notes", t.notes.trim())
            put("due_date", t.date)
            put("due_time", t.time)
            put("priority", t.priority)
            put("category", t.category)
            put("rpt", t.repeat)
            put("created", t.created)
        }
        val w = writableDatabase
        return if (t.id == 0L) {
            w.insert("tasks", null, cv)
        } else {
            w.update("tasks", cv, "id=?", arrayOf(t.id.toString()))
            t.id
        }
    }

    fun getTask(id: Long): Task? =
        readTasks(readableDatabase.rawQuery("SELECT * FROM tasks WHERE id=?", arrayOf(id.toString()))).firstOrNull()

    fun deleteTask(id: Long) {
        val w = writableDatabase
        w.delete("tasks", "id=?", arrayOf(id.toString()))
        w.delete("completions", "task_id=?", arrayOf(id.toString()))
    }

    fun setDone(id: Long, day: LocalDate, done: Boolean) {
        val w = writableDatabase
        if (done) {
            val cv = ContentValues().apply {
                put("task_id", id)
                put("day", day.toString())
                put("at", System.currentTimeMillis())
            }
            w.insertWithOnConflict("completions", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } else {
            w.delete("completions", "task_id=? AND day=?", arrayOf(id.toString(), day.toString()))
        }
    }

    fun isDone(id: Long, day: LocalDate): Boolean =
        DatabaseUtils.queryNumEntries(
            readableDatabase, "completions", "task_id=? AND day=?", arrayOf(id.toString(), day.toString())
        ) > 0

    private fun doneIds(day: String): Set<Long> =
        readableDatabase.rawQuery("SELECT task_id FROM completions WHERE day=?", arrayOf(day)).use { c ->
            val s = HashSet<Long>()
            while (c.moveToNext()) s.add(c.getLong(0))
            s
        }

    /** Every task that falls on [day] (one-off + repeating), with its done state for that day. */
    fun tasksFor(day: LocalDate): List<Task> {
        val k = day.toString()
        val raw = readTasks(
            readableDatabase.rawQuery(
                "SELECT * FROM tasks WHERE (rpt='none' AND due_date=?) OR (rpt!='none' AND due_date<=?)",
                arrayOf(k, k)
            )
        )
        val done = doneIds(k)
        return raw.filter { it.occursOn(day) }.map { it.copy(done = it.id in done) }.sortedWith(taskOrder)
    }

    /** One-off tasks from earlier days that were never completed. */
    fun overdue(today: LocalDate): List<Task> = readTasks(
        readableDatabase.rawQuery(
            "SELECT * FROM tasks t WHERE t.rpt='none' AND t.due_date<? AND NOT EXISTS " +
                "(SELECT 1 FROM completions c WHERE c.task_id=t.id AND c.day=t.due_date) " +
                "ORDER BY t.due_date, t.priority DESC",
            arrayOf(today.toString())
        )
    )

    fun allTasks(): List<Task> = readTasks(
        readableDatabase.rawQuery(
            "SELECT t.*, (SELECT COUNT(*) FROM completions c WHERE c.task_id=t.id AND c.day=t.due_date) AS dn " +
                "FROM tasks t ORDER BY t.due_date DESC, t.due_time",
            null
        )
    )

    // ---------- Day log ----------

    fun getLog(day: String): DayLog? =
        readableDatabase.rawQuery("SELECT * FROM daylog WHERE day=?", arrayOf(day)).use { c ->
            if (!c.moveToFirst()) null else DayLog(
                date = c.str("day"),
                steps = c.int("steps"),
                sleepMin = c.int("sleep_min"),
                screenMin = c.int("screen_min"),
                unlocks = c.int("unlocks"),
                wentWell = c.str("went_well"),
                improve = c.str("improve"),
            )
        }

    @Synchronized
    fun saveMetrics(
        day: String,
        steps: Int? = null,
        sleepMin: Int? = null,
        screenMin: Int? = null,
        unlocks: Int? = null,
    ) {
        val cur = getLog(day) ?: DayLog(day)
        writeLog(
            cur.copy(
                steps = steps ?: cur.steps,
                sleepMin = sleepMin ?: cur.sleepMin,
                screenMin = screenMin ?: cur.screenMin,
                unlocks = unlocks ?: cur.unlocks,
            )
        )
    }

    @Synchronized
    fun saveReflection(day: String, wentWell: String, improve: String) {
        val cur = getLog(day) ?: DayLog(day)
        writeLog(cur.copy(wentWell = wentWell.trim(), improve = improve.trim()))
    }

    private fun writeLog(l: DayLog) {
        val cv = ContentValues().apply {
            put("day", l.date)
            put("steps", l.steps)
            put("sleep_min", l.sleepMin)
            put("screen_min", l.screenMin)
            put("unlocks", l.unlocks)
            put("went_well", l.wentWell)
            put("improve", l.improve)
        }
        writableDatabase.insertWithOnConflict("daylog", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        @Volatile
        private var inst: Db? = null

        fun get(ctx: Context): Db =
            inst ?: synchronized(this) { inst ?: Db(ctx.applicationContext).also { inst = it } }

        /** Unfinished first, then by time (untimed last), then high priority first. */
        val taskOrder: Comparator<Task> = compareBy<Task>(
            { it.done },
            { if (it.time.isBlank()) "99:99" else it.time },
            { -it.priority },
            { it.id },
        )
    }
}
