package com.example.houseflow

import com.example.houseflow.model.Expense
import com.example.houseflow.model.ExpenseShare
import com.example.houseflow.model.HouseholdRole
import com.example.houseflow.model.Roommate
import com.example.houseflow.model.Settlement
import com.example.houseflow.model.SplitType
import com.example.houseflow.util.ExpenseMath
import org.junit.Assert.assertEquals
import org.junit.Test

// ExpenseMath.pairBalances — what each roommate owes the viewer specifically,
// rather than the household-wide net in ExpenseMath.balances.
class ExpensePairBalanceTest {

    private val me = "me"
    private val bob = "bob"
    private val cara = "cara"

    private fun roommate(id: String) =
        Roommate(userId = id, householdId = "h", displayName = id, role = HouseholdRole.MEMBER)

    private fun expense(id: String, paidBy: String, cents: Int) = Expense(
        id = id, householdId = "h", paidByUserId = paidBy, createdByUserId = paidBy,
        description = "e", amountCents = cents, splitType = SplitType.EQUAL, createdAt = 0
    )

    private fun share(expenseId: String, userId: String, cents: Int) = ExpenseShare(
        id = "$expenseId:$userId", expenseId = expenseId, householdId = "h",
        userId = userId, shareCents = cents
    )

    private val trio = listOf(roommate(me), roommate(bob), roommate(cara))

    private fun pairs(
        userId: String,
        expenses: List<Expense>,
        shares: List<ExpenseShare>,
        settlements: List<Settlement> = emptyList(),
        roommates: List<Roommate> = trio
    ) = ExpenseMath.pairBalances(userId, roommates, expenses, shares, settlements)

    @Test
    fun `paying for someone makes them owe you their share`() {
        val expenses = listOf(expense("e1", me, 3000))
        val shares = listOf(share("e1", me, 1000), share("e1", bob, 1000), share("e1", cara, 1000))
        val result = pairs(me, expenses, shares).associate { it.userId to it.netCents }
        assertEquals(1000, result[bob])
        assertEquals(1000, result[cara])
    }

    @Test
    fun `the view is mirrored from the other side`() {
        val expenses = listOf(expense("e1", me, 3000))
        val shares = listOf(share("e1", me, 1000), share("e1", bob, 1000), share("e1", cara, 1000))
        val fromBob = pairs(bob, expenses, shares).associate { it.userId to it.netCents }
        assertEquals(-1000, fromBob[me])   // Bob owes me
        assertEquals(0, fromBob[cara])     // Bob and Cara have nothing between them
    }

    @Test
    fun `the payer never owes themselves`() {
        val expenses = listOf(expense("e1", me, 3000))
        val shares = listOf(share("e1", me, 1000), share("e1", bob, 1000), share("e1", cara, 1000))
        // Own row is excluded entirely, and the payer's own share creates no debt.
        val result = pairs(me, expenses, shares)
        assertEquals(listOf(bob, cara), result.map { it.userId })
        assertEquals(2000, result.sumOf { it.netCents })   // only the other two shares
    }

    @Test
    fun `a settlement only moves the pair it was recorded between`() {
        val expenses = listOf(expense("e1", me, 3000))
        val shares = listOf(share("e1", me, 1000), share("e1", bob, 1000), share("e1", cara, 1000))
        val settlements = listOf(Settlement("s1", "h", fromUserId = bob, toUserId = me, amountCents = 1000, createdAt = 0))
        val result = pairs(me, expenses, shares, settlements).associate { it.userId to it.netCents }
        assertEquals(0, result[bob])
        assertEquals(1000, result[cara])   // Cara is unaffected
    }

    @Test
    fun `pair balances sum to the household net`() {
        // Mixed history: everyone pays for something at least once.
        val expenses = listOf(
            expense("e1", me, 3000), expense("e2", bob, 1500), expense("e3", cara, 900)
        )
        val shares = listOf(
            share("e1", me, 1000), share("e1", bob, 1000), share("e1", cara, 1000),
            share("e2", me, 500), share("e2", bob, 500), share("e2", cara, 500),
            share("e3", me, 300), share("e3", bob, 300), share("e3", cara, 300)
        )
        val settlements = listOf(Settlement("s1", "h", fromUserId = bob, toUserId = me, amountCents = 250, createdAt = 0))
        val household = ExpenseMath.balances(trio, expenses, shares, settlements)
            .associate { it.userId to it.netCents }

        for (userId in listOf(me, bob, cara)) {
            assertEquals(
                "pair balances for $userId must reconcile with their household net",
                household[userId],
                pairs(userId, expenses, shares, settlements).sumOf { it.netCents }
            )
        }
    }

    @Test
    fun `orphaned shares and departed roommates are ignored`() {
        val expenses = listOf(expense("e1", me, 2000))
        val shares = listOf(
            share("e1", me, 1000), share("e1", bob, 1000),
            share("gone", bob, 5000)       // expense no longer exists
        )
        val settlements = listOf(
            Settlement("s1", "h", fromUserId = "departed", toUserId = me, amountCents = 700, createdAt = 0)
        )
        val result = pairs(me, expenses, shares, settlements, roommates = listOf(roommate(me), roommate(bob)))
        assertEquals(listOf(bob), result.map { it.userId })   // no phantom "departed" row
        assertEquals(1000, result.single().netCents)          // orphaned share ignored
    }
}
