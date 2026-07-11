package com.example.houseflow.data.local

import androidx.room.TypeConverter
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.BlockType
import com.example.houseflow.model.ChoreFrequency

// Stores the domain enums as their String name in SQLite.
class Converters {
    @TypeConverter fun blockTypeToString(value: BlockType): String = value.name
    @TypeConverter fun blockTypeFromString(value: String): BlockType = BlockType.valueOf(value)

    @TypeConverter fun frequencyToString(value: ChoreFrequency): String = value.name
    @TypeConverter fun frequencyFromString(value: String): ChoreFrequency = ChoreFrequency.valueOf(value)

    @TypeConverter fun statusToString(value: AssignmentStatus): String = value.name
    @TypeConverter fun statusFromString(value: String): AssignmentStatus = AssignmentStatus.valueOf(value)
}
