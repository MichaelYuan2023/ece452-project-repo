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
import com.example.houseflow.data.repository.HouseholdRepository
import com.example.houseflow.data.repository.UserRepository
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.BulletinPost
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.ChoreFrequency
import com.example.houseflow.model.Household
import com.example.houseflow.model.Roommate
import com.example.houseflow.model.User
import com.example.houseflow.util.AssignmentAlgorithm
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

// Drives top-level navigation. Derived from auth + household state.
enum class SessionState { LOADING, SIGNED_OUT, NEEDS_HOUSEHOLD, IN_HOUSEHOLD }

class AppViewModel(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
    private val householdRepo: HouseholdRepository,
    private val choreRepo: ChoreRepository,
    private val bulletinRepo: BulletinRepository
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

    // Busy blocks for every roommate in the household, keyed by userId.
    private val _householdBusyBlocks = MutableStateFlow<Map<String, List<BusyBlock>>>(emptyMap())
    val householdBusyBlocks: StateFlow<Map<String, List<BusyBlock>>> = _householdBusyBlocks.asStateFlow()

    private val _chores = MutableStateFlow<List<Chore>>(emptyList())
    val chores: StateFlow<List<Chore>> = _chores.asStateFlow()

    private val _assignments = MutableStateFlow<List<ChoreAssignment>>(emptyList())
    val assignments: StateFlow<List<ChoreAssignment>> = _assignments.asStateFlow()

    // Used for the "run assignments" button — resets when new chores are added.
    private val _assignmentsRun = MutableStateFlow(false)
    val assignmentsRun: StateFlow<Boolean> = _assignmentsRun.asStateFlow()

    private val _bulletinPosts = MutableStateFlow<List<BulletinPost>>(emptyList())
    val bulletinPosts: StateFlow<List<BulletinPost>> = _bulletinPosts.asStateFlow()

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

    val weekStart: Long = currentWeekStart()

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
        refreshHouseholdBlocks()
        refreshChores()
        refreshAssignments()
        _bulletinPosts.value = bulletinRepo.getPosts(household.id)
        _assignmentsRun.value = _assignments.value.any { it.weekStart == weekStart }
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
        _householdBusyBlocks.value = emptyMap()
        _chores.value = emptyList()
        _assignments.value = emptyList()
        _assignmentsRun.value = false
        _bulletinPosts.value = emptyList()
    }

    // --- Household ---

    // Returns true if the invite code is valid.
    suspend fun joinHousehold(code: String): Boolean {
        val user = _currentUser.value ?: return false
        val household = householdRepo.joinHousehold(code).getOrNull() ?: return false

        householdRepo.addRoommateToHousehold(
            household.id,
            Roommate(userId = user.uid, householdId = household.id, displayName = user.displayName)
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
        refreshHouseholdBlocks()
    }

    fun deleteBusyBlock(blockId: String) = viewModelScope.launch {
        householdRepo.deleteBusyBlock(blockId)
        refreshMyBlocks()
        refreshHouseholdBlocks()
    }

    private suspend fun refreshMyBlocks() {
        _myBusyBlocks.value = householdRepo.getBusyBlocks(_currentUser.value?.uid ?: return)
    }

    private suspend fun refreshHouseholdBlocks() {
        val householdId = _household.value?.id ?: return
        val map = mutableMapOf<String, List<BusyBlock>>()
        for (roommate in householdRepo.getRoommates(householdId)) {
            map[roommate.userId] = householdRepo.getBusyBlocks(roommate.userId)
        }
        _householdBusyBlocks.value = map
    }

    // --- Chores ---

    fun addChore(chore: Chore) = viewModelScope.launch {
        choreRepo.addChore(chore)
        refreshChores()
        runAssignmentsInternal()
    }

    fun updateChore(chore: Chore) = viewModelScope.launch {
        choreRepo.updateChore(chore)
        refreshChores()
    }

    fun deleteChore(choreId: String) = viewModelScope.launch {
        choreRepo.deleteChore(choreId)
        refreshChores()
        refreshAssignments()
    }

    fun runAssignments() = viewModelScope.launch { runAssignmentsInternal() }

    private suspend fun runAssignmentsInternal() {
        val household = _household.value ?: return
        val roommates = _roommates.value
        val chores = _chores.value
        if (chores.isEmpty() || roommates.isEmpty()) return

        val busyBlocksByRoommate = mutableMapOf<String, List<BusyBlock>>()
        for (r in roommates) busyBlocksByRoommate[r.userId] = householdRepo.getBusyBlocks(r.userId)

        val history = choreRepo.getAssignments(household.id)
        val msPerDay = 86_400_000L
        val weekEnd = weekStart + 7 * msPerDay

        val slots = mutableListOf<Pair<Chore, Long>>()
        for (c in chores) {
            when (c.frequency) {
                ChoreFrequency.WEEKLY -> {
                    if (history.none { it.choreId == c.id && it.weekStart == weekStart }) {
                        slots.add(c to weekStart)
                    }
                }
                ChoreFrequency.ONE_TIME -> {
                    if (history.none { it.choreId == c.id }) {
                        slots.add(c to (weekStart + c.dueDayOfWeek * msPerDay))
                    }
                }
                ChoreFrequency.DAILY -> {
                    var date = weekStart
                    while (date < weekEnd) {
                        val d = date
                        if (history.none { it.choreId == c.id && it.weekStart == d }) {
                            slots.add(c to date)
                        }
                        date += msPerDay
                    }
                }
                ChoreFrequency.EVERY_N_DAYS -> {
                    val interval = (c.intervalDays ?: 2) * msPerDay
                    var date = weekStart + c.dueDayOfWeek * msPerDay
                    while (date < weekEnd) {
                        val d = date
                        if (history.none { it.choreId == c.id && it.weekStart == d }) {
                            slots.add(c to date)
                        }
                        date += interval
                    }
                }
            }
        }

        val newAssignments = mutableListOf<ChoreAssignment>()
        for ((chore, dueDate) in slots) {
            val allHistory = history + newAssignments
            val assignment = AssignmentAlgorithm.assignOne(
                chore, roommates, busyBlocksByRoommate, allHistory, dueDate
            )
            newAssignments.add(assignment)
        }

        newAssignments.forEach { choreRepo.addAssignment(it) }
        _assignmentsRun.value = true
        refreshAssignments()
    }

    fun markComplete(assignmentId: String) = viewModelScope.launch {
        val householdId = _household.value?.id ?: return@launch
        val completed = choreRepo.getAssignments(householdId).find { it.id == assignmentId } ?: return@launch
        choreRepo.updateAssignmentStatus(assignmentId, AssignmentStatus.COMPLETED)

        val chore = _chores.value.find { it.id == completed.choreId }
        val roommates = _roommates.value
        if (chore != null && chore.frequency != ChoreFrequency.ONE_TIME && roommates.isNotEmpty()) {
            val msPerDay = 24L * 3600 * 1000
            val nextWeekStart = completed.weekStart + when (chore.frequency) {
                ChoreFrequency.DAILY -> msPerDay
                ChoreFrequency.EVERY_N_DAYS -> (chore.intervalDays ?: 2) * msPerDay
                else -> 7 * msPerDay
            }
            val history = choreRepo.getAssignments(householdId)
            val alreadyScheduled = history.any { it.choreId == chore.id && it.weekStart == nextWeekStart }
            if (!alreadyScheduled) {
                val busyByRoommate = mutableMapOf<String, List<BusyBlock>>()
                for (r in roommates) busyByRoommate[r.userId] = householdRepo.getBusyBlocks(r.userId)
                val next = AssignmentAlgorithm.assignOne(chore, roommates, busyByRoommate, history, nextWeekStart)
                choreRepo.addAssignment(next)
            }
        }
        refreshAssignments()
    }

    fun swapAssignment(assignmentId: String) = viewModelScope.launch {
        val householdId = _household.value?.id ?: return@launch
        val current = choreRepo.getAssignments(householdId).find { it.id == assignmentId } ?: return@launch
        val chore = _chores.value.find { it.id == current.choreId } ?: return@launch
        val candidates = _roommates.value.filter { it.userId != current.assignedToRoommateId }
        if (candidates.isEmpty()) return@launch

        val busyByRoommate = mutableMapOf<String, List<BusyBlock>>()
        for (r in candidates) busyByRoommate[r.userId] = householdRepo.getBusyBlocks(r.userId)
        val history = choreRepo.getAssignments(householdId)
        val reassigned = AssignmentAlgorithm
            .assignOne(chore, candidates, busyByRoommate, history, current.weekStart)
            .copy(id = current.id)
        choreRepo.updateAssignment(reassigned)
        refreshAssignments()
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
        _bulletinPosts.value = bulletinRepo.getPosts(household.id)
    }

    fun deleteBulletinPost(postId: String) = viewModelScope.launch {
        bulletinRepo.deletePost(postId)
        _household.value?.let { _bulletinPosts.value = bulletinRepo.getPosts(it.id) }
    }

    fun refreshOverdue() = viewModelScope.launch {
        val householdId = _household.value?.id ?: return@launch
        val now = System.currentTimeMillis()
        val choresById = _chores.value.associateBy { it.id }
        choreRepo.getAssignments(householdId).forEach { a ->
            if (a.status == AssignmentStatus.PENDING) {
                val chore = choresById[a.choreId] ?: return@forEach
                val due = if (chore.frequency == ChoreFrequency.WEEKLY) {
                    a.weekStart + chore.dueDayOfWeek * 86_400_000L + chore.dueHour * 3_600_000L
                } else {
                    a.weekStart + chore.dueHour * 3_600_000L
                }
                if (now > due) choreRepo.updateAssignmentStatus(a.id, AssignmentStatus.MISSED)
            }
        }
        refreshAssignments()
    }

    private suspend fun refreshChores() {
        _chores.value = choreRepo.getChores(_household.value?.id ?: return)
    }

    private suspend fun refreshAssignments() {
        _assignments.value = choreRepo.getAssignments(_household.value?.id ?: return)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppViewModel(
                    authRepo = AppContainer.authRepository,
                    userRepo = AppContainer.userRepository,
                    householdRepo = AppContainer.householdRepository,
                    choreRepo = AppContainer.choreRepository,
                    bulletinRepo = AppContainer.bulletinRepository
                )
            }
        }
    }
}

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
