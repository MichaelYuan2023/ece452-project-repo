package com.example.houseflow.data.local

import com.example.houseflow.data.DemoAccounts
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
import com.example.houseflow.model.SplitType
import com.example.houseflow.model.TradeRequest
import com.example.houseflow.model.TradeStatus
import com.example.houseflow.util.ExpenseMath
import com.example.houseflow.util.PointsPolicy
import java.util.Calendar

// First-run demo seed. Runs when the Room database file is first created and,
// via HouseflowDatabase's onDestructiveMigration callback, after a destructive
// schema migration (both cases start from an empty database). Populates two
// households: the original "Demo House" (Maya/Jake/Priya) and the "Ana Baker"
// presentation household (HF user story) with roommates, schedules, chores, a
// populated bulletin board, chore assignments, two incoming trade requests, a
// starter leaderboard, and a couple of shared expenses.
object DatabaseSeeder {

    private const val DAY = 24L * 3600 * 1000
    private const val HOUR = 3600_000L

    suspend fun seed(db: HouseflowDatabase) {
        seedDemoHouse(db)
        seedAnaHousehold(db)
    }

    // ---------------------------------------------------------------------
    // Original demo household (household-1)
    // ---------------------------------------------------------------------
    private suspend fun seedDemoHouse(db: HouseflowDatabase) {
        val householdId = DemoAccounts.HOUSEHOLD_ID

        DemoAccounts.all.forEach { db.userDao().upsert(it) }

        db.householdDao().upsert(
            Household(id = householdId, name = "Demo House", inviteCode = "DEMO123")
        )

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

        demoBusyBlocks().forEach { db.busyBlockDao().insert(it) }
        demoChores(householdId).forEach { db.choreDao().insert(it) }
        demoBulletin(householdId).forEach { db.bulletinDao().insert(it) }
    }

    // Busy blocks: 0=Mon … 6=Sun, endHour is exclusive.
    private fun demoBusyBlocks(): List<BusyBlock> {
        val maya = DemoAccounts.MAYA.uid
        val jake = DemoAccounts.JAKE.uid
        val priya = DemoAccounts.PRIYA.uid
        return listOf(
            BusyBlock("bb-1", maya, 0, 19, 22, "Gym", BlockType.CLUB),
            BusyBlock("bb-2", maya, 2, 19, 22, "Gym", BlockType.CLUB),
            BusyBlock("bb-3", maya, 4, 19, 22, "Gym", BlockType.CLUB),
            BusyBlock("bb-4", maya, 1, 9, 13, "Work", BlockType.WORK),
            BusyBlock("bb-5", maya, 3, 9, 13, "Work", BlockType.WORK),
            BusyBlock("bb-6", jake, 0, 8, 17, "Work", BlockType.WORK),
            BusyBlock("bb-7", jake, 1, 8, 17, "Work", BlockType.WORK),
            BusyBlock("bb-8", jake, 2, 8, 17, "Work", BlockType.WORK),
            BusyBlock("bb-9", jake, 3, 8, 17, "Work", BlockType.WORK),
            BusyBlock("bb-10", jake, 4, 8, 17, "Work", BlockType.WORK),
            BusyBlock("bb-11", priya, 0, 18, 21, "Class", BlockType.CLASS),
            BusyBlock("bb-12", priya, 1, 18, 21, "Class", BlockType.CLASS),
            BusyBlock("bb-13", priya, 2, 18, 21, "Class", BlockType.CLASS),
            BusyBlock("bb-14", priya, 3, 18, 21, "Class", BlockType.CLASS),
            BusyBlock("bb-15", priya, 5, 10, 14, "Club", BlockType.CLUB),
        )
    }

    private fun demoChores(householdId: String): List<Chore> = listOf(
        Chore("chore-garbage", householdId, DemoAccounts.MAYA.uid, "Take out garbage",
            "Bring bins to the curb", ChoreFrequency.WEEKLY, 1, 1, 20, true),
        Chore("chore-bathroom", householdId, DemoAccounts.JAKE.uid, "Clean bathroom",
            "Scrub toilet, sink, and shower", ChoreFrequency.WEEKLY, 4, 6, 12, false),
        Chore("chore-vacuum", householdId, DemoAccounts.PRIYA.uid, "Vacuum living room",
            "Vacuum floors and under couch", ChoreFrequency.WEEKLY, 3, 5, 14, false),
        Chore("chore-kitchen", householdId, DemoAccounts.MAYA.uid, "Kitchen cleanup",
            "Wipe counters, do dishes, take out compost", ChoreFrequency.WEEKLY, 3, 3, 21, false),
        Chore("chore-groceries", householdId, DemoAccounts.JAKE.uid, "Buy shared supplies",
            "Toilet paper, dish soap, garbage bags", ChoreFrequency.WEEKLY, 2, 4, 18, false),
    )

