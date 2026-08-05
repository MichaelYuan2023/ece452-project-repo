package com.example.houseflow.data

import com.example.houseflow.model.User

// The demo household seeded for live demos: "Maple Street House".
//
// This differs from [DemoAccounts] in one important way. DemoAccounts hardcodes
// real Firebase Auth uids that were looked up by hand. Here the owner is a real
// Firebase account whose uid is NOT known at build time, so the owner is seeded
// under a placeholder uid and *claimed* the first time someone signs in with
// OWNER_EMAIL — see SeedClaimDao / SeedClaimRepository, invoked from
// AppViewModel's auth listener.
//
// The three roommates are fixtures only. They have no Firebase accounts and are
// not meant to be signed in as; they exist so the schedules, chore assignments,
// trades, and the recommendation engine all have realistic data to work with.
object DemoHousehold {

    const val HOUSEHOLD_ID = "household-maple"
    const val HOUSEHOLD_NAME = "Maple Street House"
    const val INVITE_CODE = "MAPLE24"

    // Whoever authenticates with this email takes ownership of the seeded rows.
    const val OWNER_EMAIL = "agentronisthe1@gmail.com"
    const val OWNER_PLACEHOLDER_UID = "maple-owner-unclaimed"
    const val OWNER_DISPLAY_NAME = "Ron"

    val OWNER = User(
        uid = OWNER_PLACEHOLDER_UID,
        email = OWNER_EMAIL,
        displayName = OWNER_DISPLAY_NAME,
        activeHouseholdId = HOUSEHOLD_ID
    )

    // Full-time undergrad: morning lectures, afternoon labs, varsity volleyball.
    val SOFIA = User(
        uid = "maple-sofia",
        email = "sofia.reyes@maple.demo",
        displayName = "Sofia",
        activeHouseholdId = HOUSEHOLD_ID
    )

    // Works a 9-5 downtown, free during the day only on weekends, evening leagues.
    val DEVON = User(
        uid = "maple-devon",
        email = "devon.clarke@maple.demo",
        displayName = "Devon",
        activeHouseholdId = HOUSEHOLD_ID
    )

    // Grad student and TA: teaching mornings, long afternoons in the lab, Sundays out.
    val AMARA = User(
        uid = "maple-amara",
        email = "amara.okafor@maple.demo",
        displayName = "Amara",
        activeHouseholdId = HOUSEHOLD_ID
    )

    val mockRoommates: List<User> = listOf(SOFIA, DEVON, AMARA)
    val all: List<User> = listOf(OWNER) + mockRoommates
}
