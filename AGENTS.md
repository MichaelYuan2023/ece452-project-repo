# Agent Instructions

This project is a Kotlin Android project using MVVM and Jetpack Compose.
Project-specific product requirements will be added later, so keep new work
focused on the architecture and conventions below unless the user gives more
context.

## Project Layout

Source lives under `app/src/main/java/com/example/houseflow/`:

- `ui/screen/`: Jetpack Compose UI. Put composable screen/view code here.
- `ui/viewmodel/`: ViewModels and UI state/event handling for screens.
- `model/`: Model layer code, including data models and domain objects (Room
  `@Entity` classes).
- `data/`: Repository interfaces, Room-backed implementations, and the
  `AppContainer` composition root.

See "Package Structure" below for the full current tree.

## Architecture

- Follow MVVM boundaries.
- Views should render state and send user actions upward.
- ViewModels should own UI state, screen events, and coordination logic.
- Model classes should not depend on Compose UI APIs.
- Prefer immutable UI state data classes and explicit event/action types.
- Keep business/domain state out of composables when it belongs in a ViewModel
  or model object.

## Jetpack Compose

- Build UI with composable functions.
- Keep composables small enough to preview, test, and reuse.
- Hoist state where practical; avoid storing long-lived screen state directly in
  composables.
- Use `Modifier` parameters on reusable composables.
- Keep previews near the composables they exercise when previews are added.

## Kotlin Style

- Prefer clear names over abbreviations.
- Use `data class` for simple immutable state and model containers.
- Keep files scoped to one clear responsibility.
- Add comments only where they explain non-obvious behavior or decisions.

## Verification

- Run the most specific available checks after changes.
- If no build or test command exists yet, state that clearly when reporting work.
- Do not invent project requirements before the user provides more context.

---

## App Overview

**HouseFlow** is a roommate chore-tracking Android app that fairly assigns household chores based on each roommate's availability, plus a house bulletin board for announcements/events. Data persists on-device via Room (SQLite); authentication is real Firebase Auth (email/password). See `HANDOFF_auth_persistence.md` for the migration writeup.

---

## Tech Stack

| Layer | Library / Version |
|---|---|
| Language | Kotlin 2.2.10 |
| UI | Jetpack Compose (BOM `2026.02.01`) |
| Navigation | `androidx.navigation.compose` 2.8.0 — dependency present but unused; top-level nav is a plain `when` over `AppViewModel.sessionState`, not a `NavController` |
| Auth | Firebase Auth, via Firebase BOM `34.16.0` |
| Persistence | Room `2.7.1` (`room-runtime`, `room-ktx`, `room-compiler` via KSP) — on-device SQLite |
| Build | AGP 9.2.1, Gradle 9.4.1, KSP `2.2.10-2.0.2`, Google Services plugin `4.5.0` |
| Min SDK | 24 · Target/Compile SDK 36 |

---

## Package Structure

```
com.example.houseflow/
  HouseflowApplication.kt           ← calls AppContainer.init(context) before any ViewModel
  MainActivity.kt
  data/
    AppContainer.kt                 ← composition root; Room-backed repositories
    DemoAccounts.kt                 ← seeded demo identities (real Firebase uids)
    local/
      HouseflowDatabase.kt          ← Room @Database, seeds on first create
      Daos.kt                       ← Room @Dao interfaces
      Converters.kt                 ← Room TypeConverters
      DatabaseSeeder.kt             ← first-run demo data (household, chores, bulletin)
    repository/
      AuthRepository.kt             ← interface (Migration seam: swap for test/fake)
      FirebaseAuthRepository.kt     ← Firebase Auth impl
      UserRepository.kt             ← interface
      HouseholdRepository.kt        ← interface
      ChoreRepository.kt            ← interface
      BulletinRepository.kt         ← interface
      RoomRepositories.kt           ← Room impls of User/Household/Chore/Bulletin repos
  model/
    User.kt, Roommate.kt, Household.kt, BusyBlock.kt, Chore.kt,
    ChoreAssignment.kt, BulletinPost.kt   ← all Room @Entity classes
  ui/
    navigation/AppNavGraph.kt       ← `when(sessionState)` gating; no NavController
    screen/
      AuthScreen.kt                 ← Firebase email/password sign-up + sign-in
      JoinHouseholdScreen.kt
      MainScreen.kt                 ← bottom-nav host (Roommates / My Schedule / Chores / Bulletin)
      AvailabilityScreen.kt
      RoommateAvailabilityScreen.kt ← household-wide weekly availability grid
      ChoreListScreen.kt
      DashboardScreen.kt            ← House Bulletin: announcements/events feed
    theme/                          ← Color.kt, Shape.kt, Theme.kt, Type.kt
    viewmodel/AppViewModel.kt       ← single ViewModel for the entire app
  util/AssignmentAlgorithm.kt       ← scoring engine
```

