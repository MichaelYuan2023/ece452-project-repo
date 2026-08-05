package com.example.houseflow.data.local

import com.example.houseflow.data.DemoHousehold
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.BlockType
import com.example.houseflow.model.BulletinPost
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.ChoreFrequency
import com.example.houseflow.model.Household
import com.example.houseflow.model.HouseholdRole
import com.example.houseflow.model.Roommate
import com.example.houseflow.model.TradeRequest
import com.example.houseflow.model.TradeStatus
import com.example.houseflow.model.User
import com.example.houseflow.util.WeekStart

// Seeds "Maple Street House" — the household used for live demos.
//
// Everything here is anchored to the week the seed actually runs, so the board
// looks plausible whenever the app is installed: chores whose due day has
// already passed come out COMPLETED (or MISSED, for the one we want to show in
// that state), and chores still ahead come out PENDING or AVAILABLE. The
// previous week is seeded too, purely as history — the recommendation engine
// scores on the last 14 days, and the interaction report has nothing to show
// without it.
object DemoHouseholdSeeder {

    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 24 * HOUR_MS
    private const val WEEK_MS = 7 * DAY_MS

    private val OWNER = DemoHousehold.OWNER_PLACEHOLDER_UID
    private val SOFIA = DemoHousehold.SOFIA.uid
    private val DEVON = DemoHousehold.DEVON.uid
    private val AMARA = DemoHousehold.AMARA.uid

    // Everything the seed writes, assembled before any of it touches the
    // database so it can be asserted over in tests.
    internal data class SeedData(
        val household: Household,
        val users: List<User>,
        val memberships: List<Roommate>,
        val busyBlocks: List<BusyBlock>,
        val chores: List<Chore>,
        val thisWeek: List<ChoreAssignment>,
        val lastWeek: List<ChoreAssignment>,
        val trades: List<TradeRequest>,
        val posts: List<BulletinPost>
    ) {
        val assignments: List<ChoreAssignment> get() = thisWeek + lastWeek
    }

    internal fun build(weekStart: Long, today: Int): SeedData {
        val householdId = DemoHousehold.HOUSEHOLD_ID
        return SeedData(
            household = Household(
                id = householdId,
                name = DemoHousehold.HOUSEHOLD_NAME,
                inviteCode = DemoHousehold.INVITE_CODE
            ),
            users = DemoHousehold.all,
            // Ron owns the house. Sofia is an admin so the role-management UI
            // has a demotion target; Devon and Amara are plain members so
            // promotion is demoable too.
            memberships = memberships(householdId),
            busyBlocks = busyBlocks(),
            chores = chores(householdId),
            thisWeek = thisWeekAssignments(householdId, weekStart, today) +
                dailyDishAssignments(householdId, weekStart, today),
            lastWeek = lastWeekAssignments(householdId, weekStart - WEEK_MS),
            trades = tradeRequests(householdId),
            posts = bulletinPosts(householdId)
        )
    }

    // Writes the demo household unless it is already there.
    //
    // Deliberately NOT hung off RoomDatabase.Callback.onCreate: that callback
    // fires only when the database *file* is created, and Room does not call it
    // after a destructive migration — it drops and recreates the tables and
    // moves on. Any device that has ever had the app installed therefore never
    // gets an onCreate-based seed again, which is exactly how both this seed and
    // the older DatabaseSeeder ended up silently absent on the demo emulator.
    // Running it at startup behind an existence check works regardless of the
    // database file's history, and leaves user-created households alone.
    suspend fun seedIfMissing(db: HouseflowDatabase) {
        if (db.householdDao().getById(DemoHousehold.HOUSEHOLD_ID) != null) return
        seed(db)
    }

    suspend fun seed(db: HouseflowDatabase) {
        val data = build(WeekStart.current(), WeekStart.todayIndex())

        data.users.forEach { db.userDao().upsert(it) }
        db.householdDao().upsert(data.household)
        data.memberships.forEach { db.membershipDao().upsert(it) }
        data.busyBlocks.forEach { db.busyBlockDao().insert(it) }
        data.chores.forEach { db.choreDao().insert(it) }
        data.assignments.forEach { db.assignmentDao().insert(it) }
        data.trades.forEach { db.tradeRequestDao().insert(it) }
        data.posts.forEach { db.bulletinDao().insert(it) }
    }

