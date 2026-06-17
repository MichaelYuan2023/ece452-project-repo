package com.example.houseflow.model

enum class BlockType { CLASS, WORK, CLUB, OTHER }

// dayOfWeek: 0=Monday … 6=Sunday
// start/endHour: 0–23, end is exclusive (e.g. 9–17 means 9am to 5pm)
data class BusyBlock(
    val id: String,
    val roommateId: String,
    val dayOfWeek: Int,
    val startHour: Int,
    val endHour: Int,
    val title: String,
    val type: BlockType
)