    private fun demoBulletin(householdId: String): List<BulletinPost> {
        val now = System.currentTimeMillis()
        return listOf(
            BulletinPost("bp-1", householdId, "Maya", "Group grocery run Saturday",
                "Costco trip at 2pm — add items to the shared list if you need anything!", true, now - HOUR),
            BulletinPost("bp-2", householdId, "Jake", "Internet bill due Friday",
                "Everyone owes \$18.75 this month. E-transfer Jake.", false, now - DAY),
            BulletinPost("bp-3", householdId, "Priya", "House dinner Sunday night",
                "Making pasta — let me know dietary restrictions!", true, now - 2 * DAY),
            BulletinPost("bp-4", householdId, "Jake", "Quiet hours reminder",
                "Please keep it down after 11pm on weeknights. Some of us have 8am shifts.", false, now - 3 * DAY),
        )
    }

    // ---------------------------------------------------------------------
    // "Ana Baker" presentation household (household-ana)
    //
    // Ana: 3rd-year Computer Engineering student at UW, works part time, no
    // extracurriculars, shares an apartment with 4 roommates. Classes 9am-4pm
    // every weekday; works 9am-5pm Saturdays.
    // ---------------------------------------------------------------------
    private suspend fun seedAnaHousehold(db: HouseflowDatabase) {
        val hh = DemoAccounts.ANA_HOUSEHOLD_ID
        val ana = DemoAccounts.ANA.uid
        val ben = DemoAccounts.BEN.uid
        val chloe = DemoAccounts.CHLOE.uid
        val diego = DemoAccounts.DIEGO.uid
        val emma = DemoAccounts.EMMA.uid

        // Users. Ana resumes into this household on sign-in (activeHouseholdId).
        db.userDao().upsert(DemoAccounts.ANA.copy(activeHouseholdId = hh))
        db.userDao().upsert(DemoAccounts.BEN)
        db.userDao().upsert(DemoAccounts.CHLOE)
        db.userDao().upsert(DemoAccounts.DIEGO)
        db.userDao().upsert(DemoAccounts.EMMA)

        db.householdDao().upsert(Household(id = hh, name = "Maple Street Apt", inviteCode = "MAPLE1"))

        db.membershipDao().upsert(Roommate(ana, hh, "Ana Baker", HouseholdRole.CREATOR))
        db.membershipDao().upsert(Roommate(ben, hh, "Ben Carter", HouseholdRole.ADMIN))
        db.membershipDao().upsert(Roommate(chloe, hh, "Chloe Nguyen", HouseholdRole.MEMBER))
        db.membershipDao().upsert(Roommate(diego, hh, "Diego Silva", HouseholdRole.MEMBER))
        db.membershipDao().upsert(Roommate(emma, hh, "Emma Rossi", HouseholdRole.MEMBER))

        anaBusyBlocks().forEach { db.busyBlockDao().insert(it) }
        db.bulletinDao().let { dao -> anaBulletin(hh).forEach { dao.insert(it) } }

        // --- Chores, assignments, trades, points ---
        val now = System.currentTimeMillis()
        val weekStart = weekStartMillis()

        // Future due (day-of-week + hour) so the overdue sweep on load can't flip
        // these PENDING chores to MISSED and invalidate the trade requests.
        val (bathDay, bathHour) = future(now, weekStart, 3)
        val (garbDay, garbHour) = future(now, weekStart, 5)
        val (vacDay, vacHour) = future(now, weekStart, 7)
        val (kitDay, kitHour) = future(now, weekStart, 9)
        val (dishDay, dishHour) = future(now, weekStart, 4)

        val bathroom = Chore("ac-bathroom", hh, ben, "Clean bathroom",
            "Scrub toilet, sink, and shower", ChoreFrequency.WEEKLY, 4, bathDay, bathHour, false)
        val garbage = Chore("ac-garbage", hh, ana, "Take out garbage & recycling",
            "Bins to the curb before pickup", ChoreFrequency.WEEKLY, 1, garbDay, garbHour, true)
        val vacuum = Chore("ac-vacuum", hh, ana, "Vacuum common areas",
            "Living room + hallway", ChoreFrequency.WEEKLY, 3, vacDay, vacHour, false)
        val kitchen = Chore("ac-kitchen", hh, chloe, "Kitchen deep clean",
            "Counters, stove, and sink", ChoreFrequency.WEEKLY, 3, kitDay, kitHour, false)
        val dishes = Chore("ac-dishes", hh, ben, "Do the dishes", "Empty the drying rack too",
            ChoreFrequency.WEEKLY, 2, dishDay, dishHour, false)
        val groceries = Chore("ac-groceries", hh, ana, "Buy shared supplies",
            "Paper towels, dish soap, trash bags", ChoreFrequency.WEEKLY, 2, 3, 18, false)
        val recycling = Chore("ac-sweep", hh, ana, "Sweep the balcony",
            "Quick tidy of the balcony", ChoreFrequency.WEEKLY, 1, 0, 19, false)
        val mop = Chore("ac-mop", hh, diego, "Mop the floors",
            "Kitchen + bathroom tile", ChoreFrequency.WEEKLY, 3, 3, 20, false)

        listOf(bathroom, garbage, vacuum, kitchen, dishes, groceries, recycling, mop)
            .forEach { db.choreDao().insert(it) }

        fun asg(id: String, chore: Chore, who: String, status: AssignmentStatus, reason: String) =
            ChoreAssignment(id, chore.id, hh, who, weekStart, status, reason, false)

        // Ben & Chloe are trying to hand off their chores to Ana.
        val aBathroom = asg("aa-bathroom", bathroom, ben, AssignmentStatus.PENDING, "Claimed by Ben")
        val aGarbage = asg("aa-garbage", garbage, chloe, AssignmentStatus.PENDING, "Claimed by Chloe")
        val aVacuum = asg("aa-vacuum", vacuum, ana, AssignmentStatus.PENDING, "Your chore this week")
        val aKitchen = asg("aa-kitchen", kitchen, diego, AssignmentStatus.AVAILABLE,
            "Recommended for Diego — lightest workload this week")
        val aDishes = asg("aa-dishes", dishes, diego, AssignmentStatus.PENDING, "Claimed by Diego")
        val aGroceries = asg("aa-groceries", groceries, emma, AssignmentStatus.COMPLETED, "Completed by Emma")
        val aSweep = asg("aa-sweep", recycling, ana, AssignmentStatus.COMPLETED, "Completed by you")
        val aMop = asg("aa-mop", mop, ben, AssignmentStatus.COMPLETED, "Completed by Ben")

        listOf(aBathroom, aGarbage, aVacuum, aKitchen, aDishes, aGroceries, aSweep, aMop)
            .forEach { db.assignmentDao().insert(it) }

        // Two PENDING trade requests addressed to Ana (incoming), one from Ben and
        // one from Chloe, referencing their claimed chores above.
        db.tradeRequestDao().insert(
            TradeRequest("atr-1", aBathroom.id, hh, ben, ana,
                "I picked up a Saturday work shift — could you take the bathroom this week?",
                TradeStatus.PENDING, now - HOUR)
        )
        db.tradeRequestDao().insert(
            TradeRequest("atr-2", aGarbage.id, hh, chloe, ana,
                "Away visiting family this weekend, can you grab the garbage? 🙏",
                TradeStatus.PENDING, now - 2 * HOUR)
        )

        // Starter leaderboard: points for the completed chores this week.
        db.pointsDao().insert(PointsEntry(aGroceries.id, hh, emma, groceries.name, PointsPolicy.pointsFor(groceries), weekStart, now - 3 * HOUR))
        db.pointsDao().insert(PointsEntry(aSweep.id, hh, ana, recycling.name, PointsPolicy.pointsFor(recycling), weekStart, now - 4 * HOUR))
        db.pointsDao().insert(PointsEntry(aMop.id, hh, ben, mop.name, PointsPolicy.pointsFor(mop), weekStart, now - 5 * HOUR))

        // A couple of shared expenses so the Expenses tab is populated.
        seedExpense(db, "ae-1", hh, ana, "Costco grocery run", 6000, listOf(ana, ben, chloe, diego, emma), now - DAY)
        seedExpense(db, "ae-2", hh, ben, "Internet bill (August)", 7500, listOf(ana, ben, chloe, diego, emma), now - 2 * DAY)
    }

