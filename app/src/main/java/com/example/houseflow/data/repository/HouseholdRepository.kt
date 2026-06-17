package com.example.houseflow.data.repository

import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Household
import com.example.houseflow.model.Roommate

// Migration seam: swap InMemoryHouseholdRepository for a Room-backed implementation
// without touching any ViewModel or UI code.
interface HouseholdRepository {
    fun getHousehold(): Household?
    fun joinHousehold(code: String): Household?
    fun addRoommateToHousehold(householdId: String, roommate: Roommate)
    fun getRoommates(householdId: String): List<Roommate>
    fun getBusyBlocks(roommateId: String): List<BusyBlock>
    fun addBusyBlock(block: BusyBlock)
    fun deleteBusyBlock(blockId: String)
}