    private fun memberships(householdId: String): List<Roommate> = listOf(
        Roommate(OWNER, householdId, DemoHousehold.OWNER_DISPLAY_NAME, HouseholdRole.CREATOR),
        Roommate(SOFIA, householdId, DemoHousehold.SOFIA.displayName, HouseholdRole.ADMIN),
        Roommate(DEVON, householdId, DemoHousehold.DEVON.displayName, HouseholdRole.MEMBER),
        Roommate(AMARA, householdId, DemoHousehold.AMARA.displayName, HouseholdRole.MEMBER),
    )

    // -----------------------------------------------------------------------
    // Schedules. dayOfWeek: 0=Mon … 6=Sun; endHour exclusive.
    //
    // The four shapes are deliberately different so the recommendation engine's
    // availability penalty produces visibly different answers per chore:
    //   Ron    — light: a few lectures, TA hours, one gym block
    //   Sofia  — student: mornings in lecture, afternoons in lab, sport most evenings
    //   Devon  — 9-5 office all week, so only evenings and weekends are free
    //   Amara  — grad student: teaching mornings, long lab afternoons, Sunday choir
    // -----------------------------------------------------------------------
    private fun busyBlocks(): List<BusyBlock> = listOf(
        // Ron — 4th-year student who also TAs one course.
        BusyBlock("mbb-1", OWNER, 0, 10, 13, "ECE 452 Lecture", BlockType.CLASS),
        BusyBlock("mbb-2", OWNER, 2, 10, 13, "ECE 452 Lecture", BlockType.CLASS),
        BusyBlock("mbb-3", OWNER, 4, 10, 12, "ECE 452 Tutorial", BlockType.CLASS),
        BusyBlock("mbb-4", OWNER, 1, 14, 17, "TA office hours", BlockType.WORK),
        BusyBlock("mbb-5", OWNER, 3, 18, 20, "Gym", BlockType.CLUB),

        // Sofia — full course load plus varsity volleyball.
        BusyBlock("mbb-6", SOFIA, 0, 8, 11, "Organic Chem lecture", BlockType.CLASS),
        BusyBlock("mbb-7", SOFIA, 2, 8, 11, "Organic Chem lecture", BlockType.CLASS),
        BusyBlock("mbb-8", SOFIA, 4, 8, 11, "Organic Chem lecture", BlockType.CLASS),
        BusyBlock("mbb-9", SOFIA, 1, 13, 17, "Biology lab", BlockType.CLASS),
        BusyBlock("mbb-10", SOFIA, 3, 13, 17, "Biology lab", BlockType.CLASS),
        BusyBlock("mbb-11", SOFIA, 0, 18, 21, "Volleyball practice", BlockType.CLUB),
        BusyBlock("mbb-12", SOFIA, 2, 18, 21, "Volleyball practice", BlockType.CLUB),
        BusyBlock("mbb-13", SOFIA, 5, 9, 13, "Volleyball tournament", BlockType.CLUB),

        // Devon — graduated, works downtown, two evening commitments.
        BusyBlock("mbb-14", DEVON, 0, 9, 17, "Office (downtown)", BlockType.WORK),
        BusyBlock("mbb-15", DEVON, 1, 9, 17, "Office (downtown)", BlockType.WORK),
        BusyBlock("mbb-16", DEVON, 2, 9, 17, "Office (downtown)", BlockType.WORK),
        BusyBlock("mbb-17", DEVON, 3, 9, 17, "Office (downtown)", BlockType.WORK),
        BusyBlock("mbb-18", DEVON, 4, 9, 17, "Office (downtown)", BlockType.WORK),
        BusyBlock("mbb-19", DEVON, 1, 19, 22, "Rec hockey league", BlockType.CLUB),
        BusyBlock("mbb-20", DEVON, 3, 19, 21, "Guitar lessons", BlockType.CLUB),

        // Amara — MASc student, TAs a lab, sings in a community choir.
        BusyBlock("mbb-21", AMARA, 1, 9, 12, "TA lab section", BlockType.WORK),
        BusyBlock("mbb-22", AMARA, 3, 9, 12, "TA lab section", BlockType.WORK),
        BusyBlock("mbb-23", AMARA, 0, 13, 18, "Research lab", BlockType.CLASS),
        BusyBlock("mbb-24", AMARA, 2, 13, 18, "Research lab", BlockType.CLASS),
        BusyBlock("mbb-25", AMARA, 4, 13, 18, "Research lab", BlockType.CLASS),
        BusyBlock("mbb-26", AMARA, 6, 15, 18, "Choir practice", BlockType.CLUB),
    )

