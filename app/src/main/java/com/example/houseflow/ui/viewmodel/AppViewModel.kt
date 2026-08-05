package com.example.houseflow.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.houseflow.data.AppContainer
import com.example.houseflow.data.repository.AuthRepository
import com.example.houseflow.data.repository.BulletinRepository
import com.example.houseflow.data.repository.ChoreRepository
import com.example.houseflow.data.repository.ExpenseRepository
import com.example.houseflow.data.repository.HouseholdRepository
import com.example.houseflow.data.repository.PointsRepository
import com.example.houseflow.data.repository.UserRepository
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.BlockType
import com.example.houseflow.model.BulletinPost
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.ChoreFrequency
import com.example.houseflow.model.Expense
import com.example.houseflow.model.ExpenseShare
import com.example.houseflow.model.Household
import com.example.houseflow.model.HouseholdRole
import com.example.houseflow.model.PointsEntry
import com.example.houseflow.model.Recurrence
import com.example.houseflow.model.Roommate
import com.example.houseflow.model.Settlement
import com.example.houseflow.model.SplitType
import com.example.houseflow.model.TradeRequest
import com.example.houseflow.model.TradeStatus
import com.example.houseflow.model.User
import com.example.houseflow.notification.NotificationDispatcher
import com.example.houseflow.util.AssignmentAlgorithm
import com.example.houseflow.util.ChoreDueTime
import com.example.houseflow.util.ChoreScheduler
import com.example.houseflow.util.ExpenseMath
import com.example.houseflow.util.IcsParser
import com.example.houseflow.util.PointsPolicy
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

// Drives top-level navigation. Derived from auth + household state.
enum class SessionState { LOADING, SIGNED_OUT, NEEDS_HOUSEHOLD, IN_HOUSEHOLD }

// HF-13: one ranked row on the household scoreboard.
data class LeaderboardEntry(
    val userId: String,
    val displayName: String,
    val weeklyPoints: Int,
    val allTimePoints: Int,
    val rank: Int
)

// HF-13: the current user's personal gamification summary.
data class PointsSummary(
    val weeklyPoints: Int = 0,
    val allTimePoints: Int = 0,
    val level: Int = 1,
    val pointsIntoLevel: Int = 0,
    val pointsForNextLevel: Int = PointsPolicy.POINTS_PER_LEVEL,
    val streakWeeks: Int = 0
)

