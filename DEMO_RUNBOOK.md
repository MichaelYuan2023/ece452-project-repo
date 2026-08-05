# HouseFlow Demo Runbook

This guide walks you through running the HouseFlow app demo from a fresh clone. The demo is built around the **Ana Baker** user story — a 3rd-year Computer Engineering student at UW who shares an apartment with 4 roommates.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Android Studio | Ladybug (2024.2+) or newer | Must support AGP 9.x and Kotlin 2.2 |
| JDK | 17+ | Bundled with Android Studio is fine |
| Android SDK | API 36 (compile/target), minimum API 24 | Install via SDK Manager |
| Firebase project | Already configured | `google-services.json` is checked into the repo |
| Emulator or device | API 26+ recommended | Physical device or AVD both work |

---

## 1. Clone and Open

```bash
git clone <repo-url>
cd Project
```

Open the `Project` folder in Android Studio. Gradle sync should start automatically.

If you see a `local.properties` error, create the file at the project root:

```properties
sdk.dir=/path/to/your/Android/sdk
```

On macOS this is typically `~/Library/Android/sdk`. On Linux, `~/Android/Sdk`.

---

## 2. Build the App

From Android Studio: **Build > Make Project**, or from terminal:

```bash
./gradlew :app:assembleDebug
```

The first build takes a few minutes (Gradle downloads dependencies, KSP generates Room code).

---

## 3. Run on Emulator/Device

Select your target device in the toolbar and hit **Run** (green play button), or:

```bash
./gradlew :app:installDebug
```

Then launch the "HouseFlow" app from the device/emulator launcher.

---

## 4. Data Seeding — How It Works

**You don't need to do anything manual.** The database seeds automatically.

- On first launch, Room creates the SQLite database file (`houseflow.db`).
- The `RoomDatabase.Callback.onCreate` hook fires `DatabaseSeeder.seed()`.
- This populates **two households**:
  1. **Demo House** — the original 3-person household (Maya, Jake, Priya).
  2. **Maple Street Apt** — Ana Baker's 5-person presentation household.
- If you ever need a fresh database (e.g., after code changes to the seeder), clear app storage:
  - Emulator/device: **Settings > Apps > HouseFlow > Storage > Clear storage**
  - Or uninstall and reinstall the app.
  - The seed runs again on next launch.

---

## 5. Sign In as Ana Baker

On the Auth screen, use:

| Field | Value |
|-------|-------|
| Email | `ana@houseflow.demo` |
| Password | `anademo1` |

Ana's account has `activeHouseholdId` pre-set to the "Maple Street Apt" household, so you land directly into the full demo experience — no household selection step needed.

---

## 6. What You'll See (Demo Walkthrough)

### Tab 1: Roommates (Availability Grid)

A weekly grid showing all 5 roommates' schedules, color-coded by block type:

| Roommate | Schedule |
|----------|----------|
| **Ana Baker** | Classes Mon–Fri 9am–4pm, Part-time work Sat 9am–5pm |
| **Ben Carter** | Restaurant shifts Mon/Wed/Fri 5pm–10pm, Soccer Sun 2pm–4pm |
| **Chloe Nguyen** | Lectures Tue/Thu 10am–2pm, Art club Sat 3pm–6pm |
| **Diego Silva** | Morning shifts Mon–Fri 8am–12pm |
| **Emma Rossi** | Labs Mon/Wed 1pm–5pm, Study group Sun 10am–12pm |

Role badges are visible: Ana (Creator), Ben (Admin), Chloe/Diego/Emma (Member).

### Tab 2: My Schedule

Shows Ana's personal busy blocks. She can add/delete blocks here.

### Tab 3: Chores (Pickup Board)

Organized into sections:

- **Trade requests for you** (2 pending):
  - Ben: "I picked up a Saturday work shift — could you take the bathroom this week?"
  - Chloe: "Away visiting family this weekend, can you grab the garbage?"
- **Your chores** — Ana's claimed chore (Vacuum common areas) with Complete/Trade actions.
- **Open for pickup** — Unclaimed chores available to grab.
- **Done this week** — Completed/missed chores (groceries, sweep, mop).

Tap a trade request to **Accept** or **Decline** it. This demonstrates the chore trading flow.

### Tab 4: Bulletin Board

5 pre-populated posts:

1. Ana: "Rent due August 1st" (announcement)
2. Ben: "Movie night this Friday" (event)
3. Chloe: "New wifi password" (announcement)
4. Diego: "Please label your food" (announcement)
5. Emma: "Ana's birthday Saturday!" (event)

You can add new posts (announcements or events) and delete existing ones.

