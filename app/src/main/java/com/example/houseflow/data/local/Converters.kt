package com.example.houseflow.data.local

import androidx.room.TypeConverter
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.BlockType
import com.example.houseflow.model.ChoreFrequency
import com.example.houseflow.model.HouseholdRole
import com.example.houseflow.model.Recurrence
import com.example.houseflow.model.SplitType

// Stores the domain enums as their String name in SQLite.
class Converters {
    @TypeConverter fun blockTypeToString(value: BlockType): String = value.name
    @TypeConverter fun blockTypeFromString(value: String): BlockType = BlockType.valueOf(value)

    @TypeConverter fun recurrenceToString(value: Recurrence): String = value.name
    @TypeConverter fun recurrenceFromString(value: String): Recurrence = Recurrence.valueOf(value)

    @TypeConverter fun frequencyToString(value: ChoreFrequency): String = value.name
    @TypeConverter fun frequencyFromString(value: String): ChoreFrequency = ChoreFrequency.valueOf(value)

    @TypeConverter fun statusToString(value: AssignmentStatus): String = value.name
    @TypeConverter fun statusFromString(value: String): AssignmentStatus = AssignmentStatus.valueOf(value)

    @TypeConverter fun roleToString(value: HouseholdRole): String = value.name
    @TypeConverter fun roleFromString(value: String): HouseholdRole = HouseholdRole.valueOf(value)

    @TypeConverter fun splitTypeToString(value: SplitType): String = value.name
    @TypeConverter fun splitTypeFromString(value: String): SplitType = SplitType.valueOf(value)
}
