package com.example.houseflow.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.houseflow.data.AppContainer
import com.example.houseflow.data.repository.ChoreRepository
import com.example.houseflow.data.repository.HouseholdRepository
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.ChoreFrequency
import com.example.houseflow.model.Household
import com.example.houseflow.model.Roommate
import com.example.houseflow.util.AssignmentAlgorithm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

class AppViewModel(
    private val householdRepo: HouseholdRepository,
    private val choreRepo: ChoreRepository
) : ViewModel() {

    // Migration seam: for real auth, replace name-only sign-in with token-based auth.
    private val _currentUser = MutableStateFlow<Roommate?>(null)
    val currentUser: StateFlow<Roommate?> = _currentUser.asStateFlow()

    private val _household = MutableStateFlow<Household?>(null)
    val household: StateFlow<Household?> = _household.asStateFlow()

    private val _roommates = MutableStateFlow<List<Roommate>>(emptyList())
    val roommates: StateFlow<List<Roommate>> = _roommates.asStateFlow()

    private val _myBusyBlocks = MutableStateFlow<List<BusyBlock>>(emptyList())
    val myBusyBlocks: StateFlow<List<BusyBlock>> = _myBusyBlocks.asStateFlow()

    // Busy blocks for every roommate in the household, keyed by roommate id.
    // Powers the "see everyone's availability" calendar view.
    private val _householdBusyBlocks = MutableStateFlow<Map<String, List<BusyBlock>>>(emptyMap())
    val householdBusyBlocks: StateFlow<Map<String, List<BusyBlock>>> = _householdBusyBlocks.asStateFlow()

    private val _chores = MutableStateFlow<List<Chore>>(emptyList())
    val chores: StateFlow<List<Chore>> = _chores.asStateFlow()

    private val _assignments = MutableStateFlow<List<ChoreAssignment>>(emptyList())
    val assignments: StateFlow<List<ChoreAssignment>> = _assignments.asStateFlow()

    // Used for the "run assignments" button — resets when new chores are added.
    private val _assignmentsRun = MutableStateFlow(false)
    val assignmentsRun: StateFlow<Boolean> = _assignmentsRun.asStateFlow()

    val weekStart: Long = currentWeekStart()

    // --- Auth ---

    fun createAccount(name: String) {
        _currentUser.value = Roommate(id = "user-${name.lowercase().replace(" ", "-")}", name = name.trim())
    }

    // Returns true if the code is valid.
    fun joinHousehold(code: String): Boolean {
        val h = householdRepo.joinHousehold(code) ?: return false
        val user = _currentUser.value ?: return false
        householdRepo.addRoommateToHousehold(h.id, user)
        _household.value = h
        _roommates.value = householdRepo.getRoommates(h.id)
        refreshHouseholdBlocks()
        return true
    }

    // --- Availability ---

    fun addBusyBlock(block: BusyBlock) {
        householdRepo.addBusyBlock(block)
        refreshMyBlocks()
        refreshHouseholdBlocks()
    }

    fun deleteBusyBlock(blockId: String) {
        householdRepo.deleteBusyBlock(blockId)
        refreshMyBlocks()
        refreshHouseholdBlocks()
    }

    private fun refreshMyBlocks() {
        _myBusyBlocks.value = householdRepo.getBusyBlocks(_currentUser.value?.id ?: return)
    }

    private fun refreshHouseholdBlocks() {
        val householdId = _household.value?.id ?: return
        _householdBusyBlocks.value = householdRepo.getRoommates(householdId).associate { r ->
            r.id to householdRepo.getBusyBlocks(r.id)
        }
    }

    // --- Chores ---

    fun addChore(chore: Chore) {
        choreRepo.addChore(chore)
        refreshChores()
        runAssignments()
    }

    fun updateChore(chore: Chore) {
        choreRepo.updateChore(chore)
        refreshChores()
    }

    fun deleteChore(choreId: String) {
        choreRepo.deleteChore(choreId)
        refreshChores()
        refreshAssignments()
    }

    fun runAssignments() {
        val household = _household.value ?: return
        val roommates = _roommates.value
        val chores = _chores.value
        if (chores.isEmpty() || roommates.isEmpty()) return

        val busyBlocksByRoommate = roommates.associate { r ->
            r.id to householdRepo.getBusyBlocks(r.id)
        }
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

    fun markComplete(assignmentId: String) {
        val householdId = _household.value?.id ?: return
        val completed = choreRepo.getAssignments(householdId).find { it.id == assignmentId } ?: return
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
                val busyByRoommate = roommates.associate { it.id to householdRepo.getBusyBlocks(it.id) }
                val next = AssignmentAlgorithm.assignOne(chore, roommates, busyByRoommate, history, nextWeekStart)
                choreRepo.addAssignment(next)
            }
        }
        refreshAssignments()
    }

    fun swapAssignment(assignmentId: String) {
        val householdId = _household.value?.id ?: return
        val current = choreRepo.getAssignments(householdId).find { it.id == assignmentId } ?: return
        val chore = _chores.value.find { it.id == current.choreId } ?: return
        val candidates = _roommates.value.filter { it.id != current.assignedToRoommateId }
        if (candidates.isEmpty()) return

        val busyByRoommate = candidates.associate { it.id to householdRepo.getBusyBlocks(it.id) }
        val history = choreRepo.getAssignments(householdId)
        val reassigned = AssignmentAlgorithm
            .assignOne(chore, candidates, busyByRoommate, history, current.weekStart)
            .copy(id = current.id)
        choreRepo.updateAssignment(reassigned)
        refreshAssignments()
    }

    fun refreshOverdue() {
        val householdId = _household.value?.id ?: return
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

    private fun refreshChores() {
        _chores.value = choreRepo.getChores(_household.value?.id ?: return)
    }

    private fun refreshAssignments() {
        _assignments.value = choreRepo.getAssignments(_household.value?.id ?: return)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppViewModel(
                    householdRepo = AppContainer.householdRepository,
                    choreRepo = AppContainer.choreRepository
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