### Tab 5: Settings

- Account info (Ana Baker, ana@houseflow.demo)
- Household switcher (opens the household selection screen)
- Sign-out action

---

## 7. Demo Scenarios to Show

### Scenario A: Respond to a Trade Request

1. Go to **Chores** tab.
2. See the 2 trade requests at the top.
3. Tap **Accept** on Ben's bathroom trade — the chore moves to "Your chores."
4. Tap **Decline** on Chloe's garbage trade — it disappears.

### Scenario B: Complete a Chore

1. In "Your chores," tap **Complete** on "Vacuum common areas."
2. It moves to "Done this week" and awards points on the leaderboard.

### Scenario C: Pick Up an Open Chore

1. Scroll to "Open for pickup."
2. Tap **Pick up** on an available chore.
3. It moves to "Your chores."

### Scenario D: View Roommate Availability

1. Go to the **Roommates** tab.
2. Scroll through the weekly grid showing everyone's schedules.
3. Point out how the algorithm uses this data to suggest fair chore assignments (avoiding conflicts).

### Scenario E: Post a Bulletin Announcement

1. Go to **Bulletin** tab.
2. Tap the FAB / add button.
3. Fill in a title and message; toggle event vs. announcement.
4. Post appears at the top of the feed.

### Scenario F: Manage Chore Definitions (Admin)

1. On the **Chores** tab, tap the "Manage" action in the top bar (visible because Ana is Creator).
2. Add a new chore, edit an existing one, or delete one.
3. New chores auto-post their current-period occurrence to the pickup board.

### Scenario G: Household Switching

1. Go to **Settings** tab.
2. Tap **Households**.
3. See "Maple Street Apt" listed (plus "Demo House" if Ana was also added there).
4. Create a new household or join one with an invite code.

---

## 8. Other Demo Accounts

| Account | Email | Password | Household | Role |
|---------|-------|----------|-----------|------|
| Maya | maya@houseflow.demo | (see Firebase console) | Demo House | Creator |
| Jake | jake@houseflow.demo | (see Firebase console) | Demo House | Admin |
| Priya | priya@houseflow.demo | (see Firebase console) | Demo House | Member |
| **Ana Baker** | ana@houseflow.demo | `anademo1` | Maple Street Apt | Creator |

Ben, Chloe, Diego, and Emma are **mock-only** accounts (exist in the Room database but not as real Firebase Auth users). They can't be signed into — they exist to populate the household around Ana.

---

## 9. Resetting the Demo

If the demo state gets "used up" (trades accepted, chores completed, etc.):

1. **Clear app storage** on the device/emulator:
   - Settings > Apps > HouseFlow > Storage > Clear storage
2. Relaunch the app.
3. The database re-seeds from scratch with all trade requests, assignments, and bulletin posts restored to their initial state.

Alternatively, uninstall and reinstall the app.

---

## 10. Troubleshooting

| Issue | Fix |
|-------|-----|
| Build fails with `CannotFindBuildDirectoryException` | Make sure you're in the `Project/` root, not a subdirectory |
| `google-services.json` error | File is checked in — ensure it's at `app/google-services.json` |
| `local.properties` missing | Create it with `sdk.dir=/path/to/Android/sdk` |
| Firebase auth fails for Ana | Ensure device has internet access; the Firebase project must have `ana@houseflow.demo` registered |
| Database doesn't seed | Clear app storage and relaunch; check Logcat for Room/seeder errors |
| Chores show as "MISSED" immediately | This shouldn't happen — due times are computed dynamically to be in the future. If it does, clear storage to re-seed with fresh timestamps |
| KSP/Room compilation errors | Run `./gradlew clean :app:assembleDebug`; ensure KSP version matches Kotlin version (`2.2.10-2.0.2`) |
| Emulator too slow | Use x86_64 system image with hardware acceleration enabled |

---

## 11. Architecture Quick Reference

```
com.example.houseflow/
  data/
    DemoAccounts.kt          ← Firebase uids and User objects for all demo accounts
    local/
      DatabaseSeeder.kt      ← All seed data lives here (both households)
      HouseflowDatabase.kt   ← Room DB; seeds on onCreate + onDestructiveMigration
  model/                     ← @Entity classes (User, Household, Roommate, Chore, etc.)
  ui/
    screen/                  ← All Compose screens
    viewmodel/AppViewModel.kt ← Single ViewModel; owns all state and actions
  util/
    AssignmentAlgorithm.kt   ← Chore assignment scoring engine
```

The seeder is the single source of truth for demo data. To change what the demo shows, edit `DatabaseSeeder.kt` and clear app storage.