    // -----------------------------------------------------------------------
    // Chores — an ordinary shared-house roster, spread across the week and
    // across the effort scale so the workload penalty has something to balance.
    // -----------------------------------------------------------------------
    private const val GARBAGE = "maple-garbage"
    private const val BATHROOM = "maple-bathroom"
    private const val VACUUM = "maple-vacuum"
    private const val KITCHEN = "maple-kitchen"
    private const val SUPPLIES = "maple-supplies"
    private const val FRIDGE = "maple-fridge"
    private const val LAUNDRY = "maple-laundry"
    private const val PLANTS = "maple-plants"
    private const val MOP = "maple-mop"
    private const val DISHES = "maple-dishes"

    private fun chores(householdId: String): List<Chore> = listOf(
        Chore(
            id = GARBAGE, householdId = householdId, createdByRoommateId = OWNER,
            name = "Take out garbage & recycling",
            description = "Bins to the curb before pickup Tuesday morning",
            frequency = ChoreFrequency.WEEKLY, effortScore = 1,
            dueDayOfWeek = 0, dueHour = 20, isTimeSensitive = true
        ),
        Chore(
            id = BATHROOM, householdId = householdId, createdByRoommateId = OWNER,
            name = "Clean the bathroom",
            description = "Toilet, sink, shower, mirror, and swap the towels",
            frequency = ChoreFrequency.WEEKLY, effortScore = 4,
            dueDayOfWeek = 5, dueHour = 11, isTimeSensitive = false
        ),
        Chore(
            id = VACUUM, householdId = householdId, createdByRoommateId = SOFIA,
            name = "Vacuum living room & hallway",
            description = "Move the couch, get the stairs too",
            frequency = ChoreFrequency.WEEKLY, effortScore = 3,
            dueDayOfWeek = 6, dueHour = 13, isTimeSensitive = false
        ),
        Chore(
            id = KITCHEN, householdId = householdId, createdByRoommateId = AMARA,
            name = "Kitchen deep clean",
            description = "Counters, stovetop, microwave, and take out the compost",
            frequency = ChoreFrequency.WEEKLY, effortScore = 4,
            dueDayOfWeek = 4, dueHour = 18, isTimeSensitive = false
        ),
        Chore(
            id = SUPPLIES, householdId = householdId, createdByRoommateId = DEVON,
            name = "Restock shared supplies",
            description = "Toilet paper, paper towel, dish soap, garbage bags",
            frequency = ChoreFrequency.WEEKLY, effortScore = 2,
            dueDayOfWeek = 3, dueHour = 18, isTimeSensitive = false
        ),
        Chore(
            id = FRIDGE, householdId = householdId, createdByRoommateId = AMARA,
            name = "Clean out the fridge",
            description = "Toss anything expired, wipe the shelves",
            frequency = ChoreFrequency.WEEKLY, effortScore = 2,
            dueDayOfWeek = 2, dueHour = 19, isTimeSensitive = false
        ),
        Chore(
            id = LAUNDRY, householdId = householdId, createdByRoommateId = SOFIA,
            name = "Wash shared linens",
            description = "Kitchen towels, bath mats, and the entryway rug",
            frequency = ChoreFrequency.WEEKLY, effortScore = 2,
            dueDayOfWeek = 6, dueHour = 15, isTimeSensitive = false
        ),
        Chore(
            id = PLANTS, householdId = householdId, createdByRoommateId = OWNER,
            name = "Water the plants",
            description = "Living room, kitchen windowsill, and the balcony pots",
            frequency = ChoreFrequency.WEEKLY, effortScore = 1,
            dueDayOfWeek = 6, dueHour = 10, isTimeSensitive = false
        ),
        Chore(
            id = MOP, householdId = householdId, createdByRoommateId = DEVON,
            name = "Mop kitchen & entryway floors",
            description = "Sweep first, then mop — let it dry before anyone walks through",
            frequency = ChoreFrequency.EVERY_N_DAYS, effortScore = 3,
            dueDayOfWeek = 1, dueHour = 18, isTimeSensitive = false, intervalDays = 3
        ),
        Chore(
            id = DISHES, householdId = householdId, createdByRoommateId = OWNER,
            name = "Wash & put away dishes",
            description = "Empty the drying rack too — nobody else can use it otherwise",
            frequency = ChoreFrequency.DAILY, effortScore = 1,
            dueDayOfWeek = 0, dueHour = 20, isTimeSensitive = false
        ),
    )

