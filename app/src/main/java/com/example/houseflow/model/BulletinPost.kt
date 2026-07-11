package com.example.houseflow.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bulletin_posts")
data class BulletinPost(
    @PrimaryKey val id: String,
    val householdId: String,
    val authorName: String,
    val title: String,
    val message: String,
    val isEvent: Boolean, // true = event (e.g. house party), false = announcement
    val timestamp: Long // epoch ms
)
