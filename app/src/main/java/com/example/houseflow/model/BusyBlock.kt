package com.example.houseflow.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BlockType { CLASS, WORK, CLUB, OTHER }

// Whether a busy block repeats every week or is tied to one specific date.
enum class Recurrence { WEEKLY, ONE_TIME }

// A busy period on a roommate's schedule. HF-10 made this date-aware:
//   - WEEKLY   : repeats on [dayOfWeek] every week (the original behavior).
//   - ONE_TIME : happens once, on [date]; [dayOfWeek] is derived from that date
//                so weekly-grid views that key off dayOfWeek keep working.
// start/endHour: 0–24, end exclusive (e.g. 9–17 means 9am to 5pm).
@Entity(tableName = "busy_blocks")
data class BusyBlock(
    @PrimaryKey val id: String,
    val roommateId: String,
    val dayOfWeek: Int,                 // 0=Monday … 6=Sunday
    val startHour: Int,
    val endHour: Int,
    val title: String,
    val type: BlockType,
    val recurrence: Recurrence = Recurrence.WEEKLY,
    val date: Long? = null,             // epoch ms at local midnight; null for WEEKLY
    val sourceUid: String? = null       // ICS UID for imported blocks (HF-11); null if manual
)
