# Handoff: Auth + Persistence (HF-1, HF-2, HF-3)

This document summarizes the migration from a fabricated in-memory identity to
real **Firebase Auth** + **Room** persistence, and what you need to know to build
on top of it.

## What changed, at a glance

| Task | In one line |
|------|-------------|
| **HF-1** | Name-only sign-in → **Firebase Auth** (email/password); nav gated on auth; sign-out. |
| **HF-2** | New **`User`** identity (uid/email/displayName); **`Roommate` is now a household membership**; Firebase uid threaded everywhere; demo roommates are real Firebase accounts. |
| **HF-3** | **Room** persistence retires the in-memory repos; async repos; bulletin persists; state-driven nav; session restore fixes re-join-every-login. |

## The new mental model

```
Firebase Auth ──► User (uid, email, displayName, completedChoreCount)
                    │  the authenticated identity
                    ▼
                 Roommate (userId, householdId, displayName)   ← household membership
                    │
   busyBlock.roommateId / chore.createdByRoommateId / assignment.assignedToRoommateId
                    └── all hold the Firebase uid
```

- **`currentUser` is now `StateFlow<User?>`** (was `Roommate?`). Use `.uid`, not `.id`.
- **`Roommate` no longer has `id`/`name`** — it's `{ userId, householdId, displayName }`.
- **Everything persists in Room**; only auth talks to Firebase.

## Where things live

- **Auth:** `data/repository/AuthRepository` + `FirebaseAuthRepository`.
- **Room:** `data/local/` → `HouseflowDatabase`, `Daos.kt`, `Converters`, `DatabaseSeeder`.
- **Repos (all `suspend`):** `data/repository/RoomRepositories.kt` behind the `User/Household/Chore/BulletinRepository` interfaces.
- **Wiring:** `data/AppContainer` (Room repos), initialized by `HouseflowApplication` (registered in the manifest).
- **Seed/demo data:** `data/DemoAccounts.kt` (the 3 real Firebase accounts) + `DatabaseSeeder` (household, schedules, chores, bulletin).
- **Nav:** `ui/navigation/AppNavGraph` — a `when(sessionState)`, no NavController.

## Contracts you code against (`AppViewModel`)

- **Observe (in Composables):** `val x by vm.something.collectAsState()`
  — `currentUser`, `household`, `roommates`, `myBusyBlocks`,
  `householdBusyBlocks` (`Map<uid, List<BusyBlock>>`), `chores`, `assignments`,
  `assignmentsRun`, `bulletinPosts`, `sessionState`.
- **Mutations are fire-and-forget** (`viewModelScope.launch` internally) — call
  from `onClick`: `addBusyBlock`, `deleteBusyBlock`, `addChore`, `updateChore`,
  `deleteChore`, `runAssignments`, `markComplete`, `swapAssignment`,
  `addBulletinPost`, `deleteBulletinPost`, `refreshOverdue`.
- **`suspend`** (call from a coroutine): `signIn`, `signUp`, `joinHousehold`.

## Session / navigation states

`AppViewModel.sessionState` drives the top-level screen:

| State | Meaning | Screen shown |
|-------|---------|--------------|
| `LOADING` | Restoring a signed-in user's household | Loading spinner |
| `SIGNED_OUT` | No authenticated user | `AuthScreen` |
| `NEEDS_HOUSEHOLD` | Signed in, not a member of any household | `JoinHouseholdScreen` |
| `IN_HOUSEHOLD` | Signed in and a member | `MainScreen` (tabs) |

Sign-in, join, and sign-out all just change state; the correct screen follows.

## ⚠️ Things that will bite you if you don't know

1. **Room schema changes need care.** DB is `version = 1`, `exportSchema = false`,
   with **no migration and no destructive fallback**. If you add/rename a column
   or entity, the app **crashes on launch** unless you either bump the version +
   add a `Migration`, or add `.fallbackToDestructiveMigration()` (wipes data —
   fine for dev). During active development, the destructive fallback is the
   low-friction option.

2. **Repos are `suspend` now.** Call them from `viewModelScope.launch` or a
   `suspend` function. You **can't call them inside `.map{}` / `.associate{}`**
   lambdas — use an explicit loop (see `runAssignmentsInternal`).