---

## Data Models

### `BusyBlock`
`dayOfWeek` 0=Mon…6=Sun. `endHour` is exclusive (9–17 = 9 am to 5 pm).
```kotlin
enum class BlockType { CLASS, WORK, CLUB, OTHER }
data class BusyBlock(val id: String, val roommateId: String, val dayOfWeek: Int,
    val startHour: Int, val endHour: Int, val title: String, val type: BlockType)
```

### `Chore`
```kotlin
enum class ChoreFrequency { DAILY, WEEKLY, EVERY_N_DAYS, ONE_TIME }
data class Chore(val id: String, val householdId: String, val createdByRoommateId: String,
    val name: String, val description: String, val frequency: ChoreFrequency,
    val effortScore: Int, // 1–5
    val dueDayOfWeek: Int, val dueHour: Int, val isTimeSensitive: Boolean,
    val intervalDays: Int? = null) // only used when frequency == EVERY_N_DAYS
```

### `ChoreAssignment`
```kotlin
enum class AssignmentStatus { PENDING, COMPLETED, MISSED }
data class ChoreAssignment(val id: String, val choreId: String, val householdId: String,
    val assignedToRoommateId: String,
    val weekStart: Long,      // epoch ms of that week's Monday at 00:00
    val status: AssignmentStatus,
    val reason: String,       // shown on Dashboard
    val hasConflict: Boolean) // true if assigned despite being busy at due time
```

### `Household`
Seed: `id="household-1"`, `name="Demo House"`, `inviteCode="DEMO123"`.

### `User`
The authenticated identity — `uid` is the Firebase Auth uid and is the single source of truth for a person across the app.
```kotlin
data class User(val uid: String, val email: String, val displayName: String,
    val completedChoreCount: Int = 0) // placeholder; unused until HF-7
```

### `Roommate`
A household-scoped **membership** record, not an identity — links a `User` (by uid) to a household. No longer carries `id`/`name`.
```kotlin
data class Roommate(val userId: String, val householdId: String, val displayName: String)
```

### `BulletinPost`
Backs the House Bulletin tab (announcements/events).
```kotlin
data class BulletinPost(val id: String, val householdId: String, val authorName: String,
    val title: String, val message: String,
    val isEvent: Boolean, // true = event, false = announcement
    val timestamp: Long)  // epoch ms
```

---

## Repository Interfaces

All repositories are Room-backed and every method is `suspend` (implementations in `data/repository/RoomRepositories.kt`). `AuthRepository` is the one exception, backed by Firebase Auth.

**`AuthRepository`** (impl `FirebaseAuthRepository`): `currentUser: FirebaseUser?` (sync, for restoring session at launch), `authState(): Flow<FirebaseUser?>`, `signIn(email, password): Result<FirebaseUser>`, `signUp(displayName, email, password): Result<FirebaseUser>`, `signOut()`. Carries the codebase's one remaining `// Migration seam:` comment — swap the impl for a fake/test double or another auth provider without touching the ViewModel.

**`UserRepository`**: `getUser(uid)`, `upsertUser(user)`, `getUsers()`.

