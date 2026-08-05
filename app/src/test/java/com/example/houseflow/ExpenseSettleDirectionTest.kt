package com.example.houseflow

import com.example.houseflow.model.Expense
import com.example.houseflow.model.ExpenseShare
import com.example.houseflow.model.HouseholdRole
import com.example.houseflow.model.Roommate
import com.example.houseflow.model.Settlement
import com.example.houseflow.model.SplitType
import com.example.houseflow.util.ExpenseMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// Covers the "Settle" flow in ExpensesScreen: who pays whom, and how much.
//
// Two bugs are pinned here. The screen used to hardcode the current user as the
// payer, so tapping Settle on a roommate who owed *you* recorded you paying
// *them* and doubled what you were owed. It then derived direction from
// household-wide nets, so credit from one roommate could net away a real debt
// to another and leave that debt impossible to pay off.
class ExpenseSettleDirectionTest {

    private val me = "me"
    private val bob = "bob"
    private val cara = "cara"

    private fun roommate(id: String) =
        Roommate(userId = id, householdId = "h", displayName = id, role = HouseholdRole.MEMBER)

    private fun expense(id: String, paidBy: String, cents: Int) = Expense(
        id = id, householdId = "h", paidByUserId = paidBy, createdByUserId = paidBy,
        description = "groceries", amountCents = cents, splitType = SplitType.EQUAL, createdAt = 0
    )

    private fun share(expenseId: String, userId: String, cents: Int) = ExpenseShare(
        id = "$expenseId:$userId", expenseId = expenseId, householdId = "h",
        userId = userId, shareCents = cents
    )

    private val pair = listOf(roommate(me), roommate(bob))
    private val trio = listOf(roommate(me), roommate(bob), roommate(cara))

    private fun pairsFor(
        roommates: List<Roommate>,
        expenses: List<Expense>,
        shares: List<ExpenseShare>,
        settlements: List<Settlement> = emptyList()
    ) = ExpenseMath.pairBalances(me, roommates, expenses, shares, settlements)
        .associate { it.userId to it.netCents }

    private fun householdNetFor(
        userId: String,
        roommates: List<Roommate>,
        expenses: List<Expense>,
        shares: List<ExpenseShare>
    ) = ExpenseMath.balances(roommates, expenses, shares, emptyList())
        .first { it.userId == userId }.netCents

    // What the screen does now: read the pair balance, derive the direction from
    // it, then record exactly that.
    private fun tapSettle(
        roommates: List<Roommate>,
        expenses: List<Expense>,
        shares: List<ExpenseShare>,
        settlements: List<Settlement>,
        otherUserId: String
    ): Settlement? {
        val pairNet = pairsFor(roommates, expenses, shares, settlements)[otherUserId] ?: 0
        val direction = ExpenseMath.settleDirectionForPair(me, otherUserId, pairNet) ?: return null
        return Settlement(
            id = "s${settlements.size}", householdId = "h",
            fromUserId = direction.fromUserId, toUserId = direction.toUserId,
            amountCents = direction.maxCents, createdAt = 0
        )
    }

    @Test
    fun `settling with a roommate who owes me clears us both instead of doubling`() {
        // I paid $30 split two ways -> Bob owes me $15.
        val expenses = listOf(expense("e1", me, 3000))
        val shares = listOf(share("e1", me, 1500), share("e1", bob, 1500))
        assertEquals(1500, pairsFor(pair, expenses, shares)[bob])

        val settlement = tapSettle(pair, expenses, shares, emptyList(), bob)!!
        assertEquals(bob, settlement.fromUserId)   // Bob pays me, not the reverse
        assertEquals(me, settlement.toUserId)

        val after = pairsFor(pair, expenses, shares, listOf(settlement))
        assertEquals(0, after[bob])                // was 3000 under the old wiring
    }

    @Test
    fun `settling with a roommate I owe still clears us both`() {
        val expenses = listOf(expense("e1", bob, 3000))
        val shares = listOf(share("e1", me, 1500), share("e1", bob, 1500))
        assertEquals(-1500, pairsFor(pair, expenses, shares)[bob])

        val settlement = tapSettle(pair, expenses, shares, emptyList(), bob)!!
        assertEquals(me, settlement.fromUserId)
        assertEquals(bob, settlement.toUserId)
        assertEquals(0, pairsFor(pair, expenses, shares, listOf(settlement))[bob])
    }

    @Test
    fun `a debt to one roommate is not netted away by credit from another`() {
        // Bob paid $30 split between us   -> I owe Bob $15.
        // I paid $40 split me and Cara    -> Cara owes me $20.
        val expenses = listOf(expense("e1", bob, 3000), expense("e2", me, 4000))
        val shares = listOf(
            share("e1", me, 1500), share("e1", bob, 1500),
            share("e2", me, 2000), share("e2", cara, 2000)
        )

        // Household-wide, both Bob and I look like creditors, so the old
        // direction check found no payment to make between us at all.
        assertEquals(500, householdNetFor(me, trio, expenses, shares))
        assertEquals(1500, householdNetFor(bob, trio, expenses, shares))
        assertNull(
            ExpenseMath.settleDirection(
                me, householdNetFor(me, trio, expenses, shares),
                bob, householdNetFor(bob, trio, expenses, shares)
            )
        )

        // Pairwise, the $15 I owe Bob is still there and still payable.
        val pairs = pairsFor(trio, expenses, shares)
        assertEquals(-1500, pairs[bob])
        assertEquals(2000, pairs[cara])

        val toBob = tapSettle(trio, expenses, shares, emptyList(), bob)
        assertNotNull("I owe Bob \$15 and must be able to pay him", toBob)
        assertEquals(me, toBob!!.fromUserId)
        assertEquals(1500, toBob.amountCents)
        assertEquals(0, pairsFor(trio, expenses, shares, listOf(toBob))[bob])
    }

