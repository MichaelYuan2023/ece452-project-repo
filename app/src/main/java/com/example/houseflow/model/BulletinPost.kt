package com.example.houseflow.model

data class BulletinPost(
    val id: String,
    val householdId: String,
    val authorName: String,
    val title: String,
    val message: String,
    val isEvent: Boolean, // true = event (e.g. house party), false = announcement
    val timestamp: Long // epoch ms
)
