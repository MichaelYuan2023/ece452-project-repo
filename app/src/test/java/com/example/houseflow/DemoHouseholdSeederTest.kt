package com.example.houseflow

import com.example.houseflow.data.DemoHousehold
import com.example.houseflow.data.local.DemoHouseholdSeeder
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.HouseholdRole
import com.example.houseflow.model.TradeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The demo seed is only exercised at database-create time on a real device, so
// a dangling chore id or a misspelled uid would show up as a blank card
// mid-demo rather than as a crash. These assertions keep it honest, and they
// run for every day of the week because assignment status is resolved against
// the day the seed happens to run.
class DemoHouseholdSeederTest {

    private val weekStart = 1_767_225_600_000L // an arbitrary Monday 00:00
    private val everyDay = 0..6

    private fun seedFor(today: Int) = DemoHouseholdSeeder.build(weekStart, today)

    @Test
    fun `every assignment points at a seeded chore`() {
        everyDay.forEach { today ->
            val data = seedFor(today)
            val choreIds = data.chores.map { it.id }.toSet()
            data.assignments.forEach {
                assertTrue(
                    "day $today: assignment ${it.id} references unknown chore ${it.choreId}",
                    it.choreId in choreIds
                )
            }
        }
    }

    @Test
    fun `every assignment is given to a member of the household`() {
        val memberIds = seedFor(0).memberships.map { it.userId }.toSet()
        everyDay.forEach { today ->
            seedFor(today).assignments.forEach {
                assertTrue(
                    "day $today: assignment ${it.id} assigned to non-member ${it.assignedToRoommateId}",
                    it.assignedToRoommateId in memberIds
                )
            }
        }
    }

    @Test
    fun `ids are unique across each table`() {
        everyDay.forEach { today ->
            val data = seedFor(today)
            fun assertUnique(label: String, ids: List<String>) =
                assertEquals("day $today: duplicate $label id", ids.size, ids.toSet().size)

            assertUnique("chore", data.chores.map { it.id })
            assertUnique("assignment", data.assignments.map { it.id })
            assertUnique("busy block", data.busyBlocks.map { it.id })
            assertUnique("trade", data.trades.map { it.id })
            assertUnique("bulletin post", data.posts.map { it.id })
            assertUnique("user", data.users.map { it.uid })
        }
    }

    @Test
    fun `busy blocks use valid days and non-empty hour ranges`() {
        val memberIds = seedFor(0).memberships.map { it.userId }.toSet()
        seedFor(0).busyBlocks.forEach {
            assertTrue("${it.id}: day ${it.dayOfWeek} out of range", it.dayOfWeek in 0..6)
            assertTrue("${it.id}: start ${it.startHour} out of range", it.startHour in 0..23)
            assertTrue("${it.id}: end ${it.endHour} out of range", it.endHour in 1..24)
            assertTrue("${it.id}: empty hour range", it.startHour < it.endHour)
            assertTrue("${it.id}: unknown roommate", it.roommateId in memberIds)
        }
    }

    @Test
    fun `all four roommates have a schedule and they are all different`() {
        val data = seedFor(0)
        val byRoommate = data.busyBlocks.groupBy { it.roommateId }
        assertEquals("every member should have busy blocks", 4, byRoommate.size)

        // "Typical but different" is the whole point — if two people ended up
        // with identical timetables the recommendation engine has nothing to
        // discriminate on.
        val shapes = byRoommate.values.map { blocks ->
            blocks.map { Triple(it.dayOfWeek, it.startHour, it.endHour) }.toSet()
        }
        assertEquals("two roommates share an identical schedule", shapes.size, shapes.toSet().size)
    }

    @Test
    fun `exactly one creator owns the household`() {
        val data = seedFor(0)
        val creators = data.memberships.filter { it.role == HouseholdRole.CREATOR }
        assertEquals(1, creators.size)
        assertEquals(DemoHousehold.OWNER_PLACEHOLDER_UID, creators.single().userId)
    }

    @Test
    fun `last week is fully settled so nothing gets swept as stale`() {
        // refreshAssignments() runs deleteStaleAvailable(weekStart), which drops
        // any AVAILABLE row from a previous week. History has to be terminal.
        everyDay.forEach { today ->
            seedFor(today).lastWeek.forEach {
                assertTrue(
                    "day $today: ${it.id} is ${it.status}; history must be COMPLETED or MISSED",
                    it.status == AssignmentStatus.COMPLETED || it.status == AssignmentStatus.MISSED
                )
            }
        }
    }

    @Test
    fun `the pending trade always targets a claimable pending assignment`() {
        // respondToTrade() drops a request as stale unless its assignment is
        // still PENDING and still owned by the sender, so this pairing has to
        // hold no matter which day the seed runs on.
        everyDay.forEach { today ->
            val data = seedFor(today)
            val pending = data.trades.single { it.status == TradeStatus.PENDING }
            val target = data.assignments.single { it.id == pending.assignmentId }

            assertEquals("day $today: trade target is not PENDING", AssignmentStatus.PENDING, target.status)
            assertEquals(
                "day $today: trade sender does not own the assignment",
                pending.fromUserId,
                target.assignedToRoommateId
            )
            assertEquals(
                "the live trade should land in the owner's inbox",
                DemoHousehold.OWNER_PLACEHOLDER_UID,
                pending.toUserId
            )
        }
    }

    @Test
    fun `every trade references a real assignment and two real members`() {
        val memberIds = seedFor(0).memberships.map { it.userId }.toSet()
        everyDay.forEach { today ->
            val data = seedFor(today)
            val assignmentIds = data.assignments.map { it.id }.toSet()
            data.trades.forEach {
                assertTrue("day $today: trade ${it.id} references unknown assignment", it.assignmentId in assignmentIds)
                assertTrue("trade ${it.id}: unknown sender", it.fromUserId in memberIds)
                assertTrue("trade ${it.id}: unknown recipient", it.toUserId in memberIds)
                assertTrue("trade ${it.id}: sent to self", it.fromUserId != it.toUserId)
            }
        }
    }

    @Test
    fun `the board always has something to claim and something already done`() {
        // A demo that opens on an empty or uniformly-complete board is a dud.
        everyDay.forEach { today ->
            val statuses = seedFor(today).assignments.map { it.status }
            assertTrue("day $today: nothing available to claim", AssignmentStatus.AVAILABLE in statuses)
            assertTrue("day $today: nothing in progress", AssignmentStatus.PENDING in statuses)
            assertTrue("day $today: no completed history", AssignmentStatus.COMPLETED in statuses)
            assertTrue("day $today: no missed chore to show", AssignmentStatus.MISSED in statuses)
        }
    }
}
