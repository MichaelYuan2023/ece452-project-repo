package com.example.houseflow.data.repository

import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Household
import com.example.houseflow.model.HouseholdRole
import com.example.houseflow.model.Roommate

// Backed by Room. All calls are suspend so they run off the main thread.
interface HouseholdRepository {
    // Resolves an invite code to its household. Failure means the code doesn't
    // match any household — the ViewModel surfaces this as an inline UI error.
    suspend fun joinHousehold(code: String): Result<Household>
    // Creates a new household with a freshly generated, unique invite code and
    // adds the creator as its first member, with role = CREATOR.
    suspend fun createHousehold(name: String, creatorUserId: String, creatorDisplayName: String): Household
    // Every household this user belongs to — a user can be a member of many.
    suspend fun getHouseholdsForUser(userId: String): List<Household>
    suspend fun getHousehold(householdId: String): Household?
    suspend fun addRoommateToHousehold(householdId: String, roommate: Roommate)
    suspend fun getRoommates(householdId: String): List<Roommate>
    // Changes a member's role. Callers must enforce the promote/demote
    // permission matrix themselves (see AppViewModel.promoteToAdmin/
    // demoteToMember) — this just persists whatever role is passed in.
    suspend fun updateRoommateRole(householdId: String, userId: String, newRole: HouseholdRole)
    suspend fun getBusyBlocks(roommateId: String): List<BusyBlock>
    suspend fun addBusyBlock(block: BusyBlock)
    suspend fun deleteBusyBlock(blockId: String)
}
