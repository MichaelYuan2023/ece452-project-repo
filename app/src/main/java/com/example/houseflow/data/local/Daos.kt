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
import com.example.houseflow.model.Expense
import com.example.houseflow.model.ExpenseShare
import com.example.houseflow.model.Household
import com.example.houseflow.model.PointsEntry
import com.example.houseflow.model.Roommate
import com.example.houseflow.model.Settlement
import com.example.houseflow.model.TradeRequest
import com.example.houseflow.model.TradeStatus
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blocks: List<BusyBlock>)

    @Query("DELETE FROM busy_blocks WHERE id = :blockId")
    suspend fun delete(blockId: String)

    // HF-11: removes only calendar-imported blocks (sourceUid set), leaving
    // manually added blocks untouched. Used to replace the imported set on
    // re-import so there are never duplicates and upstream removals propagate.
    @Query("DELETE FROM busy_blocks WHERE roommateId = :roommateId AND sourceUid IS NOT NULL")
    suspend fun deleteImportedForRoommate(roommateId: String)
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
interface TradeRequestDao {
    @Query("SELECT * FROM trade_requests WHERE householdId = :householdId")
    suspend fun getForHousehold(householdId: String): List<TradeRequest>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: TradeRequest)

    // Conditional so concurrent accept/deny taps can't both resolve — the
    // first write wins and the loser sees 0 rows updated.
    @Query("UPDATE trade_requests SET status = :status WHERE id = :requestId AND status = 'PENDING'")
    suspend fun resolve(requestId: String, status: TradeStatus): Int

    @Query("DELETE FROM trade_requests WHERE id = :requestId")
    suspend fun delete(requestId: String)

    @Query("DELETE FROM trade_requests WHERE assignmentId IN (SELECT id FROM assignments WHERE choreId = :choreId)")
    suspend fun deleteForChore(choreId: String)
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

// --- HF-13: gamification points ledger ---

@Dao
interface PointsDao {
    @Query("SELECT * FROM points_entries WHERE householdId = :householdId ORDER BY awardedAt DESC")
    suspend fun getForHousehold(householdId: String): List<PointsEntry>

    // IGNORE makes awarding idempotent: the PK is the assignment id, so a second
    // award for the same completed assignment is a no-op.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: PointsEntry): Long

    @Query("SELECT COUNT(*) FROM points_entries WHERE id = :assignmentId")
    suspend fun existsForAssignment(assignmentId: String): Int

    // Cascade with chore deletion — mirrors TradeRequestDao.deleteForChore. Must
    // run before the assignments themselves are deleted (the subquery reads them).
    @Query("DELETE FROM points_entries WHERE id IN (SELECT id FROM assignments WHERE choreId = :choreId)")
    suspend fun deleteForChore(choreId: String)
}

// --- HF-15: shared expenses ---

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE householdId = :householdId ORDER BY createdAt DESC")
    suspend fun getForHousehold(householdId: String): List<Expense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun delete(expenseId: String)
}

@Dao
interface ExpenseShareDao {
    @Query("SELECT * FROM expense_shares WHERE householdId = :householdId")
    suspend fun getForHousehold(householdId: String): List<ExpenseShare>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shares: List<ExpenseShare>)

    @Query("DELETE FROM expense_shares WHERE expenseId = :expenseId")
    suspend fun deleteForExpense(expenseId: String)
}

@Dao
interface SettlementDao {
    @Query("SELECT * FROM settlements WHERE householdId = :householdId ORDER BY createdAt DESC")
    suspend fun getForHousehold(householdId: String): List<Settlement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settlement: Settlement)

    @Query("DELETE FROM settlements WHERE id = :settlementId")
    suspend fun delete(settlementId: String)
}