    private suspend fun seedExpense(
        db: HouseflowDatabase,
        id: String,
        householdId: String,
        paidBy: String,
        description: String,
        amountCents: Int,
        participants: List<String>,
        createdAt: Long
    ) {
        db.expenseDao().insert(
            Expense(id, householdId, paidBy, paidBy, description, amountCents, SplitType.EQUAL, createdAt)
        )
        val shares = ExpenseMath.equalSplit(amountCents, participants).map { (uid, cents) ->
            ExpenseShare("$id:$uid", id, householdId, uid, cents)
        }
        db.expenseShareDao().insertAll(shares)
    }

    private fun anaBusyBlocks(): List<BusyBlock> {
        val ana = DemoAccounts.ANA.uid
        val ben = DemoAccounts.BEN.uid
        val chloe = DemoAccounts.CHLOE.uid
        val diego = DemoAccounts.DIEGO.uid
        val emma = DemoAccounts.EMMA.uid
        val blocks = mutableListOf<BusyBlock>()

        // Ana: classes Mon–Fri 9am–4pm, part-time work Saturday 9am–5pm.
        for (d in 0..4) {
            blocks += BusyBlock("abb-ana-c$d", ana, d, 9, 16, "Classes", BlockType.CLASS)
        }
        blocks += BusyBlock("abb-ana-work", ana, 5, 9, 17, "Work (part-time)", BlockType.WORK)

        // Ben: evening restaurant shifts + Sunday soccer.
        blocks += BusyBlock("abb-ben-1", ben, 0, 17, 22, "Restaurant shift", BlockType.WORK)
        blocks += BusyBlock("abb-ben-2", ben, 2, 17, 22, "Restaurant shift", BlockType.WORK)
        blocks += BusyBlock("abb-ben-3", ben, 4, 17, 22, "Restaurant shift", BlockType.WORK)
        blocks += BusyBlock("abb-ben-4", ben, 6, 14, 16, "Soccer", BlockType.CLUB)

        // Chloe: Tue/Thu lectures + Saturday art club.
        blocks += BusyBlock("abb-chloe-1", chloe, 1, 10, 14, "Lectures", BlockType.CLASS)
        blocks += BusyBlock("abb-chloe-2", chloe, 3, 10, 14, "Lectures", BlockType.CLASS)
        blocks += BusyBlock("abb-chloe-3", chloe, 5, 15, 18, "Art club", BlockType.CLUB)

        // Diego: morning shifts Mon–Fri.
        for (d in 0..4) {
            blocks += BusyBlock("abb-diego-$d", diego, d, 8, 12, "Morning shift", BlockType.WORK)
        }

        // Emma: Mon/Wed labs + Sunday study group.
        blocks += BusyBlock("abb-emma-1", emma, 0, 13, 17, "Labs", BlockType.CLASS)
        blocks += BusyBlock("abb-emma-2", emma, 2, 13, 17, "Labs", BlockType.CLASS)
        blocks += BusyBlock("abb-emma-3", emma, 6, 10, 12, "Study group", BlockType.OTHER)

        return blocks
    }