    @Test
    fun `I can still pay someone while my household net reads all settled up`() {
        // Bob paid $20 split between us -> I owe Bob $10.
        // I paid $20 split me and Cara  -> Cara owes me $10.
        // Net across the household: zero. The old UI showed "You're all settled
        // up" and hid every Settle button, stranding the debt to Bob.
        val expenses = listOf(expense("e1", bob, 2000), expense("e2", me, 2000))
        val shares = listOf(
            share("e1", me, 1000), share("e1", bob, 1000),
            share("e2", me, 1000), share("e2", cara, 1000)
        )
        assertEquals(0, householdNetFor(me, trio, expenses, shares))

        val pairs = pairsFor(trio, expenses, shares)
        assertEquals(-1000, pairs[bob])     // I owe Bob $10
        assertEquals(1000, pairs[cara])     // Cara owes me $10

        val toBob = tapSettle(trio, expenses, shares, emptyList(), bob)
        assertNotNull("must be able to pay Bob despite a zero household net", toBob)
        assertEquals(me, toBob!!.fromUserId)
        assertEquals(bob, toBob.toUserId)
        assertEquals(1000, toBob.amountCents)
        assertEquals(0, pairsFor(trio, expenses, shares, listOf(toBob))[bob])
    }

    @Test
    fun `amount is capped at the pair balance, not at a larger household figure`() {
        // I paid $30 split three ways -> Bob and Cara owe me $10 each, and my
        // household net is $20. Settling with Bob must cap at his $10.
        val expenses = listOf(expense("e1", me, 3000))
        val shares = listOf(share("e1", me, 1000), share("e1", bob, 1000), share("e1", cara, 1000))
        assertEquals(2000, householdNetFor(me, trio, expenses, shares))

        val direction = ExpenseMath.settleDirectionForPair(me, bob, pairsFor(trio, expenses, shares)[bob]!!)!!
        assertEquals(1000, direction.maxCents)

        val after = pairsFor(
            trio, expenses, shares,
            listOf(Settlement("s", "h", direction.fromUserId, direction.toUserId, direction.maxCents, 0))
        )
        assertEquals(0, after[bob])
        assertEquals(1000, after[cara])   // untouched
    }

    @Test
    fun `settling twice is a no-op because the pair is already square`() {
        val expenses = listOf(expense("e1", me, 3000))
        val shares = listOf(share("e1", me, 1500), share("e1", bob, 1500))
        val first = tapSettle(pair, expenses, shares, emptyList(), bob)!!
        assertNull(tapSettle(pair, expenses, shares, listOf(first), bob))
    }

    @Test
    fun `no direction when either party is already settled`() {
        assertNull(ExpenseMath.settleDirection(me, 0, bob, -1500))
        assertNull(ExpenseMath.settleDirection(me, 1500, bob, 0))
        assertNull(ExpenseMath.settleDirectionForPair(me, bob, 0))
    }

    @Test
    fun `no direction when both are on the same side of zero`() {
        assertNull(ExpenseMath.settleDirection(me, 1500, bob, 2000))
        assertNull(ExpenseMath.settleDirection(me, -1500, bob, -2000))
    }

    @Test
    fun `no direction with oneself`() {
        assertNull(ExpenseMath.settleDirection(me, -1500, me, -1500))
        assertNull(ExpenseMath.settleDirectionForPair(me, me, -1500))
    }

    @Test
    fun `debtor always pays the creditor regardless of argument order`() {
        val fromMe = ExpenseMath.settleDirection(me, -1500, bob, 1500)!!
        assertEquals(me, fromMe.fromUserId)
        assertEquals(bob, fromMe.toUserId)

        val fromBob = ExpenseMath.settleDirection(bob, 1500, me, -1500)!!
        assertEquals(me, fromBob.fromUserId)
        assertEquals(bob, fromBob.toUserId)
        assertEquals(fromMe.maxCents, fromBob.maxCents)
    }

    @Test
    fun `pair direction reads the sign of the pair balance`() {
        val theyPay = ExpenseMath.settleDirectionForPair(me, bob, 1500)!!
        assertEquals(bob, theyPay.fromUserId)
        assertEquals(me, theyPay.toUserId)
        assertEquals(1500, theyPay.maxCents)

        val iPay = ExpenseMath.settleDirectionForPair(me, bob, -1500)!!
        assertEquals(me, iPay.fromUserId)
        assertEquals(bob, iPay.toUserId)
        assertEquals(1500, iPay.maxCents)
    }
}
