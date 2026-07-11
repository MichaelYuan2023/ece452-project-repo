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
import com.example.houseflow.model.BlockType
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

    private val _household = MutableStateFlow<Household?>(null)
    val household: StateFlow<Household?> = _household.asStateFlow()

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
                    val user = firebaseUser.toUser()
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
        authRepo.signUp(displayName.trim(), email.trim(), password).map { }

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
        val household = householdRepo.getHouseholdForUser(user.uid)
        if (household != null) {
            _household.value = household
            loadHouseholdData(household)
        } else {
            _household.value = null
        }
        _restoring.value = false
    }

    private suspend fun loadHouseholdData(household: Household) {
        _roommates.value = householdRepo.getRoommates(household.id)
        refreshMyBlocks()
        refreshHouseholdBlocks()
        refreshChores()
        refreshAssignments()
        _bulletinPosts.value = bulletinRepo.getPosts(household.id)
        _assignmentsRun.value = _assignments.value.any { it.weekStart == weekStart }
    }

    private fun clearSessionState() {
        _household.value = null
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
        val household = householdRepo.joinHousehold(code) ?: return false

        userRepo.upsertUser(user)
        householdRepo.addRoommateToHousehold(
            household.id,
            Roommate(userId = user.uid, householdId = household.id, displayName = user.displayName)
        )
        seedUserSchedule(user.uid)

        _household.value = household
        loadHouseholdData(household)
        return true
    }

    // Give a brand-new member a realistic starter timetable. Skipped if they
    // already have blocks (e.g. a demo roommate signing in as themselves).
    private suspend fun seedUserSchedule(userId: String) {
        if (householdRepo.getBusyBlocks(userId).isNotEmpty()) return

        fun block(n: Int, day: Int, start: Int, end: Int, title: String, type: BlockType) =
            BusyBlock("seed-$userId-$n", userId, day, start, end, title, type)

        val blocks = listOf(
            block(1, 0, 9, 12, "CS 446 Lecture", BlockType.CLASS),
            block(2, 0, 14, 16, "ECE 452 Lab", BlockType.CLASS),
            block(3, 1, 10, 12, "MATH 239 Lecture", BlockType.CLASS),
            block(4, 2, 9, 12, "CS 446 Lecture", BlockType.CLASS),
            block(5, 2, 16, 19, "Part-time job", BlockType.WORK),
            block(6, 3, 10, 12, "MATH 239 Lecture", BlockType.CLASS),
            block(7, 3, 13, 15, "Study group", BlockType.OTHER),
            block(8, 4, 9, 11, "CS 446 Tutorial", BlockType.CLASS),
            block(9, 4, 16, 19, "Part-time job", BlockType.WORK),
            block(10, 5, 11, 13, "Intramurals", BlockType.CLUB),
        )
        blocks.forEach { householdRepo.addBusyBlock(it) }
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