    private fun anaBulletin(householdId: String): List<BulletinPost> {
        val now = System.currentTimeMillis()
        return listOf(
            BulletinPost("abp-1", householdId, "Ana Baker", "Rent due August 1st",
                "Reminder: rent + utilities are due on the 1st. Send your share to me and I'll pay the landlord.",
                false, now - HOUR),
            BulletinPost("abp-2", householdId, "Ben Carter", "Movie night this Friday",
                "Projector's set up in the living room — 8pm Friday. Bringing popcorn!", true, now - 5 * HOUR),
            BulletinPost("abp-3", householdId, "Chloe Nguyen", "New wifi password",
                "Router got reset. New password is on the fridge whiteboard.", false, now - DAY),
            BulletinPost("abp-4", householdId, "Diego Silva", "Please label your food",
                "Running out of fridge space — label anything you want kept, unlabeled leftovers get tossed Sunday.",
                false, now - 2 * DAY),
            BulletinPost("abp-5", householdId, "Emma Rossi", "Ana's birthday Saturday!",
                "Surprise cake at 7pm Saturday after her work shift — keep it quiet 🎂", true, now - 3 * DAY),
        )
    }

    // This week's Monday at local midnight (matches AppViewModel.currentWeekStart).
    private fun weekStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // A (dayOfWeek 0-6, hour) a few hours ahead of [now], guaranteed to resolve to
    // a WEEKLY due time later than now within the current week (falls back to
    // Sunday 23:00 if it would otherwise wrap into next week).
    private fun future(now: Long, weekStart: Long, hoursAhead: Int): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.add(Calendar.HOUR_OF_DAY, hoursAhead)
        var dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        var hour = cal.get(Calendar.HOUR_OF_DAY)
        if (weekStart + dow * DAY + hour * HOUR <= now) {
            dow = 6; hour = 23
        }
        return dow to hour
    }
}
