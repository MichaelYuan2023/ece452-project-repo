package com.example.houseflow.data.repository

import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Household
import com.example.houseflow.model.Roommate

// Backed by Room. All calls are suspend so they run off the main thread.
interface HouseholdRepository {
    suspend fun joinHousehold(code: String): Household?
    // The household this user already belongs to, if any — used to restore the
    // session on launch so returning members skip the join screen.
    suspend fun getHouseholdForUser(userId: String): Household?
    suspend fun addRoommateToHousehold(householdId: String, roommate: Roommate)
    suspend fun getRoommates(householdId: String): List<Roommate>
    suspend fun getBusyBlocks(roommateId: String): List<BusyBlock>
    suspend fun addBusyBlock(block: BusyBlock)
    suspend fun deleteBusyBlock(blockId: String)
}
