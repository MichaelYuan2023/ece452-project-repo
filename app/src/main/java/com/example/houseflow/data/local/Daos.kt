package com.example.houseflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.BulletinPost
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.Household
import com.example.houseflow.model.Roommate
import com.example.houseflow.model.User

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE uid = :uid")
    suspend fun getUser(uid: String): User?

    @Query("SELECT * FROM users")
    suspend fun getAll(): List<User>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: User)

    @Query("UPDATE users SET completedChoreCount = completedChoreCount + 1 WHERE uid = :uid")
    suspend fun incrementCompletedCount(uid: String)
}

@Dao
interface HouseholdDao {
    @Query("SELECT * FROM households WHERE id = :id")
    suspend fun getById(id: String): Household?

    @Query("SELECT * FROM households WHERE inviteCode = :code LIMIT 1")
    suspend fun getByInviteCode(code: String): Household?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(household: Household)
}

@Dao
interface MembershipDao {
    @Query("SELECT * FROM memberships WHERE householdId = :householdId")
    suspend fun getByHousehold(householdId: String): List<Roommate>

    @Query("SELECT * FROM memberships WHERE userId = :userId")
    suspend fun getAllByUser(userId: String): List<Roommate>

    @Query("SELECT * FROM memberships WHERE userId = :userId AND householdId = :householdId LIMIT 1")
    suspend fun getMembership(userId: String, householdId: String): Roommate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(membership: Roommate)
}

@Dao
interface BusyBlockDao {
    @Query("SELECT * FROM busy_blocks WHERE roommateId = :roommateId")
    suspend fun getForRoommate(roommateId: String): List<BusyBlock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: BusyBlock)

    @Query("DELETE FROM busy_blocks WHERE id = :blockId")
    suspend fun delete(blockId: String)
}

@Dao
interface ChoreDao {
    @Query("SELECT * FROM chores WHERE householdId = :householdId")
    suspend fun getForHousehold(householdId: String): List<Chore>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chore: Chore)

    @Update
    suspend fun update(chore: Chore)

    @Query("DELETE FROM chores WHERE id = :choreId")
    suspend fun delete(choreId: String)
}

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignments WHERE householdId = :householdId")
    suspend fun getForHousehold(householdId: String): List<ChoreAssignment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assignment: ChoreAssignment)

    @Update
    suspend fun update(assignment: ChoreAssignment)

    @Query("DELETE FROM assignments WHERE choreId = :choreId")
    suspend fun deleteForChore(choreId: String)

    @Query("UPDATE assignments SET status = :status WHERE id = :assignmentId")
    suspend fun updateStatus(assignmentId: String, status: AssignmentStatus)

    @Query("SELECT COUNT(*) FROM assignments WHERE assignedToRoommateId = :userId AND status = 'COMPLETED'")
    suspend fun countCompleted(userId: String): Int

    @Query("DELETE FROM assignments WHERE status = 'AVAILABLE' AND weekStart < :cutoff")
    suspend fun deleteStaleAvailable(cutoff: Long)
}

@Dao
interface BulletinDao {
    @Query("SELECT * FROM bulletin_posts WHERE householdId = :householdId ORDER BY timestamp DESC")
    suspend fun getForHousehold(householdId: String): List<BulletinPost>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: BulletinPost)

    @Query("DELETE FROM bulletin_posts WHERE id = :postId")
    suspend fun delete(postId: String)
}
