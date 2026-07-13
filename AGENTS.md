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

**HouseFlow** is a roommate chore-tracking Android app that fairly assigns household chores based on each roommate's availability, plus a house bulletin board for announcements/events. Users can create or join multiple households and switch between them. Data persists on-device via Room (SQLite); authentication is real Firebase Auth (email/password). See `HANDOFF_auth_persistence.md` for the migration writeup.

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
      HouseflowDatabase.kt          ← Room @Database (version 3), seeds on first create
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
      HouseholdSelectionScreen.kt   ← list/create/join households; has its own sign-out icon
      MainScreen.kt                 ← bottom-nav host (Roommates / Schedule / Chores / Bulletin / Settings)
      AvailabilityScreen.kt
      RoommateAvailabilityScreen.kt ← household-wide weekly availability grid
      ChoreListScreen.kt
      DashboardScreen.kt            ← House Bulletin: announcements/events feed
      SettingsScreen.kt             ← sign-out + entry point to household switcher
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
Seed: `id="household-1"`, `name="Demo House"`, `inviteCode="DEMO123"`. User-created households get a freshly generated, unique 6-character invite code (see `RoomHouseholdRepository.createHousehold` / `generateInviteCode()` in `RoomRepositories.kt` — excludes visually ambiguous characters like `0`/`O`, `1`/`I`).

### `User`
The authenticated identity — `uid` is the Firebase Auth uid and is the single source of truth for a person across the app. `activeHouseholdId` is the household to resume into on sign-in (see `AppViewModel.activateHousehold`); null until the user has joined/created/selected one.
```kotlin
data class User(val uid: String, val email: String, val displayName: String,
    val completedChoreCount: Int = 0, // placeholder; unused until HF-7
    val activeHouseholdId: String? = null)
```

