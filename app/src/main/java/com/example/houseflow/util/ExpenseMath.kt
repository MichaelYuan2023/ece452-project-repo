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

    // Where one roommate stands with the viewer specifically, rather than with
    // the household as a whole. netCents > 0 => they owe you; netCents < 0 =>
    // you owe them; 0 => the two of you are square.
    data class PairBalance(val userId: String, val displayName: String, val netCents: Int)

    // Every other roommate's standing with [userId], netted per person instead
    // of across the household.
    //
    // A single household net hides real debts: if Bob covered $15 of your share
    // while you are up $40 elsewhere, your net is positive and nothing suggests
    // you owe Bob anything. Paying for someone is what creates a debt, so this
    // walks the shares directly — the payer is owed each other participant's
    // share — and applies settlements to the pair they were recorded between.
    //
    // Each person's pair balances sum to their household net from [balances], so
    // the two views never disagree about how much someone is up or down overall.
    fun pairBalances(
        userId: String,
        roommates: List<Roommate>,
        expenses: List<Expense>,
        shares: List<ExpenseShare>,
        settlements: List<Settlement>
    ): List<PairBalance> {
        val others = roommates.filter { it.userId != userId }
        val net = LinkedHashMap<String, Int>()
        others.forEach { net[it.userId] = 0 }
        // Ignore anyone no longer in the household so departed roommates cannot
        // conjure a balance row out of leftover rows.
        fun add(otherUserId: String, cents: Int) {
            if (net.containsKey(otherUserId)) net[otherUserId] = (net[otherUserId] ?: 0) + cents
        }

        val expenseById = expenses.associateBy { it.id }
        shares.forEach { share ->
            val expense = expenseById[share.expenseId] ?: return@forEach  // orphaned share
            val payer = expense.paidByUserId
            if (share.userId == payer) return@forEach                    // nobody owes themselves
            when (userId) {
                payer -> add(share.userId, share.shareCents)             // I covered their share
                share.userId -> add(payer, -share.shareCents)            // they covered mine
            }
        }
        settlements.forEach { s ->
            when (userId) {
                s.fromUserId -> add(s.toUserId, s.amountCents)           // I paid them
                s.toUserId -> add(s.fromUserId, -s.amountCents)          // they paid me
            }
        }

        return others.map { PairBalance(it.userId, it.displayName, net[it.userId] ?: 0) }
    }

    // Which way a repayment between two roommates has to be recorded, and the
    // most that can sensibly change hands. [maxCents] is the smaller of the two
    // magnitudes: paying more than that would push one of them past zero and
    // flip them from owing to being owed.
    data class SettleDirection(val fromUserId: String, val toUserId: String, val maxCents: Int)

    // Direction for a settlement between [userId] and one other roommate, taken
    // from what the two of them owe each other ([pairNetCents] > 0 => the other
    // owes [userId]). Within a pair the two positions are exactly equal and
    // opposite, so the ceiling is the pair balance itself.
    fun settleDirectionForPair(
        userId: String,
        otherUserId: String,
        pairNetCents: Int
    ): SettleDirection? = settleDirection(userId, pairNetCents, otherUserId, -pairNetCents)

    // Works out who pays whom, given both parties' balances. The one in debt
    // (net < 0) always pays the one who is owed (net > 0). Returns null when no
    // payment between them makes sense — either is already settled, or they are
    // both on the same side of zero and settling would only make things worse.
    //
    // Callers pass pairwise positions via [settleDirectionForPair]; the raw form
    // is kept for callers holding two independent nets.
    fun settleDirection(
        userId: String,
        userNetCents: Int,
        otherUserId: String,
        otherNetCents: Int
    ): SettleDirection? {
        if (userId == otherUserId) return null
        if (userNetCents == 0 || otherNetCents == 0) return null
        if ((userNetCents < 0) == (otherNetCents < 0)) return null
        val maxCents = minOf(kotlin.math.abs(userNetCents), kotlin.math.abs(otherNetCents))
        return if (userNetCents < 0) {
            SettleDirection(fromUserId = userId, toUserId = otherUserId, maxCents = maxCents)
        } else {
            SettleDirection(fromUserId = otherUserId, toUserId = userId, maxCents = maxCents)
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
