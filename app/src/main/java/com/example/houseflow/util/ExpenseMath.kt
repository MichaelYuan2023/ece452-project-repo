package com.example.houseflow.util

import com.example.houseflow.model.Expense
import com.example.houseflow.model.ExpenseShare
import com.example.houseflow.model.Roommate
import com.example.houseflow.model.Settlement

// Pure money math for HF-15 shared expenses. All amounts are integer cents. No
// Android/Room dependencies so it can be unit-tested in isolation
// (see ExpenseMathTest).
object ExpenseMath {

    // Splits [totalCents] as evenly as possible across [participantIds]. Any
    // remainder cents (total not divisible by the participant count) are handed
    // out one-per-participant to the first participants, so the shares always sum
    // back to exactly [totalCents]. Returns userId -> shareCents.
    fun equalSplit(totalCents: Int, participantIds: List<String>): Map<String, Int> {
        if (participantIds.isEmpty()) return emptyMap()
        val base = totalCents / participantIds.size
        var remainder = totalCents % participantIds.size
        return participantIds.associateWith { id ->
            if (remainder > 0) { remainder--; base + 1 } else base
        }
    }

    // Net balance for a roommate. netCents > 0 => the household owes them;
    // netCents < 0 => they owe the household; 0 => settled up.
    data class Balance(val userId: String, val displayName: String, val netCents: Int)

    // Derives every roommate's net balance purely from expenses, their shares,
    // and recorded settlements. The returned nets always sum to zero.
    //
    //   for each expense:     payer is credited the full amount,
    //                         each participant is debited their share
    //   for each settlement:  the payer's balance rises, the receiver's falls
    fun balances(
        roommates: List<Roommate>,
        expenses: List<Expense>,
        shares: List<ExpenseShare>,
        settlements: List<Settlement>
    ): List<Balance> {
        val net = LinkedHashMap<String, Int>()
        roommates.forEach { net[it.userId] = 0 }

        val expenseById = expenses.associateBy { it.id }
        expenses.forEach { net[it.paidByUserId] = (net[it.paidByUserId] ?: 0) + it.amountCents }
        shares.forEach { share ->
            // Ignore orphaned shares whose expense no longer exists.
            if (expenseById.containsKey(share.expenseId)) {
                net[share.userId] = (net[share.userId] ?: 0) - share.shareCents
            }
        }
        settlements.forEach { s ->
            net[s.fromUserId] = (net[s.fromUserId] ?: 0) + s.amountCents
            net[s.toUserId] = (net[s.toUserId] ?: 0) - s.amountCents
        }

        val nameById = roommates.associate { it.userId to it.displayName }
        return net.map { (userId, cents) ->
            Balance(userId, nameById[userId] ?: "?", cents)
        }
    }

    // Parses a user-entered dollar amount into cents. Accepts "12", "12.5",
    // "12.50", optional leading "$" and surrounding whitespace. Returns null for
    // empty/invalid input, more than two decimal places, or non-positive values.
    fun parseAmountToCents(input: String): Int? {
        val cleaned = input.trim().removePrefix("$").trim()
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(".")
        if (parts.size > 2) return null
        val whole = parts[0]
        if (whole.isNotEmpty() && !whole.all { it.isDigit() }) return null
        val dollars = if (whole.isEmpty()) 0 else whole.toLongOrNull() ?: return null
        var centsPart = 0
        if (parts.size == 2) {
            val frac = parts[1]
            if (frac.isEmpty() || frac.length > 2 || !frac.all { it.isDigit() }) return null
            centsPart = (if (frac.length == 1) frac + "0" else frac).toInt()
        }
        val total = dollars * 100 + centsPart
        if (total <= 0 || total > Int.MAX_VALUE) return null
        return total.toInt()
    }

    // Formats cents as a "$X.XX" string (negatives as "-$X.XX").
    fun formatCents(cents: Int): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        return "$sign$${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }
}