### `Roommate`
A household-scoped **membership** record, not an identity — links a `User` (by uid) to a household. No longer carries `id`/`name`. Primary key is composite (`userId`, `householdId`), so one `User` can have a `Roommate` row per household they belong to — this is what makes multi-household support possible with no schema change needed there. `displayName` is denormalized from `User.displayName` at join/create time; `AppViewModel.syncOwnRoommateDisplayName()` self-heals it back in line with the `User` record if it ever drifts (e.g. a stale value written before a display-name bug was fixed). `role` holds this membership's position in the three-tier hierarchy — see "Roles & Permissions" below.
```kotlin
enum class HouseholdRole { CREATOR, ADMIN, MEMBER }
data class Roommate(val userId: String, val householdId: String, val displayName: String,
    val role: HouseholdRole = HouseholdRole.MEMBER)
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

**`HouseholdRepository`**: `joinHousehold(code): Result<Household>` (failure = invalid code, surfaced by the ViewModel/UI as "Invalid house code"), `createHousehold(name, creatorUserId, creatorDisplayName): Household` (generates a unique invite code and inserts the creator as a `Roommate` with `role = CREATOR`), `getHouseholdsForUser(userId): List<Household>` (every household the user belongs to — a user can belong to several), `getHousehold(householdId): Household?`, `addRoommateToHousehold`, `getRoommates`, `updateRoommateRole(householdId, userId, newRole)` (persists whatever role is passed — permission checks are the caller's job, see "Roles & Permissions"), `getBusyBlocks`, `addBusyBlock`, `deleteBusyBlock`.

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
| `household` | `Household?` | The **active** household. Null until one is joined/created/selected, or restored from `User.activeHouseholdId` on launch |
| `households` | `List<Household>` | Every household the current user belongs to |
| `showHouseholdSwitcher` | `Boolean` | True to show `HouseholdSelectionScreen` on top of an already-active session (opened from Settings), without disturbing `sessionState` |
| `roommates` | `List<Roommate>` | All in the active household |
| `currentUserRole` | `HouseholdRole?` | Signed-in user's role in the active household, derived from `roommates` + `currentUser`; null while restoring |
| `myBusyBlocks` | `List<BusyBlock>` | Current user only |
| `householdBusyBlocks` | `Map<String, List<BusyBlock>>` | All roommates' blocks, keyed by Firebase uid |
| `chores` | `List<Chore>` | All active-household chores |
| `assignments` | `List<ChoreAssignment>` | All weeks |
| `assignmentsRun` | `Boolean` | Button guard; resets when chores change |
| `bulletinPosts` | `List<BulletinPost>` | All posts for the active household |
| `weekStart` | `Long` | This week's Monday epoch ms, computed once |

Key actions: `signIn(email, password): Result<Unit>`, `signUp(displayName, email, password): Result<Unit>`, `signOut()`, `joinHousehold(code): Boolean`, `createHousehold(name)`, `selectHousehold(householdId)` (switch active household to one already joined), `openHouseholdSwitcher()` / `closeHouseholdSwitcher()`, `promoteToAdmin(targetUserId)`, `demoteToMember(targetUserId)` (both enforce the permission matrix — see "Roles & Permissions"), `addBusyBlock`, `deleteBusyBlock`, `addChore` (auto-runs assignments; CREATOR/ADMIN only), `updateChore` (CREATOR/ADMIN only), `deleteChore` (CREATOR/ADMIN only), `runAssignments()`, `markComplete(id)`, `swapAssignment(id)`, `refreshOverdue()`, `addBulletinPost(title, message, isEvent)`, `deleteBulletinPost(id)`.

Mutation actions (`addBusyBlock`, `addChore`, `createHousehold`, `selectHousehold`, etc.) are fire-and-forget `viewModelScope.launch` calls; `signIn`/`signUp`/`joinHousehold` are `suspend` and must be called from a coroutine (`joinHousehold` returns `Boolean` so the UI can show an inline "Invalid house code" error on `false`).

Joining, creating, or selecting a household all funnel through a private `activateHousehold(household)` helper: it persists the choice as `User.activeHouseholdId`, loads the household's data, and refreshes `households`. `loadHouseholdData()` also runs two self-heal steps on every load: `syncOwnRoommateDisplayName()` (see `Roommate` above) and `removeMockedScheduleBlocks()`, which sweeps away any leftover auto-seeded busy blocks (ids prefixed `seed-`) from when the app used to give new members a fake starter timetable — that seeding was removed; new members now start with an empty schedule.

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

## Roles & Permissions

Three-tier hierarchy on `Roommate.role`: `CREATOR > ADMIN > MEMBER`. `CREATOR` is set once, on `createHousehold()`, and is otherwise **immutable** — no one (including the Creator) can ever change it, and no one can be promoted to it.

| Actor \ Action | Promote MEMBER → ADMIN | Demote ADMIN → MEMBER | Change CREATOR's role |
|---|---|---|---|
| Creator | Yes | Yes | No (not even self) |
| Admin | Yes | No | No |
| Member | No | No | No |

Chore authoring (`addChore`/`updateChore`/`deleteChore`) is allowed for `CREATOR`/`ADMIN`, rejected for `MEMBER`. Chore assignment status changes (`markComplete`, `swapAssignment`, `runAssignments`) are **not** gated — those are about working an existing chore, not authoring one, and are available to everyone.

Enforcement lives entirely in `AppViewModel`, independent of what the UI already filters out:
- `promoteToAdmin(targetUserId)`: rejects if the caller is `MEMBER`, or if the target's current role isn't `MEMBER` (covers targeting an existing `ADMIN` or the `CREATOR`).
- `demoteToMember(targetUserId)`: rejects unless the caller is `CREATOR`, or if the target's current role isn't `ADMIN` (covers targeting the `CREATOR`, including the Creator targeting themselves).
- `canManageChores()` (private): `currentUserRole in {CREATOR, ADMIN}`.

`RoommateAvailabilityScreen` shows each roommate's role as a badge ("Creator"/"Admin"/"Member") on their card, and a Promote/Demote text button per `roleActionFor()` (mirrors the matrix above — never shown on your own card or the Creator's card). Every promote/demote requires confirming an `AlertDialog` before `AppViewModel` is called. `ChoreListScreen` hides the add-chore FAB and each chore's edit/delete icons for `MEMBER`, showing an explanatory banner instead ("Your role (Member) does not have permission to create chores...").

---

## Navigation

There is no `NavController` — `AppNavGraph` is a plain `when` over `AppViewModel.sessionState`, plus one extra boolean layered on top:

```
LOADING  →  SIGNED_OUT (AuthScreen)  →  NEEDS_HOUSEHOLD (HouseholdSelectionScreen)  →  IN_HOUSEHOLD (MainScreen)
                                                                                             ↕
                                                                          showHouseholdSwitcher (HouseholdSelectionScreen, onBack)