    // -----------------------------------------------------------------------
    // This week's board.
    //
    // Intent decides how a row resolves once the calendar is taken into account;
    // see [resolve]. Two chores are pinned AVAILABLE so the "claim a chore"
    // flow is always demoable, and one is pinned PENDING because a trade
    // request hangs off it (a trade against a non-PENDING assignment is treated
    // as stale and silently dropped).
    // -----------------------------------------------------------------------
    private enum class Intent { DONE_IF_PAST, MISSED_IF_PAST, ALWAYS_AVAILABLE, ALWAYS_PENDING }

    private data class Seeded(
        val id: String,
        val choreId: String,
        val assignee: String,
        val dueDay: Int,
        val intent: Intent,
        val reason: String,
        val hasConflict: Boolean = false
    )

    private fun thisWeekAssignments(householdId: String, weekStart: Long, today: Int): List<ChoreAssignment> =
        listOf(
            Seeded(
                "ma-1", GARBAGE, DEVON, 0, Intent.DONE_IF_PAST,
                "Recommended for Devon — free Mon 20:00, no other chores this week"
            ),
            Seeded(
                "ma-2", FRIDGE, AMARA, 2, Intent.DONE_IF_PAST,
                "Recommended for Amara — free Wed 19:00, lightest workload (1 chore(s) this week)"
            ),
            Seeded(
                "ma-3", SUPPLIES, SOFIA, 3, Intent.MISSED_IF_PAST,
                "Recommended for Sofia — free Thu 18:00, lightest workload (1 chore(s) this week)"
            ),
            Seeded(
                "ma-4", KITCHEN, OWNER, 4, Intent.DONE_IF_PAST,
                "Recommended for Ron — free Fri 18:00, lightest workload (1 chore(s) this week)"
            ),
            Seeded(
                "ma-5", MOP, DEVON, 1, Intent.DONE_IF_PAST,
                "Recommended for Devon — free Tue 18:00, lightest workload (1 chore(s) this week)"
            ),
            // Sofia's volleyball tournament runs Sat 09:00-13:00, so this one is
            // assigned despite a conflict — it drives the conflict badge and the
            // pending trade request below.
            Seeded(
                "ma-6", BATHROOM, SOFIA, 5, Intent.ALWAYS_PENDING,
                "Recommended for Sofia — conflict: busy Sat 11:00, but fewest tasks this week",
                hasConflict = true
            ),
            Seeded(
                "ma-7", PLANTS, AMARA, 6, Intent.ALWAYS_AVAILABLE,
                "Recommended for Amara — free Sun 10:00, no other chores this week"
            ),
            Seeded(
                "ma-8", VACUUM, OWNER, 6, Intent.ALWAYS_AVAILABLE,
                "Recommended for Ron — free Sun 13:00, lightest workload (1 chore(s) this week)"
            ),
            Seeded(
                "ma-9", LAUNDRY, DEVON, 6, Intent.DONE_IF_PAST,
                "Recommended for Devon — free Sun 15:00, lightest workload (2 chore(s) this week)"
            ),
        ).map { it.toAssignment(householdId, weekStart, today) }

    private fun Seeded.toAssignment(householdId: String, weekStart: Long, today: Int) =
        ChoreAssignment(
            id = id,
            choreId = choreId,
            householdId = householdId,
            assignedToRoommateId = assignee,
            weekStart = weekStart,
            status = resolve(intent, dueDay, today),
            reason = reason,
            hasConflict = hasConflict
        )

    private fun resolve(intent: Intent, dueDay: Int, today: Int): AssignmentStatus = when (intent) {
        Intent.ALWAYS_AVAILABLE -> AssignmentStatus.AVAILABLE
        Intent.ALWAYS_PENDING -> AssignmentStatus.PENDING
        Intent.MISSED_IF_PAST -> if (dueDay < today) AssignmentStatus.MISSED else AssignmentStatus.PENDING
        Intent.DONE_IF_PAST -> if (dueDay < today) AssignmentStatus.COMPLETED else AssignmentStatus.PENDING
    }

    // The daily dish rotation. Only yesterday and today are seeded — pressing
    // "Run assignments" then fills in the rest of the week, which is a tidy way
    // to show the recommendation engine actually running during a demo.
    private fun dailyDishAssignments(householdId: String, weekStart: Long, today: Int): List<ChoreAssignment> {
        val rotation = listOf(OWNER, SOFIA, DEVON, AMARA)
        val names = mapOf(
            OWNER to "Ron", SOFIA to "Sofia", DEVON to "Devon", AMARA to "Amara"
        )
        return listOf(today - 1, today)
            .filter { it >= 0 }
            .map { day ->
                val assignee = rotation[day % rotation.size]
                ChoreAssignment(
                    id = "ma-dishes-$day",
                    choreId = DISHES,
                    householdId = householdId,
                    assignedToRoommateId = assignee,
                    weekStart = weekStart + day * DAY_MS,
                    status = if (day < today) AssignmentStatus.COMPLETED else AssignmentStatus.PENDING,
                    reason = "Recommended for ${names[assignee]} — free 20:00, lightest workload today",
                    hasConflict = false
                )
            }
    }

