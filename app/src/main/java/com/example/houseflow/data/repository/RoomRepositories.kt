package com.example.houseflow.data.repository

import com.example.houseflow.data.local.AssignmentDao
import com.example.houseflow.data.local.BulletinDao
import com.example.houseflow.data.local.BusyBlockDao
import com.example.houseflow.data.local.ChoreDao
import com.example.houseflow.data.local.HouseholdDao
import com.example.houseflow.data.local.MembershipDao
import com.example.houseflow.data.local.UserDao
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.BulletinPost
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.Household
import com.example.houseflow.model.Roommate

class RoomUserRepository(private val userDao: UserDao) : UserRepository {
    override suspend fun getUser(uid: String): com.example.houseflow.model.User? = userDao.getUser(uid)
    override suspend fun upsertUser(user: com.example.houseflow.model.User) = userDao.upsert(user)
    override suspend fun getUsers() = userDao.getAll()
}

class RoomHouseholdRepository(
    private val householdDao: HouseholdDao,
    private val membershipDao: MembershipDao,
    private val busyBlockDao: BusyBlockDao,
) : HouseholdRepository {

    override suspend fun joinHousehold(code: String): Household? =
        householdDao.getByInviteCode(code.trim().uppercase())

    override suspend fun getHouseholdForUser(userId: String): Household? =
        membershipDao.getByUser(userId)?.let { householdDao.getById(it.householdId) }

    override suspend fun addRoommateToHousehold(householdId: String, roommate: Roommate) =
        membershipDao.upsert(roommate)

    override suspend fun getRoommates(householdId: String): List<Roommate> =
        membershipDao.getByHousehold(householdId)

    override suspend fun getBusyBlocks(roommateId: String): List<BusyBlock> =
        busyBlockDao.getForRoommate(roommateId)

    override suspend fun addBusyBlock(block: BusyBlock) = busyBlockDao.insert(block)

    override suspend fun deleteBusyBlock(blockId: String) = busyBlockDao.delete(blockId)
}

class RoomChoreRepository(
    private val choreDao: ChoreDao,
    private val assignmentDao: AssignmentDao,
) : ChoreRepository {

    override suspend fun getChores(householdId: String): List<Chore> =
        choreDao.getForHousehold(householdId)

    override suspend fun addChore(chore: Chore) = choreDao.insert(chore)

    override suspend fun updateChore(chore: Chore) = choreDao.update(chore)

    override suspend fun deleteChore(choreId: String) {
        choreDao.delete(choreId)
        assignmentDao.deleteForChore(choreId)
    }

    override suspend fun getAssignments(householdId: String): List<ChoreAssignment> =
        assignmentDao.getForHousehold(householdId)

    override suspend fun addAssignment(assignment: ChoreAssignment) = assignmentDao.insert(assignment)

    override suspend fun updateAssignment(assignment: ChoreAssignment) = assignmentDao.update(assignment)

    override suspend fun updateAssignmentStatus(assignmentId: String, status: AssignmentStatus) =
        assignmentDao.updateStatus(assignmentId, status)
}

class RoomBulletinRepository(private val bulletinDao: BulletinDao) : BulletinRepository {
    override suspend fun getPosts(householdId: String): List<BulletinPost> =
        bulletinDao.getForHousehold(householdId)

    override suspend fun addPost(post: BulletinPost) = bulletinDao.insert(post)

    override suspend fun deletePost(postId: String) = bulletinDao.delete(postId)
}