3. **Build toolchain quirk.** The project uses **AGP 9 built-in Kotlin** (there is
   no `kotlin.android` plugin). KSP only works because of
   `android.disallowKotlinSourceSets=false` in `gradle.properties` — don't remove
   it. Versions: Room `2.7.1`, KSP `2.2.10-2.0.2` (keep the KSP version's `2.2.10`
   prefix matched to the Kotlin version).

4. **Firebase is required to run.** `google-services.json` is in `app/`, and the
   **Email/Password provider must stay enabled** in the console. The three demo
   accounts (`maya`/`jake`/`priya@houseflow.demo`) are real; their uids are
   hard-coded in `DemoAccounts.kt`. Don't delete those console accounts or the
   seeded demo won't map to real logins.

5. **Navigation is state-driven, not NavController-based.** To add a *top-level*
   destination, add a `SessionState` case; to add a *tab*, edit `MainScreen`.
   `navigation-compose` is still a dependency but is now **unused**.

## Hooks left for upcoming tasks

- **HF-7 (chore counts):** `User.completedChoreCount` exists as a placeholder
  (currently always 0) — wire it up when you get there.
- **HF-4 (household creation/invites):** today there's only the one seeded
  household joined via `DEMO123`; there is no "create household" flow yet. When
  HF-4 lands, the demo seed in `DatabaseSeeder` is meant to be dropped/gated.
- **Dead code:** `AssignmentAlgorithm.assignAll()` is unused (the ViewModel calls
  `assignOne` per slot) — safe to delete.

## Quick "how do I…"

- **Add a persisted field to a model:** edit the `@Entity` → bump DB `version` +
  add a `Migration` (or use destructive fallback in dev) → thread it through the
  DAO/repo if queried.
- **Add a new repo method:** add a `suspend fun` to the interface → implement in
  the `Room*Repository` → add the DAO `@Query`.
- **Read data in a new screen:** screens already receive `vm: AppViewModel`; just
  `collectAsState()` the flow you need.

## Where the database lives

On-device SQLite at `/data/data/com.example.houseflow/databases/houseflow.db`
(private internal storage; WAL mode, so `-wal`/`-shm` sidecar files exist).
Persists across launches and app updates; wiped by uninstall or *Clear storage*
(which re-runs the first-run seed). Inspect via Android Studio's **Database
Inspector** (easiest) or `adb ... run-as com.example.houseflow`.

## Manual test checklist

**Setup:** the passwords you set for the demo accounts in the Firebase console;
invite code `DEMO123`.

### Auth (HF-1)
- Fresh launch → **Sign In** screen.
- Sign up (name + email + password) → **Join Household**.
- Bad email / short password on sign-up → error under the password field.
- Wrong password on sign-in → error, stays on screen.
- Logout icon (top-right of *My Schedule*) → back to Sign In, state cleared.
- Sign in, then **force-kill + relaunch** → auto-signed-in (no Sign In screen).

### Persistence + re-join fix (HF-3) — the important one
- Sign in → join `DEMO123`.
- Add a busy block, add a chore, Run Fair Assignment, mark one complete, add a
  bulletin post.
- **Force-kill and relaunch** → lands straight on the tabs (no re-join) and all
  changes are still there.
- Sign out → sign back in as the same user → straight to tabs, data intact.
- Settings → Apps → HouseFlow → **Clear storage**, relaunch → demo data re-seeds.

### Identity / real seeded accounts (HF-2)
- Sign in as `maya@houseflow.demo` → join `DEMO123` → you *are* Maya (her seeded
  schedule + chores).
- Sign out → sign in as `jake@houseflow.demo` → Jake's schedule, not Maya's.
- Roommates tab lists Maya, Jake, Priya (plus you, if a brand-new account).
- Brand-new account → join `DEMO123` → gets a seeded starter timetable that
  persists across relaunch.

### Feature regression
- **My Schedule:** add / delete busy blocks; weekly grid renders them.
- **Roommates:** tap a roommate → profile dialog (mini calendar + this-week chores).
- **Chores:** create with each frequency; Run Fair Assignment shows reasoning;
  Mark Complete rotates to next occurrence; Swap re-routes; edit + delete.
- **Bulletin:** post an Event and an Announcement; delete a post (stays gone after
  relaunch).