**`HouseholdRepository`**: `joinHousehold(code): Household?`, `getHouseholdForUser(userId): Household?` (restores session on launch — replaces the old `getHousehold`), `addRoommateToHousehold`, `getRoommates`, `getBusyBlocks`, `addBusyBlock`, `deleteBusyBlock`.

**`ChoreRepository`**: `getChores`, `addChore`, `updateChore`, `deleteChore` (cascades to assignments), `getAssignments`, `addAssignment`, `updateAssignment`, `updateAssignmentStatus`.

**`BulletinRepository`**: `getPosts(householdId)`, `addPost(post)`, `deletePost(postId)`.

`AppContainer` is the composition root: `authRepository` is created eagerly; the Room-backed repositories are `lateinit` and created in `AppContainer.init(context)`, which `HouseflowApplication` calls before any ViewModel is created.

---

## `AppViewModel` — State & Actions

Single ViewModel scoped to the nav graph via `AppViewModel.Factory`, constructed with `AuthRepository`, `UserRepository`, `HouseholdRepository`, `ChoreRepository`, `BulletinRepository`.

| Flow | Type | Notes |
|---|---|---|
| `currentUser` | `User?` | Restored from `authRepo.currentUser` on launch; null when signed out |
| `sessionState` | `SessionState` (`LOADING, SIGNED_OUT, NEEDS_HOUSEHOLD, IN_HOUSEHOLD`) | Derived from `currentUser` + `household`; drives top-level nav |
| `household` | `Household?` | Null until `joinHousehold()` succeeds or is restored on launch |
| `roommates` | `List<Roommate>` | All in the household |
| `myBusyBlocks` | `List<BusyBlock>` | Current user only |
| `householdBusyBlocks` | `Map<String, List<BusyBlock>>` | All roommates' blocks, keyed by Firebase uid |
| `chores` | `List<Chore>` | All household chores |
| `assignments` | `List<ChoreAssignment>` | All weeks |
| `assignmentsRun` | `Boolean` | Button guard; resets when chores change |
| `bulletinPosts` | `List<BulletinPost>` | All posts for the household |
| `weekStart` | `Long` | This week's Monday epoch ms, computed once |

Key actions: `signIn(email, password): Result<Unit>`, `signUp(displayName, email, password): Result<Unit>`, `signOut()`, `joinHousehold(code): Boolean` (also seeds a starter schedule for brand-new users via `seedUserSchedule`), `addBusyBlock`, `deleteBusyBlock`, `addChore` (auto-runs assignments), `updateChore`, `deleteChore`, `runAssignments()`, `markComplete(id)`, `swapAssignment(id)`, `refreshOverdue()`, `addBulletinPost(title, message, isEvent)`, `deleteBulletinPost(id)`.

Mutation actions (`addBusyBlock`, `addChore`, etc.) are fire-and-forget `viewModelScope.launch` calls; `signIn`/`signUp`/`joinHousehold` are `suspend` and must be called from a coroutine.

`runAssignments()` builds frequency-aware slots (one per day for DAILY, one per interval for EVERY_N_DAYS, once for WEEKLY/ONE_TIME) skipping already-assigned slots, then calls `AssignmentAlgorithm.assignOne()` per slot.

`swapAssignment(id)` re-routes the assignment to the fairest other roommate (excludes the current assignee).

`refreshOverdue()` marks PENDING assignments whose due timestamp has passed as MISSED.

---

## Assignment Algorithm (`util/AssignmentAlgorithm.kt`)

Iterates chores and scores every roommate for each one. The accumulating `newAssignments` list is passed along so intra-run workload is counted.

### Scoring penalties (start at 100, higher = better)

| Condition | Penalty |
|---|---|
| Busy at `dueDayOfWeek`/`dueHour` | −30 |
| Each assignment in past 2 weeks | −10 |
| Each chore already assigned this week | −5 |
| Had this exact chore last week | −15 |

`hasConflict = true` when the winner still has a busy-block conflict (best available despite it). A human-readable `reason` string is generated and stored on every `ChoreAssignment`.