```

Signing in/up, joining/creating/selecting a household, and signing out all just change `sessionState`; the right screen follows automatically. `showHouseholdSwitcher` reuses `HouseholdSelectionScreen` on top of an already-active `IN_HOUSEHOLD` session (opened from Settings) without touching `sessionState` itself — `onBack` closes it via `vm.closeHouseholdSwitcher()`. (Tab navigation within `MainScreen` is local `remember` state, unrelated to this.)

`MainScreen` is a `Scaffold` with a bottom `NavigationBar` (5 tabs): 0 = Roommates (`RoommateAvailabilityScreen`), 1 = Schedule (`AvailabilityScreen` — label reads "Schedule" in the nav bar, "My Schedule" in its top bar), 2 = Chores (`ChoreListScreen`), 3 = Bulletin (`DashboardScreen`), 4 = Settings (`SettingsScreen`, owns the sign-out action and the entry point to the household switcher).

---

## Screens

- **AuthScreen**: Firebase email/password sign-up and sign-in, toggled via local state; calls `vm.signIn(email, password)` or `vm.signUp(displayName, email, password)`.
- **HouseholdSelectionScreen**: lists households the user already belongs to (tap to `selectHousehold`), a "Create a Household" name field (`createHousehold`), and a "Join a Household" invite-code field (`joinHousehold`, demo code `DEMO123`) with an inline "Invalid house code" error on failure. Has a sign-out icon top-right (`onSignOut`) and, when opened as the switcher (not the initial gate), a back icon (`onBack`).
- **AvailabilityScreen**: lists/adds/deletes the current user's `BusyBlock`s. No longer owns sign-out (moved to `SettingsScreen`). Shared `SimpleDropdown` composable lives here.
- **RoommateAvailabilityScreen**: read-only weekly grid showing all roommates' busy blocks, color-coded by `BlockType`. Hour window expands dynamically to cover all blocks. Each roommate card shows a role badge and, per the permission matrix, a Promote/Demote button that opens a confirmation dialog before calling `vm.promoteToAdmin`/`vm.demoteToMember`.
- **ChoreListScreen**: lists/adds/edits/deletes chores — add/edit/delete UI only rendered for `CREATOR`/`ADMIN`; `MEMBER` sees a view-only list plus an explanatory banner. "Run Fair Assignment" button disabled once run or if no chores (available to all roles). Also renders the current user's `ChoreAssignment`s this week as cards (color-coded: error=conflict, secondary=completed), with "Mark Complete" and "Swap" actions (available to all roles).
- **DashboardScreen**: House Bulletin — announcements/events feed backed by `BulletinPost`. Add/delete posts, `isEvent` distinguishes events from announcements.
- **SettingsScreen**: account info (display name, email), a "Households" row that opens the household switcher (`vm.openHouseholdSwitcher()`), and sign-out.

---

## Seed Data (`DemoAccounts.kt` + `data/local/DatabaseSeeder.kt`)

Seeding is now a one-time `RoomDatabase.Callback.onCreate` step, not code baked into a repository. The three demo roommates are **real Firebase Auth accounts** (`maya@houseflow.demo`, `jake@houseflow.demo`, `priya@houseflow.demo`), so signing in as one of them lands on their seeded schedule/chores. Their ids are Firebase uids, not the old `r-maya`-style strings:

| Roommate | Firebase uid | Role | Busy blocks |
|---|---|---|---|
| Maya | `FQY4uJtyTPWRuffTXqyTw8tnHIp2` | `CREATOR` | Gym Mon/Wed/Fri 19–22, Work Tue/Thu 9–13 |
| Jake | `R891SPtU09hpwN985sJBcZojsBg2` | `ADMIN` | Full-time work Mon–Fri 8–17 |
| Priya | `NvrEZtU6yae7BtKgFOHecuQlrz52` | `MEMBER` | Classes Mon–Thu 18–21, Club Sat 10–14 |

`DatabaseSeeder` also seeds 5 household chores and 4 bulletin posts. Brand-new (non-demo) users no longer get an auto-generated starter timetable — that seeding step (`AppViewModel.seedUserSchedule()`) was removed; `AppViewModel.removeMockedScheduleBlocks()` cleans up any already-seeded blocks (ids prefixed `seed-`) left over from before the change. My Schedule starts empty for everyone except the three demo accounts above.

---

## Migration Seams

The persistence and auth migrations described in earlier versions of this doc are **done** — see `HANDOFF_auth_persistence.md` for the writeup. Only one `// Migration seam:` comment remains in source, on `data/repository/AuthRepository.kt`: swap `FirebaseAuthRepository` for a fake/test double or another auth provider without touching the ViewModel.

Penalty weights for the assignment algorithm are still plain constants in `AssignmentAlgorithm.score()` if they need tuning.

`HouseflowDatabase` uses `fallbackToDestructiveMigration(dropAllTables = true)` rather than real `Migration` objects — acceptable while there's no production data to preserve, but revisit before a real release. Bump `version` in `@Database(...)` whenever an `@Entity` field changes (currently `3`, after `Roommate.role` was added).

---

## What Is Not Yet Implemented

- Push notifications / reminders
- `effortScore` and `isTimeSensitive` captured in UI but not used by the algorithm
- `User.completedChoreCount` is a placeholder, always `0` (tracked as HF-7)
