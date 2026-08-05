package com.example.houseflow.data

import com.example.houseflow.model.User

// The pre-seeded demo roommates, defined once so their uids stay consistent
// across the user, membership, busy-block, and chore seed data.
//
// These are REAL Firebase Auth accounts (per the HF-2 decision): the uids below
// are the actual Firebase Auth uids for maya@houseflow.demo / jake@houseflow.demo
// / priya@houseflow.demo, so you can sign in as any of them and land on their
// seeded schedule and chores.
object DemoAccounts {
    const val HOUSEHOLD_ID = "household-1"

    val MAYA = User(uid = "FQY4uJtyTPWRuffTXqyTw8tnHIp2", email = "maya@houseflow.demo", displayName = "Maya")
    val JAKE = User(uid = "R891SPtU09hpwN985sJBcZojsBg2", email = "jake@houseflow.demo", displayName = "Jake")
    val PRIYA = User(uid = "NvrEZtU6yae7BtKgFOHecuQlrz52", email = "priya@houseflow.demo", displayName = "Priya")

    val all: List<User> = listOf(MAYA, JAKE, PRIYA)

    // ---- "Ana Baker" demo household (presentation flow) ----
    // ANA is a REAL Firebase account (ana@houseflow.demo / password: anademo1) so a
    // presenter can sign in as her. Her 4 roommates are Room-only mock members —
    // they never sign in; they exist to populate schedules, chores, and the two
    // incoming chore-trade requests addressed to Ana.
    const val ANA_HOUSEHOLD_ID = "household-ana"

    val ANA = User(uid = "UHpZWG2aWDfY7MGGjHB2OrgVeo32", email = "ana@houseflow.demo", displayName = "Ana Baker")
    val BEN = User(uid = "mock-ben", email = "ben@houseflow.demo", displayName = "Ben Carter")
    val CHLOE = User(uid = "mock-chloe", email = "chloe@houseflow.demo", displayName = "Chloe Nguyen")
    val DIEGO = User(uid = "mock-diego", email = "diego@houseflow.demo", displayName = "Diego Silva")
    val EMMA = User(uid = "mock-emma", email = "emma@houseflow.demo", displayName = "Emma Rossi")

    val anaHousehold: List<User> = listOf(ANA, BEN, CHLOE, DIEGO, EMMA)
}
