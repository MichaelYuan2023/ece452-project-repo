# Agent Instructions

This project is a Kotlin Android project using MVVM and Jetpack Compose.
Project-specific product requirements will be added later, so keep new work
focused on the architecture and conventions below unless the user gives more
context.

## Project Layout

- `screens/view/`: Jetpack Compose UI. Put composable screen/view code here.
- `screens/viewmodel/`: ViewModels and UI state/event handling for screens.
- `model/`: Model layer code, including data models and domain objects.

Keep the top-level architecture organized around these directories until the
project gains a fuller Android/Gradle module structure. If an Android app module
is added later, preserve the same separation inside the Kotlin source set unless
the user changes the layout.

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

**HouseFlow** is a roommate chore-tracking Android app that fairly assigns household chores based on each roommate's availability. All data is currently in-memory (no persistence); a Room migration seam is in place throughout.

---

## Tech Stack

| Layer | Library / Version |
|---|---|
| Language | Kotlin 2.2.10 |
| UI | Jetpack Compose (BOM `2026.02.01`) |
| Navigation | `androidx.navigation.compose` 2.8.0 |
| Build | AGP 9.2.1, Gradle 9.4.1 |
| Min SDK | 24 · Target/Compile SDK 36 |

---

## Package Structure

```
com.example.houseflow/
  MainActivity.kt
  data/
    AppContainer.kt                 ← singleton service-locator; swap impls here for Room
    repository/
      HouseholdRepository.kt        ← interface
      ChoreRepository.kt            ← interface
      InMemoryHouseholdRepository.kt
      InMemoryChoreRepository.kt
  model/
    Household.kt, Roommate.kt, BusyBlock.kt, Chore.kt, ChoreAssignment.kt
  ui/
    navigation/AppNavGraph.kt       ← route constants + NavHost wiring
    screen/
      CreateAccountScreen.kt
      JoinHouseholdScreen.kt
      MainScreen.kt                 ← bottom-nav host (Schedule / Chores / Dashboard)
      AvailabilityScreen.kt
      ChoreListScreen.kt
      DashboardScreen.kt
    theme/                          ← Color.kt, Theme.kt, Type.kt
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
enum class ChoreFrequency { DAILY, WEEKLY }
data class Chore(val id: String, val householdId: String, val createdByRoommateId: String,
    val name: String, val description: String, val frequency: ChoreFrequency,
    val effortScore: Int, // 1–5
    val dueDayOfWeek: Int, val dueHour: Int, val isTimeSensitive: Boolean)
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
Seed: `id="household-1"`, `inviteCode="DEMO123"`.

---

## Repository Interfaces

**`HouseholdRepository`**: `getHousehold`, `joinHousehold(code)`, `addRoommateToHousehold`, `getRoommates`, `getBusyBlocks`, `addBusyBlock`, `deleteBusyBlock`.

**`ChoreRepository`**: `getChores`, `addChore`, `deleteChore` (cascades to assignments), `getAssignments`, `addAssignment`, `updateAssignmentStatus`.

`AppContainer` is the service locator. To migrate to Room, replace the concrete types there — ViewModels only see the interfaces.

---

## `AppViewModel` — State & Actions

Single ViewModel scoped to the nav graph via `AppViewModel.Factory`.

| Flow | Type | Notes |
|---|---|---|
| `currentUser` | `Roommate?` | Null until `createAccount()` |
| `household` | `Household?` | Null until `joinHousehold()` succeeds |
| `roommates` | `List<Roommate>` | All in the household |
| `myBusyBlocks` | `List<BusyBlock>` | Current user only |
| `chores` | `List<Chore>` | All household chores |
| `assignments` | `List<ChoreAssignment>` | All weeks |
| `assignmentsRun` | `Boolean` | Button guard; resets when chores change |
| `weekStart` | `Long` | This week's Monday epoch ms, computed once |

Key actions: `createAccount(name)`, `joinHousehold(code): Boolean`, `addBusyBlock`, `deleteBusyBlock`, `addChore`, `deleteChore`, `runAssignments()`, `markComplete(id)`.

`runAssignments()` skips chores already assigned this week and calls `AssignmentAlgorithm.assignAll()`.

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

```
CREATE_ACCOUNT  →  JOIN_HOUSEHOLD  →  MAIN  (auth screens popped off stack)
```

`MainScreen` is a `Scaffold` with a bottom `NavigationBar`: tab 0 = Schedule (`AvailabilityScreen`), tab 1 = Chores (`ChoreListScreen`), tab 2 = Dashboard (`DashboardScreen`).

---

## Screens

- **CreateAccountScreen**: name-only sign-in, calls `vm.createAccount(name)`.
- **JoinHouseholdScreen**: invite-code entry, demo code `DEMO123`, shows inline error on failure.
- **AvailabilityScreen**: lists/adds/deletes the current user's `BusyBlock`s. Shared `SimpleDropdown` composable lives here.
- **ChoreListScreen**: lists/adds/deletes chores. "Run Fair Assignment" button disabled once run or if no chores.
- **DashboardScreen**: shows all `ChoreAssignment`s. Cards are color-coded (error=conflict, secondary=completed). "Mark Complete" shown only for the current user's pending chores.

---

## Seed Data (`InMemoryHouseholdRepository`)

| Roommate | ID | Busy blocks |
|---|---|---|
| Maya | `r-maya` | Gym Mon/Wed/Fri 19–22, Work Tue/Thu 9–13 |
| Jake | `r-jake` | Full-time work Mon–Fri 8–17 |
| Priya | `r-priya` | Classes Mon–Thu 18–21, Club Sat 10–14 |

---

## Migration Seams

All marked `// Migration seam:` in source.

1. **Persistence** — Replace `InMemory*Repository` with Room DAO-backed implementations; update `AppContainer`. No ViewModel/UI changes needed.
2. **Auth** — Replace name-only `createAccount()` with real auth; keep `currentUser: StateFlow<Roommate?>` contract.
3. **Algorithm** — All penalty weights are constants in `AssignmentAlgorithm.score()`.

---

## What Is Not Yet Implemented

- Real persistence (data resets on restart)
- Real authentication
- Push notifications / reminders
- Chore editing (only add/delete)
- `effortScore` and `isTimeSensitive` captured in UI but not used by the algorithm
- `MISSED` status defined but never set automatically
- Multi-household support
