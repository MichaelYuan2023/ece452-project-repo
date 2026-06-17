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
        return true
    }

    // --- Availability ---

    fun addBusyBlock(block: BusyBlock) {
        householdRepo.addBusyBlock(block)
        refreshMyBlocks()
    }

    fun deleteBusyBlock(blockId: String) {
        householdRepo.deleteBusyBlock(blockId)
        refreshMyBlocks()
    }

    private fun refreshMyBlocks() {
        _myBusyBlocks.value = householdRepo.getBusyBlocks(_currentUser.value?.id ?: return)
    }

    // --- Chores ---

    fun addChore(chore: Chore) {
        choreRepo.addChore(chore)
        _assignmentsRun.value = false
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
        val alreadyAssignedIds = history.filter { it.weekStart == weekStart }.map { it.choreId }.toSet()
        val toAssign = chores.filter { it.id !in alreadyAssignedIds }

        val newAssignments = AssignmentAlgorithm.assignAll(
            chores = toAssign,
            roommates = roommates,
            busyBlocksByRoommate = busyBlocksByRoommate,
            history = history,
            weekStart = weekStart
        )
        newAssignments.forEach { choreRepo.addAssignment(it) }
        _assignmentsRun.value = true
        refreshAssignments()
    }

    fun markComplete(assignmentId: String) {
        choreRepo.updateAssignmentStatus(assignmentId, AssignmentStatus.COMPLETED)
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
