package com.example.houseflow.data.repository

import com.example.houseflow.model.BlockType
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Household
import com.example.houseflow.model.Roommate

// Migration seam: replace these mutable lists with Room DAOs.
// Seed data lives here so it's easy to find and remove when real persistence is added.
class InMemoryHouseholdRepository : HouseholdRepository {

    private val household = Household(
        id = "household-1",
        name = "Demo House",
        inviteCode = "DEMO123"
    )

    // Three pre-seeded roommates with varied schedules so the algorithm has real differences to work with.
    private val maya = Roommate(id = "r-maya", name = "Maya")
    private val jake = Roommate(id = "r-jake", name = "Jake")
    private val priya = Roommate(id = "r-priya", name = "Priya")

    private val roommates = mutableListOf(maya, jake, priya)

    // Busy blocks: 0=Mon … 6=Sun, endHour is exclusive
    private val busyBlocks = mutableListOf(
        // Maya: evenings Mon/Wed/Fri (gym), Tue/Thu mornings (part-time work)
        BusyBlock("bb-1", maya.id, dayOfWeek = 0, startHour = 19, endHour = 22, title = "Gym", type = BlockType.CLUB),
        BusyBlock("bb-2", maya.id, dayOfWeek = 2, startHour = 19, endHour = 22, title = "Gym", type = BlockType.CLUB),
        BusyBlock("bb-3", maya.id, dayOfWeek = 4, startHour = 19, endHour = 22, title = "Gym", type = BlockType.CLUB),
        BusyBlock("bb-4", maya.id, dayOfWeek = 1, startHour = 9,  endHour = 13, title = "Work", type = BlockType.WORK),
        BusyBlock("bb-5", maya.id, dayOfWeek = 3, startHour = 9,  endHour = 13, title = "Work", type = BlockType.WORK),

        // Jake: full-time work Mon–Fri all day
        BusyBlock("bb-6",  jake.id, dayOfWeek = 0, startHour = 8, endHour = 17, title = "Work", type = BlockType.WORK),
        BusyBlock("bb-7",  jake.id, dayOfWeek = 1, startHour = 8, endHour = 17, title = "Work", type = BlockType.WORK),
        BusyBlock("bb-8",  jake.id, dayOfWeek = 2, startHour = 8, endHour = 17, title = "Work", type = BlockType.WORK),
        BusyBlock("bb-9",  jake.id, dayOfWeek = 3, startHour = 8, endHour = 17, title = "Work", type = BlockType.WORK),
        BusyBlock("bb-10", jake.id, dayOfWeek = 4, startHour = 8, endHour = 17, title = "Work", type = BlockType.WORK),

        // Priya: evening classes Mon–Thu, Saturday club
        BusyBlock("bb-11", priya.id, dayOfWeek = 0, startHour = 18, endHour = 21, title = "Class", type = BlockType.CLASS),
        BusyBlock("bb-12", priya.id, dayOfWeek = 1, startHour = 18, endHour = 21, title = "Class", type = BlockType.CLASS),
        BusyBlock("bb-13", priya.id, dayOfWeek = 2, startHour = 18, endHour = 21, title = "Class", type = BlockType.CLASS),
        BusyBlock("bb-14", priya.id, dayOfWeek = 3, startHour = 18, endHour = 21, title = "Class", type = BlockType.CLASS),
        BusyBlock("bb-15", priya.id, dayOfWeek = 5, startHour = 10, endHour = 14, title = "Club", type = BlockType.CLUB),
    )

    override fun getHousehold(): Household = household

    override fun joinHousehold(code: String): Household? {
        return if (code.trim().uppercase() == household.inviteCode) household else null
    }

    override fun addRoommateToHousehold(householdId: String, roommate: Roommate) {
        if (roommates.none { it.id == roommate.id }) {
            roommates.add(roommate)
        }
    }

    override fun getRoommates(householdId: String): List<Roommate> = roommates.toList()

    override fun getBusyBlocks(roommateId: String): List<BusyBlock> =
        busyBlocks.filter { it.roommateId == roommateId }

    override fun addBusyBlock(block: BusyBlock) {
        busyBlocks.add(block)
    }

    override fun deleteBusyBlock(blockId: String) {
        busyBlocks.removeAll { it.id == blockId }
    }
}
