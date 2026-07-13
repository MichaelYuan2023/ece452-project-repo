package com.example.houseflow.data.local

import com.example.houseflow.data.DemoAccounts
import com.example.houseflow.model.BlockType
import com.example.houseflow.model.BulletinPost
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreFrequency
import com.example.houseflow.model.Household
import com.example.houseflow.model.HouseholdRole
import com.example.houseflow.model.Roommate

// First-run demo seed. Runs once, when the Room database file is first created
// (RoomDatabase.Callback.onCreate). Populates the demo household, its three
// roommates (real Firebase accounts — see DemoAccounts), their schedules,
// household chores, and a few bulletin posts so the app feels lived-in.
object DatabaseSeeder {

    suspend fun seed(db: HouseflowDatabase) {
        val householdId = DemoAccounts.HOUSEHOLD_ID

        DemoAccounts.all.forEach { db.userDao().upsert(it) }

        db.householdDao().upsert(
            Household(id = householdId, name = "Demo House", inviteCode = "DEMO123")
        )

        // All three role tiers are represented out of the box: Maya created the
        // demo household, Jake is an admin, Priya is a plain member.
        DemoAccounts.all.forEach { user ->
            val role = when (user.uid) {
                DemoAccounts.MAYA.uid -> HouseholdRole.CREATOR
                DemoAccounts.JAKE.uid -> HouseholdRole.ADMIN
                else -> HouseholdRole.MEMBER
            }
            db.membershipDao().upsert(
                Roommate(userId = user.uid, householdId = householdId, displayName = user.displayName, role = role)
            )
        }

        busyBlocks().forEach { db.busyBlockDao().insert(it) }
        chores(householdId).forEach { db.choreDao().insert(it) }
        bulletinPosts(householdId).forEach { db.bulletinDao().insert(it) }
    }

    // Busy blocks: 0=Mon … 6=Sun, endHour is exclusive.
    private fun busyBlocks(): List<BusyBlock> {
        val maya = DemoAccounts.MAYA.uid
        val jake = DemoAccounts.JAKE.uid
        val priya = DemoAccounts.PRIYA.uid
        return listOf(
            // Maya: evenings Mon/Wed/Fri (gym), Tue/Thu mornings (part-time work)
            BusyBlock("bb-1", maya, 0, 19, 22, "Gym", BlockType.CLUB),
            BusyBlock("bb-2", maya, 2, 19, 22, "Gym", BlockType.CLUB),
            BusyBlock("bb-3", maya, 4, 19, 22, "Gym", BlockType.CLUB),
            BusyBlock("bb-4", maya, 1, 9, 13, "Work", BlockType.WORK),
            BusyBlock("bb-5", maya, 3, 9, 13, "Work", BlockType.WORK),

            // Jake: full-time work Mon–Fri all day
            BusyBlock("bb-6", jake, 0, 8, 17, "Work", BlockType.WORK),
            BusyBlock("bb-7", jake, 1, 8, 17, "Work", BlockType.WORK),
            BusyBlock("bb-8", jake, 2, 8, 17, "Work", BlockType.WORK),
            BusyBlock("bb-9", jake, 3, 8, 17, "Work", BlockType.WORK),
            BusyBlock("bb-10", jake, 4, 8, 17, "Work", BlockType.WORK),

            // Priya: evening classes Mon–Thu, Saturday club
            BusyBlock("bb-11", priya, 0, 18, 21, "Class", BlockType.CLASS),
            BusyBlock("bb-12", priya, 1, 18, 21, "Class", BlockType.CLASS),
            BusyBlock("bb-13", priya, 2, 18, 21, "Class", BlockType.CLASS),
            BusyBlock("bb-14", priya, 3, 18, 21, "Class", BlockType.CLASS),
            BusyBlock("bb-15", priya, 5, 10, 14, "Club", BlockType.CLUB),
        )
    }

    private fun chores(householdId: String): List<Chore> = listOf(
        Chore(
            id = "chore-garbage", householdId = householdId,
            createdByRoommateId = DemoAccounts.MAYA.uid, name = "Take out garbage",
            description = "Bring bins to the curb", frequency = ChoreFrequency.WEEKLY,
            effortScore = 1, dueDayOfWeek = 1, dueHour = 20, isTimeSensitive = true
        ),
        Chore(
            id = "chore-bathroom", householdId = householdId,
            createdByRoommateId = DemoAccounts.JAKE.uid, name = "Clean bathroom",
            description = "Scrub toilet, sink, and shower", frequency = ChoreFrequency.WEEKLY,
            effortScore = 4, dueDayOfWeek = 6, dueHour = 12, isTimeSensitive = false
        ),
        Chore(
            id = "chore-vacuum", householdId = householdId,
            createdByRoommateId = DemoAccounts.PRIYA.uid, name = "Vacuum living room",
            description = "Vacuum floors and under couch", frequency = ChoreFrequency.WEEKLY,
            effortScore = 3, dueDayOfWeek = 5, dueHour = 14, isTimeSensitive = false
        ),
        Chore(
            id = "chore-kitchen", householdId = householdId,
            createdByRoommateId = DemoAccounts.MAYA.uid, name = "Kitchen cleanup",
            description = "Wipe counters, do dishes, take out compost", frequency = ChoreFrequency.WEEKLY,
            effortScore = 3, dueDayOfWeek = 3, dueHour = 21, isTimeSensitive = false
        ),
        Chore(
            id = "chore-groceries", householdId = householdId,
            createdByRoommateId = DemoAccounts.JAKE.uid, name = "Buy shared supplies",
            description = "Toilet paper, dish soap, garbage bags", frequency = ChoreFrequency.WEEKLY,
            effortScore = 2, dueDayOfWeek = 4, dueHour = 18, isTimeSensitive = false
        )
    )

    private fun bulletinPosts(householdId: String): List<BulletinPost> {
        val now = System.currentTimeMillis()
        return listOf(
            BulletinPost(
                id = "bp-1", householdId = householdId,
                authorName = "Maya", title = "Group grocery run Saturday",
                message = "Costco trip at 2pm — add items to the shared list if you need anything!",
                isEvent = true, timestamp = now - 3600_000
            ),
            BulletinPost(
                id = "bp-2", householdId = householdId,
                authorName = "Jake", title = "Internet bill due Friday",
                message = "Everyone owes \$18.75 this month. E-transfer Jake.",
                isEvent = false, timestamp = now - 86400_000
            ),
            BulletinPost(
                id = "bp-3", householdId = householdId,
                authorName = "Priya", title = "House dinner Sunday night",
                message = "Making pasta — let me know dietary restrictions!",
                isEvent = true, timestamp = now - 172800_000
            ),
            BulletinPost(
                id = "bp-4", householdId = householdId,
                authorName = "Jake", title = "Quiet hours reminder",
                message = "Please keep it down after 11pm on weeknights. Some of us have 8am shifts.",
                isEvent = false, timestamp = now - 259200_000
            ),
        )
    }
}
