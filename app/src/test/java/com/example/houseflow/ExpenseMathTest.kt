package com.example.houseflow

import com.example.houseflow.model.Expense
import com.example.houseflow.model.ExpenseShare
import com.example.houseflow.model.HouseholdRole
import com.example.houseflow.model.Roommate
import com.example.houseflow.model.Settlement
import com.example.houseflow.model.SplitType
import com.example.houseflow.util.ExpenseMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseMathTest {

    private fun roommate(id: String, name: String = id) =
        Roommate(userId = id, householdId = "h", displayName = name, role = HouseholdRole.MEMBER)

    private fun expense(id: String, paidBy: String, cents: Int) = Expense(
        id = id, householdId = "h", paidByUserId = paidBy, createdByUserId = paidBy,
        description = "e", amountCents = cents, splitType = SplitType.EQUAL, createdAt = 0
    )

    private fun share(expenseId: String, userId: String, cents: Int) =
        ExpenseShare(id = "$expenseId:$userId", expenseId = expenseId, householdId = "h", userId = userId, shareCents = cents)

    @Test
    fun `equal split divides evenly`() {
        val result = ExpenseMath.equalSplit(900, listOf("a", "b", "c"))
        assertEquals(300, result["a"])
        assertEquals(300, result["b"])
        assertEquals(300, result["c"])
    }

    @Test
    fun `equal split hands remainder cents to the first participants`() {
        val result = ExpenseMath.equalSplit(1000, listOf("a", "b", "c"))
        assertEquals(334, result["a"])
        assertEquals(333, result["b"])
        assertEquals(333, result["c"])
        assertEquals(1000, result.values.sum())
    }

    @Test
    fun `equal split with a single participant`() {
        val result = ExpenseMath.equalSplit(1575, listOf("a"))
        assertEquals(1575, result["a"])
    }

    @Test
    fun `balances credit payer and debit participants`() {
        val roommates = listOf(roommate("a"), roommate("b"), roommate("c"))
        val expenses = listOf(expense("e1", "a", 900))
        val shares = listOf(share("e1", "a", 300), share("e1", "b", 300), share("e1", "c", 300))
        val balances = ExpenseMath.balances(roommates, expenses, shares, emptyList())
        assertEquals(600, balances.first { it.userId == "a" }.netCents)  // paid 900, owes 300
        assertEquals(-300, balances.first { it.userId == "b" }.netCents)
        assertEquals(-300, balances.first { it.userId == "c" }.netCents)
        assertEquals(0, balances.sumOf { it.netCents })
    }

    @Test
    fun `settlement moves both parties toward zero`() {
        val roommates = listOf(roommate("a"), roommate("b"), roommate("c"))
        val expenses = listOf(expense("e1", "a", 900))
        val shares = listOf(share("e1", "a", 300), share("e1", "b", 300), share("e1", "c", 300))
        // b pays a back their 300 share
        val settlements = listOf(Settlement("s1", "h", fromUserId = "b", toUserId = "a", amountCents = 300, createdAt = 0))
        val balances = ExpenseMath.balances(roommates, expenses, shares, settlements)
        assertEquals(300, balances.first { it.userId == "a" }.netCents)   // now owed only c's 300
        assertEquals(0, balances.first { it.userId == "b" }.netCents)     // settled
        assertEquals(-300, balances.first { it.userId == "c" }.netCents)
        assertEquals(0, balances.sumOf { it.netCents })
    }

    @Test
    fun `parse accepts whole dollars, one and two decimals`() {
        assertEquals(1200, ExpenseMath.parseAmountToCents("12"))
        assertEquals(1250, ExpenseMath.parseAmountToCents("12.5"))
        assertEquals(1250, ExpenseMath.parseAmountToCents("12.50"))
        assertEquals(1875, ExpenseMath.parseAmountToCents("$18.75"))
        assertEquals(5, ExpenseMath.parseAmountToCents("0.05"))
    }

    @Test
    fun `parse rejects invalid or non-positive input`() {
        assertNull(ExpenseMath.parseAmountToCents(""))
        assertNull(ExpenseMath.parseAmountToCents("0"))
        assertNull(ExpenseMath.parseAmountToCents("-3"))
        assertNull(ExpenseMath.parseAmountToCents("abc"))
        assertNull(ExpenseMath.parseAmountToCents("1.234"))
        assertNull(ExpenseMath.parseAmountToCents("1.2.3"))
    }

    @Test
    fun `format renders dollars and cents`() {
        assertEquals("$18.75", ExpenseMath.formatCents(1875))
        assertEquals("$5.00", ExpenseMath.formatCents(500))
        assertEquals("$0.09", ExpenseMath.formatCents(9))
        assertEquals("-$3.50", ExpenseMath.formatCents(-350))
    }
}
