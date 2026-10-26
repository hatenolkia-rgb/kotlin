# DayFlow — daily planner & tracker for Android

One app for your day: to-do list, calendar, step counter, sleep estimate, screen time / app usage, and a daily summary of what you did, what you didn't, and how to improve.

## What's inside

| Tab | What it does |
|---|---|
| **Today** | Day score (0–100), steps, sleep, screen time, tasks done, quick-add, overdue tasks, today's plan, tip of the day |
| **To-do** | Today / Next 7 days / All tasks. Priority (Low/Medium/High), category, time + reminder, repeat (Daily, Mon–Fri, Weekly) |
| **Calendar** | Month view — every day shows `done/total` in green (all done), amber (partly), red (missed). Tap a day to see and add tasks |
| **Summary** | Any day: score, numbers vs goals, ✅ what you did, ❌ what you didn't, 💡 how to improve, reflection notes, 7-day chart, app-usage breakdown |
| **Settings** | Your name, step / sleep / screen-time goals, permission status |

- **Steps** – phone's built-in step sensor, counted in the background (small "steps today" notification).
- **Sleep** – estimated from the longest time your phone stayed untouched between 8 PM and noon (short glances to check the time are ignored).
- **Screen time & apps** – Android usage data (same source as Digital Wellbeing), plus unlock count.
- **Reminders** – notification at a task's time with a "Mark done" button.
- **Day score** – tasks 40 %, steps 20 %, sleep 20 %, screen time 20 % (only what's tracked counts).
- All data stays on the phone (no account, no internet).

Requires Android 8.0 or newer.

---

## Get the APK — Option A: GitHub (no software to install)

1. Create a free account at github.com → **New repository** → name it `DayFlow` → **Private** → Create.
2. On the repo page click **uploading an existing file**, and drag in **everything inside** the `DayFlow` folder (including the hidden `.github` folder — on Windows it's visible; on Mac press `Cmd + Shift + .` in Finder to show it). Click **Commit changes**.
3. Open the **Actions** tab. The "Build DayFlow APK" job starts automatically (≈5–7 min). If it didn't start, click it → **Run workflow**.
4. When it shows a green tick, open the run → scroll to **Artifacts** → download **DayFlow-apk** (a zip). Unzip → `app-release.apk`.
5. Send the APK to your phone (WhatsApp to yourself, Google Drive, USB) and tap it → allow **Install unknown apps** → Install.

## Option B: Android Studio

1. Install Android Studio → **Open** → select the `DayFlow` folder → wait for Gradle sync.
2. **Build → Build App Bundle(s) / APK(s) → Build APK(s)** → click **locate** → `app-debug.apk`.
   Or plug in your phone (USB debugging on) and press ▶ Run.

---

## First-time setup on the phone (2 minutes)

1. Open DayFlow → allow **Physical activity** and **Notifications**.
2. On the Today tab tap **Open settings** under "Track screen time & sleep" → find **DayFlow** → turn on **Permit usage access**.
   - If the switch is greyed out ("Restricted setting"): Settings → Apps → DayFlow → ⋮ (top right) → **Allow restricted settings**, then try again.
3. Settings tab → **No battery restriction → Open** → set DayFlow to **Unrestricted / Don't optimise** so step counting isn't killed.
   - On Xiaomi / Redmi / POCO, Oppo, Vivo, Realme, OnePlus also enable **Autostart** for DayFlow.
4. Settings tab → set your step, sleep and screen-time goals.

Steps start counting from the moment you grant permission. Sleep and screen time read Android's existing data, so they show up immediately (Android keeps about the last 7 days).

## Daily routine that makes it work

- **Night (5 min):** Calendar → tomorrow → add your top 3–5 tasks with times.
- **Day:** tick tasks as you finish them; reminders fire at the set time.
- **Evening:** Summary tab → read "How to improve" → fill the two reflection lines.
- **Sunday:** look at the 7-day chart — repeat your best day's pattern.