    // Last week, entirely settled. This is what gives the recommendation engine
    // its recency and fairness signal (it scores over the past 14 days) and what
    // fills the interaction report with completed/missed counts.
    private fun lastWeekAssignments(householdId: String, weekStart: Long): List<ChoreAssignment> {
        fun done(id: String, choreId: String, assignee: String, who: String) =
            ChoreAssignment(
                id = id, choreId = choreId, householdId = householdId,
                assignedToRoommateId = assignee, weekStart = weekStart,
                status = AssignmentStatus.COMPLETED,
                reason = "Recommended for $who — completed last week",
                hasConflict = false
            )
        return listOf(
            done("ma-lw-1", GARBAGE, SOFIA, "Sofia"),
            done("ma-lw-2", BATHROOM, DEVON, "Devon"),
            done("ma-lw-3", VACUUM, AMARA, "Amara"),
            done("ma-lw-4", KITCHEN, SOFIA, "Sofia"),
            done("ma-lw-5", FRIDGE, OWNER, "Ron"),
            done("ma-lw-6", PLANTS, DEVON, "Devon"),
            done("ma-lw-7", MOP, AMARA, "Amara"),
            // One miss last week, so the report isn't uniformly green.
            ChoreAssignment(
                id = "ma-lw-8", choreId = SUPPLIES, householdId = householdId,
                assignedToRoommateId = DEVON, weekStart = weekStart,
                status = AssignmentStatus.MISSED,
                reason = "Recommended for Devon — conflict: busy Thu 18:00",
                hasConflict = true
            ),
        )
    }

    // One live request Ron can accept or deny on camera, plus two resolved ones
    // so the interaction report shows sent/received/accepted/denied traffic.
    private fun tradeRequests(householdId: String): List<TradeRequest> {
        val now = System.currentTimeMillis()
        return listOf(
            TradeRequest(
                id = "mt-1", assignmentId = "ma-6", householdId = householdId,
                fromUserId = SOFIA, toUserId = OWNER,
                reason = "Volleyball tournament runs all Saturday — any chance you can take the bathroom this week?",
                status = TradeStatus.PENDING, createdAt = now - 5 * HOUR_MS
            ),
            TradeRequest(
                id = "mt-2", assignmentId = "ma-lw-2", householdId = householdId,
                fromUserId = DEVON, toUserId = AMARA,
                reason = "Stuck late at the office, can you cover the bathroom?",
                status = TradeStatus.ACCEPTED, createdAt = now - 5 * DAY_MS
            ),
            TradeRequest(
                id = "mt-3", assignmentId = "ma-lw-7", householdId = householdId,
                fromUserId = AMARA, toUserId = SOFIA,
                reason = "Thesis deadline this week — could you mop instead?",
                status = TradeStatus.DENIED, createdAt = now - 6 * DAY_MS
            ),
        )
    }

    private fun bulletinPosts(householdId: String): List<BulletinPost> {
        val now = System.currentTimeMillis()
        return listOf(
            BulletinPost(
                id = "mbp-1", householdId = householdId, authorName = "Ron",
                title = "House meeting Sunday 7pm",
                message = "Quick one — chore rotation, the hydro bill, and whether we're renewing the lease.",
                isEvent = true, timestamp = now - 2 * HOUR_MS
            ),
            BulletinPost(
                id = "mbp-2", householdId = householdId, authorName = "Devon",
                title = "Internet + hydro due Friday",
                message = "Works out to \$41.25 each this month. E-transfer me whenever.",
                isEvent = false, timestamp = now - DAY_MS
            ),
            BulletinPost(
                id = "mbp-3", householdId = householdId, authorName = "Sofia",
                title = "Costco run Saturday afternoon",
                message = "Heading out around 3 after my tournament. Add to the list on the fridge if you need anything.",
                isEvent = true, timestamp = now - 2 * DAY_MS
            ),
            BulletinPost(
                id = "mbp-4", householdId = householdId, authorName = "Amara",
                title = "Quiet hours during exams",
                message = "I have a defense in two weeks — trying to keep it down after 11 on weeknights. Thanks all!",
                isEvent = false, timestamp = now - 4 * DAY_MS
            ),
        )
    }
}