class AppViewModel(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
    private val householdRepo: HouseholdRepository,
    private val choreRepo: ChoreRepository,
    private val bulletinRepo: BulletinRepository,
    private val pointsRepo: PointsRepository,
    private val expenseRepo: ExpenseRepository,
    private val notificationDispatcher: NotificationDispatcher
) : ViewModel() {

    // Identity from Firebase Auth, restored automatically on launch.
    private val _currentUser = MutableStateFlow(authRepo.currentUser?.toUser())
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // True while we resolve a signed-in user's household on launch/sign-in.
    private val _restoring = MutableStateFlow(authRepo.currentUser != null)

    // The household currently being viewed/worked in.
    private val _household = MutableStateFlow<Household?>(null)
    val household: StateFlow<Household?> = _household.asStateFlow()

    // Every household the signed-in user belongs to.
    private val _households = MutableStateFlow<List<Household>>(emptyList())
    val households: StateFlow<List<Household>> = _households.asStateFlow()

    // True to show the household list/create/join screen on top of an already
    // active session (reached from Settings), rather than the initial gate.
    private val _showHouseholdSwitcher = MutableStateFlow(false)
    val showHouseholdSwitcher: StateFlow<Boolean> = _showHouseholdSwitcher.asStateFlow()

    private val _roommates = MutableStateFlow<List<Roommate>>(emptyList())
    val roommates: StateFlow<List<Roommate>> = _roommates.asStateFlow()

    private val _myBusyBlocks = MutableStateFlow<List<BusyBlock>>(emptyList())
    val myBusyBlocks: StateFlow<List<BusyBlock>> = _myBusyBlocks.asStateFlow()

    private val _chores = MutableStateFlow<List<Chore>>(emptyList())
    val chores: StateFlow<List<Chore>> = _chores.asStateFlow()

    private val _assignments = MutableStateFlow<List<ChoreAssignment>>(emptyList())
    val assignments: StateFlow<List<ChoreAssignment>> = _assignments.asStateFlow()

    private val _bulletinPosts = MutableStateFlow<List<BulletinPost>>(emptyList())
    val bulletinPosts: StateFlow<List<BulletinPost>> = _bulletinPosts.asStateFlow()

    private val _tradeRequests = MutableStateFlow<List<TradeRequest>>(emptyList())
    val tradeRequests: StateFlow<List<TradeRequest>> = _tradeRequests.asStateFlow()

    // All-time completed chore count per roommate, keyed by userId. Consumed by
    // the roommate UI and available to the HF-8 recommendation engine.
    private val _completionCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val completionCounts: StateFlow<Map<String, Int>> = _completionCounts.asStateFlow()

    // HF-13: the household's points ledger. All gamification totals derive from it.
    private val _pointsEntries = MutableStateFlow<List<PointsEntry>>(emptyList())
    val pointsEntries: StateFlow<List<PointsEntry>> = _pointsEntries.asStateFlow()

    // HF-15: shared-expense state.
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()
    private val _expenseShares = MutableStateFlow<List<ExpenseShare>>(emptyList())
    val expenseShares: StateFlow<List<ExpenseShare>> = _expenseShares.asStateFlow()
    private val _settlements = MutableStateFlow<List<Settlement>>(emptyList())
    val settlements: StateFlow<List<Settlement>> = _settlements.asStateFlow()

    val sessionState: StateFlow<SessionState> =
        combine(_currentUser, _household, _restoring) { user, household, restoring ->
            when {
                restoring -> SessionState.LOADING
                user == null -> SessionState.SIGNED_OUT
                household == null -> SessionState.NEEDS_HOUSEHOLD
                else -> SessionState.IN_HOUSEHOLD
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = if (authRepo.currentUser != null) SessionState.LOADING else SessionState.SIGNED_OUT
        )

    // The signed-in user's role in the active household — null while restoring
    // or if they're somehow not a member (shouldn't happen in practice).
    val currentUserRole: StateFlow<HouseholdRole?> =
        combine(_roommates, _currentUser) { roommates, user ->
            user?.let { u -> roommates.find { it.userId == u.uid }?.role }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val weekStart: Long = currentWeekStart()

    // HF-13: household scoreboard, ranked by weekly points (ties broken by
    // all-time points, then name). Derived purely from the ledger + roommates.
    val leaderboard: StateFlow<List<LeaderboardEntry>> =
        combine(_pointsEntries, _roommates) { entries, roommates ->
            buildLeaderboard(entries, roommates)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // HF-13: the current user's weekly/all-time points, level, and streak.
    val myPointsSummary: StateFlow<PointsSummary> =
        combine(_pointsEntries, _currentUser) { entries, user ->
            buildPointsSummary(entries, user?.uid)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PointsSummary())

    // HF-15: per-roommate net balances derived from expenses, shares, settlements.
    val balances: StateFlow<List<ExpenseMath.Balance>> =
        combine(_roommates, _expenses, _expenseShares, _settlements) { roommates, expenses, shares, settlements ->
            ExpenseMath.balances(roommates, expenses, shares, settlements)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Keep identity in sync with Firebase auth state and restore the user's
        // household so returning members skip the join screen.
        viewModelScope.launch {
            authRepo.authState().collect { firebaseUser ->
                if (firebaseUser != null) {
                    _restoring.value = true
                    // Preserve the previously persisted active household — reconstructing
                    // from FirebaseUser alone would otherwise reset it to null every launch.
                    val existing = userRepo.getUser(firebaseUser.uid)
                    val user = firebaseUser.toUser().copy(activeHouseholdId = existing?.activeHouseholdId)
                    _currentUser.value = user
                    userRepo.upsertUser(user)
                    restoreHousehold(user)
                } else {
                    _currentUser.value = null
                    clearSessionState()
                    _restoring.value = false
                }
            }
        }
    }

    // --- Auth ---

    suspend fun signIn(email: String, password: String): Result<Unit> =
        authRepo.signIn(email.trim(), password).map { }

    suspend fun signUp(displayName: String, email: String, password: String): Result<Unit> =
        authRepo.signUp(displayName.trim(), email.trim(), password).map { firebaseUser ->
            // The auth-state listener in init{} fires as soon as the account is
            // created, before the display name set below finishes, so it caches a
            // premature user whose displayName is still null and falls back to
            // email. Re-sync here with the now-updated, in-memory-correct user.
            val user = firebaseUser.toUser()
            _currentUser.value = user
            userRepo.upsertUser(user)
        }

    fun signOut() {
        authRepo.signOut()
        _currentUser.value = null
        clearSessionState()
        _restoring.value = false
    }

    private fun FirebaseUser.toUser(): User =
        User(
            uid = uid,
            email = email ?: "",
            displayName = displayName?.takeIf { it.isNotBlank() } ?: email ?: "User"
        )

    private suspend fun restoreHousehold(user: User) {
        val households = householdRepo.getHouseholdsForUser(user.uid)
        _households.value = households
        val active = user.activeHouseholdId?.let { id -> households.find { it.id == id } }
            ?: households.firstOrNull()
        if (active != null) {
            _household.value = active
            loadHouseholdData(active)
        } else {
            _household.value = null
        }
        _restoring.value = false
    }

    private suspend fun loadHouseholdData(household: Household) {
        removeMockedScheduleBlocks(household.id)
        _roommates.value = syncOwnRoommateDisplayName(household)
        refreshMyBlocks()
        refreshChores()
        refreshBulletinPosts()
        refreshTradeRequests()
        refreshPoints()
        refreshExpenses()
        // Reconcile the pickup board: mark overdue chores missed and post each
        // chore's current-period occurrence. Replaces the old manual "post"
        // button; syncChoreBoard() refreshes assignments at the end.
        syncChoreBoard()
    }

    // Self-heals a Roommate row whose displayName was captured before the
    // signUp() display-name race above was fixed (or from any other stale
    // write) — brings it back in line with the current User record.
    private suspend fun syncOwnRoommateDisplayName(household: Household): List<Roommate> {
        val roommates = householdRepo.getRoommates(household.id)
        val user = _currentUser.value ?: return roommates
        val mine = roommates.find { it.userId == user.uid } ?: return roommates
        if (mine.displayName == user.displayName) return roommates

        val corrected = mine.copy(displayName = user.displayName)
        householdRepo.addRoommateToHousehold(household.id, corrected)
        return roommates.map { if (it.userId == user.uid) corrected else it }
    }

    private fun clearSessionState() {
        _household.value = null
        _households.value = emptyList()
        _showHouseholdSwitcher.value = false
        _roommates.value = emptyList()
        _myBusyBlocks.value = emptyList()
        _chores.value = emptyList()
        _assignments.value = emptyList()
        _bulletinPosts.value = emptyList()
        _tradeRequests.value = emptyList()
        _completionCounts.value = emptyMap()
        _pointsEntries.value = emptyList()
        _expenses.value = emptyList()
        _expenseShares.value = emptyList()
        _settlements.value = emptyList()
    }

    // --- Household ---

    // Returns true if the invite code is valid.
    suspend fun joinHousehold(code: String): Boolean {
        val user = _currentUser.value ?: return false
        val household = householdRepo.joinHousehold(code).getOrNull() ?: return false

        householdRepo.addRoommateToHousehold(
            household.id,
            Roommate(
                userId = user.uid,
                householdId = household.id,
                displayName = user.displayName,
                role = HouseholdRole.MEMBER
            )
        )
        activateHousehold(household)
        return true
    }

    fun createHousehold(name: String) = viewModelScope.launch {
        val user = _currentUser.value ?: return@launch
        val household = householdRepo.createHousehold(name.trim(), user.uid, user.displayName)
        activateHousehold(household)
    }

    // Switches into a household the user is already a member of.
    fun selectHousehold(householdId: String) = viewModelScope.launch {
        val household = _households.value.find { it.id == householdId }
            ?: householdRepo.getHousehold(householdId)
            ?: return@launch
        activateHousehold(household)
    }

    // Opens the household list/create/join screen without disturbing the
    // signed-in session — reachable from Settings.
    fun openHouseholdSwitcher() {
        _showHouseholdSwitcher.value = true
    }

    fun closeHouseholdSwitcher() {
        // Only closeable if there's an active household to fall back to —
        // otherwise session state itself still requires a household to be chosen.
        if (_household.value != null) _showHouseholdSwitcher.value = false
    }

    // Makes the given household active: persists it as the user's resume point,
    // loads its data, and refreshes the household list/switcher.
    private suspend fun activateHousehold(household: Household) {
        val user = _currentUser.value ?: return
        val updatedUser = user.copy(activeHouseholdId = household.id)
        _currentUser.value = updatedUser
        userRepo.upsertUser(updatedUser)

        _household.value = household
        _showHouseholdSwitcher.value = false
        loadHouseholdData(household)
        _households.value = householdRepo.getHouseholdsForUser(user.uid)
    }

    // --- Roles ---

    // Promotes a MEMBER to ADMIN. Allowed for CREATOR and ADMIN actors. Rejects
    // (no-op) if the caller is a MEMBER, if the target is already something
    // other than MEMBER, or if the target is the CREATOR — independent of
    // whatever the UI already filtered out, per the permission matrix.
    fun promoteToAdmin(targetUserId: String) = viewModelScope.launch {
        val household = _household.value ?: return@launch
        val actorRole = currentUserRole.value ?: return@launch
        if (actorRole == HouseholdRole.MEMBER) return@launch

        val target = _roommates.value.find { it.userId == targetUserId } ?: return@launch
        if (target.role != HouseholdRole.MEMBER) return@launch // already ADMIN/CREATOR — nothing to promote

        householdRepo.updateRoommateRole(household.id, targetUserId, HouseholdRole.ADMIN)
        _roommates.value = householdRepo.getRoommates(household.id)
    }

    // Demotes an ADMIN to MEMBER. Only the CREATOR may do this — admins cannot
    // demote anyone, including other admins or themselves. The CREATOR's own
    // role can never be the target of this (or any) role change.
    fun demoteToMember(targetUserId: String) = viewModelScope.launch {
        val household = _household.value ?: return@launch
        if (currentUserRole.value != HouseholdRole.CREATOR) return@launch

        val target = _roommates.value.find { it.userId == targetUserId } ?: return@launch
        if (target.role != HouseholdRole.ADMIN) return@launch // not an admin — nothing to demote

        householdRepo.updateRoommateRole(household.id, targetUserId, HouseholdRole.MEMBER)
        _roommates.value = householdRepo.getRoommates(household.id)
    }

    // One-time cleanup of the fake starter timetable this app used to seed for
    // every newly-joined/created member (ids "seed-<uid>-<n>"). New members no
    // longer get one; this removes any that already landed in existing installs.
    private suspend fun removeMockedScheduleBlocks(householdId: String) {
        for (roommate in householdRepo.getRoommates(householdId)) {
            householdRepo.getBusyBlocks(roommate.userId)
                .filter { it.id.startsWith("seed-") }
                .forEach { householdRepo.deleteBusyBlock(it.id) }
        }
    }

    // --- Availability ---

    fun addBusyBlock(block: BusyBlock) = viewModelScope.launch {
        householdRepo.addBusyBlock(block)
        refreshMyBlocks()
    }

    fun deleteBusyBlock(blockId: String) = viewModelScope.launch {
        householdRepo.deleteBusyBlock(blockId)
        refreshMyBlocks()
    }

    private suspend fun refreshMyBlocks() {
        _myBusyBlocks.value = householdRepo.getBusyBlocks(_currentUser.value?.uid ?: return)
    }

    // --- Calendar import (HF-11) ---

    // Imports an .ics calendar from raw text into the current user's busy blocks.
    // Returns the number of blocks imported. De-duplicating: it replaces the
    // user's previously imported blocks, so re-importing never creates
    // duplicates and upstream removals disappear (manual blocks are untouched).
    suspend fun importCalendarFromText(icsText: String): Result<Int> = runCatching {
        val uid = _currentUser.value?.uid ?: error("Not signed in")
        val blocks = mapIcsToBlocks(IcsParser.parse(icsText), uid)
        householdRepo.replaceImportedBusyBlocks(uid, blocks)
        refreshMyBlocks()
        blocks.size
    }

    // Fetches an .ics feed over http(s) (webcal:// is normalized to https) off the
    // main thread, then imports it.
    suspend fun importCalendarFromUrl(url: String): Result<Int> = runCatching {
        val normalized = url.trim()
            .replaceFirst(Regex("^webcal://", RegexOption.IGNORE_CASE), "https://")
        require(normalized.startsWith("http://", true) || normalized.startsWith("https://", true)) {
            "Enter an http(s) or webcal calendar URL"
        }
        val text = withContext(Dispatchers.IO) {
            val conn = (java.net.URL(normalized).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "text/calendar, text/plain, */*")
            }
            try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
        importCalendarFromText(text).getOrThrow()
    }

    // Maps parsed ICS events to busy blocks. Timed events → ONE_TIME on their
    // date (or WEEKLY when the event has a weekly RRULE); all-day → whole day.
    // Ids are deterministic and carry the source UID so re-imports collapse and
    // imported blocks are distinguishable from manual ones.
    private fun mapIcsToBlocks(events: List<IcsParser.IcsEvent>, roommateId: String): List<BusyBlock> {
        val out = mutableListOf<BusyBlock>()
        for ((index, e) in events.withIndex()) {
            val start = e.start ?: continue
            val dateMillis = localMidnight(start.year, start.month, start.day)
            val dow = mondayIndexOf(dateMillis)
            val startHour: Int
            val endHour: Int
            if (start.dateOnly) {
                startHour = 0; endHour = 24
            } else {
                startHour = start.hour.coerceIn(0, 23)
                val rawEnd = e.end?.takeUnless { it.dateOnly }?.hour ?: (startHour + 1)
                endHour = rawEnd.coerceIn(startHour + 1, 24)
            }
            val uid = e.uid?.takeIf { it.isNotBlank() } ?: "nouid-$index-$dateMillis"
            val stamp = "%04d%02d%02d".format(start.year, start.month, start.day)
            val block = if (e.weekly) {
                BusyBlock(
                    id = "ics:$uid",
                    roommateId = roommateId,
                    dayOfWeek = dow,
                    startHour = startHour,
                    endHour = endHour,
                    title = e.summary.ifBlank { "Imported event" },
                    type = BlockType.CLASS,
                    recurrence = Recurrence.WEEKLY,
                    date = null,
                    sourceUid = uid
                )
            } else {
                BusyBlock(
                    id = "ics:$uid:$stamp",
                    roommateId = roommateId,
                    dayOfWeek = dow,
                    startHour = startHour,
                    endHour = endHour,
                    title = e.summary.ifBlank { "Imported event" },
                    type = BlockType.CLASS,
                    recurrence = Recurrence.ONE_TIME,
                    date = dateMillis,
                    sourceUid = uid
                )
            }
            out.add(block)
        }
        // Collapse any exact id collisions (same uid+date) so insertAll doesn't
        // rely on REPLACE ordering.
        return out.associateBy { it.id }.values.toList()
    }

    private fun localMidnight(year: Int, month1: Int, day: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.clear()
        cal.set(year, month1 - 1, day, 0, 0, 0)
        return cal.timeInMillis
    }

    private fun mondayIndexOf(dateMillis: Long): Int {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMillis }
        return (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
    }

    // --- Chores ---

    // Chore authoring (create/edit/delete) is restricted to CREATOR and ADMIN.
    // Enforced here independent of the UI, so a MEMBER invoking these directly
    // is a no-op. Assignment status changes (markComplete, claimAssignment,
    // runAssignments) are unrelated to authoring and are not gated.
    private fun canManageChores(): Boolean =
        currentUserRole.value == HouseholdRole.CREATOR || currentUserRole.value == HouseholdRole.ADMIN

    fun addChore(chore: Chore) = viewModelScope.launch {
        if (!canManageChores()) return@launch
        choreRepo.addChore(chore)
        refreshChores()
        // Posting is automatic: syncChoreBoard puts the new chore's current
        // occurrence on the pickup board. It's a no-op for chores already posted,
        // so adding one chore no longer floods the board with every other chore.
        syncChoreBoard()
    }

    fun updateChore(chore: Chore) = viewModelScope.launch {
        if (!canManageChores()) return@launch
        choreRepo.updateChore(chore)
        refreshChores()
    }

    fun deleteChore(choreId: String) = viewModelScope.launch {
        if (!canManageChores()) return@launch
        // HF-13: remove the chore's points entries before the assignments they
        // reference are deleted (the cascade subquery reads assignments).
        pointsRepo.deleteForChore(choreId)
        choreRepo.deleteChore(choreId)
        refreshChores()
        refreshAssignments()
        refreshTradeRequests()
        refreshPoints()
    }

    // Keeps the pickup board current and truthful. Idempotent, so it's safe to
    // run on every load and after adding a chore:
    //   1. Past-due claimed (PENDING) chores are marked MISSED.
    //   2. Each chore that has no occurrence for its current period gets one
    //      fresh AVAILABLE occurrence (with a suggested roommate). Claimed and
    //      completed occurrences are left alone, so finishing a chore does NOT
    //      respawn it until its next period comes around.
    private suspend fun syncChoreBoard() {
        val household = _household.value ?: run { refreshAssignments(); return }
        val roommates = _roommates.value
        val chores = _chores.value
        val now = System.currentTimeMillis()

        // 1. Overdue sweep.
        val choresById = chores.associateBy { it.id }
        choreRepo.getAssignments(household.id).forEach { a ->
            if (a.status == AssignmentStatus.PENDING) {
                val chore = choresById[a.choreId] ?: return@forEach
                if (now > ChoreDueTime.computeDueAt(a, chore)) {
                    choreRepo.updateAssignmentStatus(a.id, AssignmentStatus.MISSED)
                }
            }
        }

        // 2. Post each chore's current-period occurrence if it's missing. Needs
        //    roommates so the algorithm can pick a suggestion. ChoreScheduler
        //    decides the anchor per frequency; already-posted periods are skipped.
        if (roommates.isNotEmpty() && chores.isNotEmpty()) {
            var history = choreRepo.getAssignments(household.id)
            val busyByRoommate = mutableMapOf<String, List<BusyBlock>>()
            for (r in roommates) busyByRoommate[r.userId] = householdRepo.getBusyBlocks(r.userId)
            val effortByChoreId = chores.associate { it.id to it.effortScore }
            val today = todayMidnight()

            for (chore in chores) {
                val existingAnchors = history
                    .filter { it.choreId == chore.id }
                    .map { it.weekStart }
                    .toSet()
                val anchor = ChoreScheduler.anchorToPost(
                    chore, existingAnchors, weekStart, today, now
                ) ?: continue
                val occurrence = AssignmentAlgorithm.assignOne(
                    chore, roommates, busyByRoommate, history, anchor, effortByChoreId
                )
                choreRepo.addAssignment(occurrence)
                history = history + occurrence // so the next chore's scoring sees it
            }
        }

        refreshAssignments()
    }

    fun markComplete(assignmentId: String) = viewModelScope.launch {
        val householdId = _household.value?.id ?: return@launch
        val completed = choreRepo.getAssignments(householdId).find { it.id == assignmentId } ?: return@launch
        if (completed.status != AssignmentStatus.PENDING) return@launch
        choreRepo.updateAssignmentStatus(assignmentId, AssignmentStatus.COMPLETED)
        userRepo.incrementCompletedCount(completed.assignedToRoommateId)

        val chore = _chores.value.find { it.id == completed.choreId }

        // HF-13: award points for the completion. Runs after the completion has
        // been persisted so a points failure can never block marking done.
        // Idempotent — the ledger PK is the assignment id.
        if (chore != null) {
            pointsRepo.award(
                PointsEntry(
                    id = completed.id,
                    householdId = householdId,
                    userId = completed.assignedToRoommateId,
                    choreName = chore.name,
                    points = PointsPolicy.pointsFor(chore),
                    weekStart = completed.weekStart,
                    awardedAt = System.currentTimeMillis()
                )
            )
            refreshPoints()
        }
        // No instant respawn: the next occurrence of a recurring chore is posted
        // by syncChoreBoard() when its next period comes around (next day / week /
        // interval), so completing a chore doesn't immediately put it back on the
        // pickup board. `chore` above is still used for the points award.
        refreshAssignments()
    }

    fun claimAssignment(assignmentId: String) = viewModelScope.launch {
        val me = _currentUser.value?.uid ?: return@launch
        val householdId = _household.value?.id ?: return@launch
        val a = choreRepo.getAssignments(householdId).find { it.id == assignmentId } ?: return@launch
        if (a.status != AssignmentStatus.AVAILABLE) return@launch

        val chore = _chores.value.find { it.id == a.choreId }
        val hasConflict = chore != null &&
            AssignmentAlgorithm.isBusyAt(
                householdRepo.getBusyBlocks(me), chore.dueDayOfWeek, chore.dueHour,
                a.weekStart + chore.dueDayOfWeek * DAY_MS
            )
        val reason = if (a.assignedToRoommateId == me) a.reason else {
            val recommended = _roommates.value.find { it.userId == a.assignedToRoommateId }?.displayName
            "Picked up — was recommended for ${recommended ?: "another roommate"}"
        }
        choreRepo.updateAssignment(
            a.copy(
                assignedToRoommateId = me,
                status = AssignmentStatus.PENDING,
                reason = reason,
                hasConflict = hasConflict
            )
        )
        refreshAssignments()
    }

    // --- Trades ---

    // Owner of a claimed (PENDING) assignment asks a specific roommate to take
    // it over. One pending request per assignment; cancel to re-send.
    fun requestTrade(assignmentId: String, toUserId: String, reason: String) = viewModelScope.launch {
        val me = _currentUser.value?.uid ?: return@launch
        val householdId = _household.value?.id ?: return@launch
        if (toUserId == me) return@launch
        val a = choreRepo.getAssignments(householdId).find { it.id == assignmentId } ?: return@launch
        if (a.status != AssignmentStatus.PENDING || a.assignedToRoommateId != me) return@launch
        val existing = choreRepo.getTradeRequests(householdId)
        if (existing.any { it.assignmentId == assignmentId && it.status == TradeStatus.PENDING }) return@launch

        choreRepo.addTradeRequest(
            TradeRequest(
                id = UUID.randomUUID().toString(),
                assignmentId = assignmentId,
                householdId = householdId,
                fromUserId = me,
                toUserId = toUserId,
                reason = reason.trim(),
                status = TradeStatus.PENDING,
                createdAt = System.currentTimeMillis()
            )
        )
        refreshTradeRequests()
    }

    fun cancelTradeRequest(requestId: String) = viewModelScope.launch {
        val me = _currentUser.value?.uid ?: return@launch
        val request = _tradeRequests.value.find { it.id == requestId } ?: return@launch
        if (request.fromUserId != me || request.status != TradeStatus.PENDING) return@launch
        choreRepo.deleteTradeRequest(requestId)
        refreshTradeRequests()
    }

    fun respondToTrade(requestId: String, accept: Boolean) = viewModelScope.launch {
        val me = _currentUser.value?.uid ?: return@launch
        val householdId = _household.value?.id ?: return@launch
        val request = choreRepo.getTradeRequests(householdId).find { it.id == requestId } ?: return@launch
        if (request.toUserId != me || request.status != TradeStatus.PENDING) return@launch

        val a = choreRepo.getAssignments(householdId).find { it.id == request.assignmentId }
        // Stale request: the assignment was completed, missed, or re-routed
        // since it was sent — drop it instead of transferring.
        if (a == null || a.status != AssignmentStatus.PENDING || a.assignedToRoommateId != request.fromUserId) {
            choreRepo.deleteTradeRequest(requestId)
            refreshTradeRequests()
            return@launch
        }

        val resolved = choreRepo.resolveTradeRequest(
            requestId,
            if (accept) TradeStatus.ACCEPTED else TradeStatus.DENIED
        )
        if (resolved && accept) {
            val chore = _chores.value.find { it.id == a.choreId }
            val hasConflict = chore != null &&
                AssignmentAlgorithm.isBusyAt(
                    householdRepo.getBusyBlocks(me), chore.dueDayOfWeek, chore.dueHour,
                    a.weekStart + chore.dueDayOfWeek * DAY_MS
                )
            val fromName = _roommates.value.find { it.userId == request.fromUserId }?.displayName ?: "a roommate"
            choreRepo.updateAssignment(
                a.copy(
                    assignedToRoommateId = me,
                    reason = "Traded from $fromName: ${request.reason}",
                    hasConflict = hasConflict
                )
            )
            refreshAssignments()
        }
        refreshTradeRequests()
    }

    private suspend fun refreshTradeRequests() {
        _tradeRequests.value = choreRepo.getTradeRequests(_household.value?.id ?: return)
        val uid = _currentUser.value?.uid ?: return
        val nameById = _roommates.value.associate { it.userId to it.displayName }
        notificationDispatcher.onTradeRequestsRefreshed(uid, _tradeRequests.value, nameById)
    }

    // --- Bulletin ---

    fun addBulletinPost(title: String, message: String, isEvent: Boolean) = viewModelScope.launch {
        val user = _currentUser.value ?: return@launch
        val household = _household.value ?: return@launch
        val post = BulletinPost(
            id = UUID.randomUUID().toString(),
            householdId = household.id,
            authorName = user.displayName,
            title = title,
            message = message,
            isEvent = isEvent,
            timestamp = System.currentTimeMillis()
        )
        bulletinRepo.addPost(post)
        refreshBulletinPosts()
    }

    fun deleteBulletinPost(postId: String) = viewModelScope.launch {
        bulletinRepo.deletePost(postId)
        refreshBulletinPosts()
    }

    private suspend fun refreshBulletinPosts() {
        val household = _household.value ?: return
        _bulletinPosts.value = bulletinRepo.getPosts(household.id)
        val user = _currentUser.value ?: return
        notificationDispatcher.onBulletinPostsRefreshed(user.uid, user.displayName, _bulletinPosts.value)
    }

    fun refreshOverdue() = viewModelScope.launch {
        val householdId = _household.value?.id ?: return@launch
        val now = System.currentTimeMillis()
        val choresById = _chores.value.associateBy { it.id }
        choreRepo.getAssignments(householdId).forEach { a ->
            if (a.status == AssignmentStatus.PENDING) {
                val chore = choresById[a.choreId] ?: return@forEach
                if (now > ChoreDueTime.computeDueAt(a, chore)) {
                    choreRepo.updateAssignmentStatus(a.id, AssignmentStatus.MISSED)
                }
            }
        }
        refreshAssignments()
    }

    private suspend fun refreshChores() {
        _chores.value = choreRepo.getChores(_household.value?.id ?: return)
    }

    private suspend fun refreshAssignments() {
        choreRepo.deleteStaleAvailable(weekStart)
        _assignments.value = choreRepo.getAssignments(_household.value?.id ?: return)
        refreshCompletionCounts()
        val uid = _currentUser.value?.uid ?: return
        val choresById = _chores.value.associateBy { it.id }
        notificationDispatcher.onAssignmentsRefreshed(uid, _assignments.value, choresById)
    }

    private suspend fun refreshCompletionCounts() {
        val counts = mutableMapOf<String, Int>()
        for (r in _roommates.value) {
            counts[r.userId] = choreRepo.getCompletedCount(r.userId)
        }
        _completionCounts.value = counts
    }

    // --- HF-13: points ---

    private suspend fun refreshPoints() {
        _pointsEntries.value = pointsRepo.getEntries(_household.value?.id ?: return)
    }

    private fun buildLeaderboard(
        entries: List<PointsEntry>,
        roommates: List<Roommate>
    ): List<LeaderboardEntry> {
        val weeklyByUser = entries.filter { it.weekStart == weekStart }
            .groupBy { it.userId }
            .mapValues { (_, es) -> es.sumOf { it.points } }
        val allTimeByUser = entries.groupBy { it.userId }
            .mapValues { (_, es) -> es.sumOf { it.points } }

        return roommates
            .map { r ->
                Triple(r, weeklyByUser[r.userId] ?: 0, allTimeByUser[r.userId] ?: 0)
            }
            .sortedWith(
                compareByDescending<Triple<Roommate, Int, Int>> { it.second }
                    .thenByDescending { it.third }
                    .thenBy { it.first.displayName.lowercase() }
            )
            .mapIndexed { index, (r, weekly, allTime) ->
                LeaderboardEntry(
                    userId = r.userId,
                    displayName = r.displayName,
                    weeklyPoints = weekly,
                    allTimePoints = allTime,
                    rank = index + 1
                )
            }
    }

    private fun buildPointsSummary(entries: List<PointsEntry>, uid: String?): PointsSummary {
        if (uid == null) return PointsSummary()
        val mine = entries.filter { it.userId == uid }
        val weekly = mine.filter { it.weekStart == weekStart }.sumOf { it.points }
        val allTime = mine.sumOf { it.points }
        val weeksWithPoints = mine.map { it.weekStart }.toSet()
        return PointsSummary(
            weeklyPoints = weekly,
            allTimePoints = allTime,
            level = PointsPolicy.levelFor(allTime),
            pointsIntoLevel = PointsPolicy.pointsIntoLevel(allTime),
            pointsForNextLevel = PointsPolicy.POINTS_PER_LEVEL,
            streakWeeks = PointsPolicy.streak(weeksWithPoints, weekStart, WEEK_MILLIS)
        )
    }

    // --- HF-15: shared expenses ---

    // Records an expense paid by [paidByUserId], split equally among
    // [participantUserIds]. No-op if the amount or participant set is invalid.
    fun addExpense(
        description: String,
        amountCents: Int,
        paidByUserId: String,
        participantUserIds: List<String>
    ) = viewModelScope.launch {
        val household = _household.value ?: return@launch
        val creator = _currentUser.value?.uid ?: return@launch
        if (amountCents <= 0 || participantUserIds.isEmpty()) return@launch

        val expenseId = UUID.randomUUID().toString()
        val expense = Expense(
            id = expenseId,
            householdId = household.id,
            paidByUserId = paidByUserId,
            createdByUserId = creator,
            description = description.trim(),
            amountCents = amountCents,
            splitType = SplitType.EQUAL,
            createdAt = System.currentTimeMillis()
        )
        val shares = ExpenseMath.equalSplit(amountCents, participantUserIds).map { (userId, cents) ->
            ExpenseShare(
                id = "$expenseId:$userId",
                expenseId = expenseId,
                householdId = household.id,
                userId = userId,
                shareCents = cents
            )
        }
        expenseRepo.addExpense(expense, shares)
        refreshExpenses()
    }

    // Deletes an expense. Allowed for its creator or a household ADMIN/CREATOR.
    fun deleteExpense(expenseId: String) = viewModelScope.launch {
        val me = _currentUser.value?.uid ?: return@launch
        val expense = _expenses.value.find { it.id == expenseId } ?: return@launch
        val isManager = currentUserRole.value == HouseholdRole.CREATOR ||
            currentUserRole.value == HouseholdRole.ADMIN
        if (expense.createdByUserId != me && !isManager) return@launch
        expenseRepo.deleteExpense(expenseId)
        refreshExpenses()
    }

    // Records a repayment from [fromUserId] to [toUserId], offsetting balances.
    fun settleUp(fromUserId: String, toUserId: String, amountCents: Int) = viewModelScope.launch {
        val household = _household.value ?: return@launch
        if (amountCents <= 0 || fromUserId == toUserId) return@launch
        expenseRepo.addSettlement(
            Settlement(
                id = UUID.randomUUID().toString(),
                householdId = household.id,
                fromUserId = fromUserId,
                toUserId = toUserId,
                amountCents = amountCents,
                createdAt = System.currentTimeMillis()
            )
        )
        refreshExpenses()
    }

    fun deleteSettlement(settlementId: String) = viewModelScope.launch {
        expenseRepo.deleteSettlement(settlementId)
        refreshExpenses()
    }

    private suspend fun refreshExpenses() {
        val householdId = _household.value?.id ?: return
        _expenses.value = expenseRepo.getExpenses(householdId)
        _expenseShares.value = expenseRepo.getShares(householdId)
        _settlements.value = expenseRepo.getSettlements(householdId)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppViewModel(
                    authRepo = AppContainer.authRepository,
                    userRepo = AppContainer.userRepository,
                    householdRepo = AppContainer.householdRepository,
                    choreRepo = AppContainer.choreRepository,
                    bulletinRepo = AppContainer.bulletinRepository,
                    pointsRepo = AppContainer.pointsRepository,
                    expenseRepo = AppContainer.expenseRepository,
                    notificationDispatcher = AppContainer.notificationDispatcher
                )
            }
        }
    }
}

private const val DAY_MS: Long = 24L * 3600 * 1000
private const val WEEK_MILLIS: Long = 7L * 24 * 3600 * 1000

private fun currentWeekStart(): Long {
    val cal = Calendar.getInstance()
    cal.firstDayOfWeek = Calendar.MONDAY
    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

// Midnight (local) at the start of today — the period anchor for DAILY chores.
private fun todayMidnight(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
