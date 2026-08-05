package com.example.houseflow.data.repository

import androidx.room.withTransaction
import com.example.houseflow.data.DemoHousehold
import com.example.houseflow.data.local.HouseflowDatabase

// What the owner's identity resolved to once the seeded household was claimed.
data class SeedClaim(val displayName: String, val householdId: String)

// Hands the seeded demo household over to a real Firebase account.
//
// The seed is written before anyone signs in, so the owner's rows start out
// under a placeholder uid. The first time someone authenticates with
// DemoHousehold.OWNER_EMAIL, every row pointing at that placeholder is
// re-pointed at their real uid, in one transaction. Returns null for every
// other user; for the owner it keeps returning their resolved display name on
// later sign-ins, even though there is no longer anything to rewrite.
class SeedClaimRepository(private val db: HouseflowDatabase) {

    suspend fun claimIfOwner(uid: String, email: String, displayName: String): SeedClaim? {
        if (!email.equals(DemoHousehold.OWNER_EMAIL, ignoreCase = true)) return null

        val placeholder = DemoHousehold.OWNER_PLACEHOLDER_UID
        if (uid == placeholder) return null

        // FirebaseUser.toUser() falls back to the email address when the account
        // has no display name set, and this demo account has none. Resolve the
        // name the owner should be shown under, preferring whatever Firebase
        // knows if it is a real name rather than the email echoed back.
        val name = displayName
            .takeIf { it.isNotBlank() && !it.equals(email, ignoreCase = true) }
            ?: DemoHousehold.OWNER_DISPLAY_NAME

        // Row rewriting happens once. The name, though, has to be re-asserted on
        // every sign-in: the claim is a no-op from the second launch onward, and
        // without a name to hand back the ViewModel would fall through to the
        // email and syncOwnRoommateDisplayName() would stamp it onto the
        // membership row — so the owner would show up as their own email
        // address on every chore card.
        val dao = db.seedClaimDao()
        if (dao.unclaimedMembershipCount(placeholder) > 0) {
            db.withTransaction {
                dao.claimMemberships(placeholder, uid, name)
                dao.claimBusyBlocks(placeholder, uid)
                dao.claimChores(placeholder, uid)
                dao.claimAssignments(placeholder, uid)
                dao.claimTradesSent(placeholder, uid)
                dao.claimTradesReceived(placeholder, uid)
                dao.deletePlaceholderUser(placeholder)
            }
        }

        return SeedClaim(displayName = name, householdId = DemoHousehold.HOUSEHOLD_ID)
    }
}
