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
}