---

## Navigation

There is no `NavController` — `AppNavGraph` is a plain `when` over `AppViewModel.sessionState`:

```
LOADING  →  SIGNED_OUT (AuthScreen)  →  NEEDS_HOUSEHOLD (JoinHouseholdScreen)  →  IN_HOUSEHOLD (MainScreen)
```

Signing in/up, joining a household, and signing out all just change `sessionState`; the right screen follows automatically. (Tab navigation within `MainScreen` is local `remember` state, unrelated to this.)

`MainScreen` is a `Scaffold` with a bottom `NavigationBar`: tab 0 = Roommates (`RoommateAvailabilityScreen`), tab 1 = My Schedule (`AvailabilityScreen`, also owns the sign-out action), tab 2 = Chores (`ChoreListScreen`), tab 3 = Bulletin (`DashboardScreen`).

---

## Screens

- **AuthScreen**: Firebase email/password sign-up and sign-in, toggled via local state; calls `vm.signIn(email, password)` or `vm.signUp(displayName, email, password)`.
- **JoinHouseholdScreen**: invite-code entry, demo code `DEMO123`, shows inline error on failure.
- **AvailabilityScreen**: lists/adds/deletes the current user's `BusyBlock`s. Shared `SimpleDropdown` composable lives here.
- **RoommateAvailabilityScreen**: read-only weekly grid showing all roommates' busy blocks, color-coded by `BlockType`. Hour window expands dynamically to cover all blocks.
- **ChoreListScreen**: lists/adds/edits/deletes chores. "Run Fair Assignment" button disabled once run or if no chores. Also renders the current user's `ChoreAssignment`s this week as cards (color-coded: error=conflict, secondary=completed), with "Mark Complete" and "Swap" actions.
- **DashboardScreen**: House Bulletin — announcements/events feed backed by `BulletinPost`. Add/delete posts, `isEvent` distinguishes events from announcements.

---

## Seed Data (`DemoAccounts.kt` + `data/local/DatabaseSeeder.kt`)

Seeding is now a one-time `RoomDatabase.Callback.onCreate` step, not code baked into a repository. The three demo roommates are **real Firebase Auth accounts** (`maya@houseflow.demo`, `jake@houseflow.demo`, `priya@houseflow.demo`), so signing in as one of them lands on their seeded schedule/chores. Their ids are Firebase uids, not the old `r-maya`-style strings:

| Roommate | Firebase uid | Busy blocks |
|---|---|---|
| Maya | `FQY4uJtyTPWRuffTXqyTw8tnHIp2` | Gym Mon/Wed/Fri 19–22, Work Tue/Thu 9–13 |
| Jake | `R891SPtU09hpwN985sJBcZojsBg2` | Full-time work Mon–Fri 8–17 |
| Priya | `NvrEZtU6yae7BtKgFOHecuQlrz52` | Classes Mon–Thu 18–21, Club Sat 10–14 |

`DatabaseSeeder` also seeds 5 household chores and 4 bulletin posts. Any brand-new (non-demo) user who joins the household via `joinHousehold()` gets a separate 10-block starter timetable from `AppViewModel.seedUserSchedule()`, skipped if they already have blocks.

---

## Migration Seams

The persistence and auth migrations described in earlier versions of this doc are **done** — see `HANDOFF_auth_persistence.md` for the writeup. Only one `// Migration seam:` comment remains in source, on `data/repository/AuthRepository.kt`: swap `FirebaseAuthRepository` for a fake/test double or another auth provider without touching the ViewModel.

Penalty weights for the assignment algorithm are still plain constants in `AssignmentAlgorithm.score()` if they need tuning.

---

## What Is Not Yet Implemented

- Push notifications / reminders
- `effortScore` and `isTimeSensitive` captured in UI but not used by the algorithm
- Multi-household support — only the one seeded household (`DEMO123`) exists; there's no "create a household" flow
- `User.completedChoreCount` is a placeholder, always `0` (tracked as HF-7)
