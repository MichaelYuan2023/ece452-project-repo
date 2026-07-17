package com.example.houseflow.data

import android.content.Context
import com.example.houseflow.data.local.HouseflowDatabase
import com.example.houseflow.data.repository.AuthRepository
import com.example.houseflow.data.repository.BulletinRepository
import com.example.houseflow.data.repository.ChoreRepository
import com.example.houseflow.data.repository.FirebaseAuthRepository
import com.example.houseflow.data.repository.HouseholdRepository
import com.example.houseflow.data.repository.RoomBulletinRepository
import com.example.houseflow.data.repository.RoomChoreRepository
import com.example.houseflow.data.repository.RoomHouseholdRepository
import com.example.houseflow.data.repository.RoomUserRepository
import com.example.houseflow.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Composition root. Repositories are Room-backed (retiring the in-memory ones in
// HF-3); init() must be called once from the Application before any ViewModel is
// created so the database context is available.
object AppContainer {

    val authRepository: AuthRepository = FirebaseAuthRepository()

    lateinit var userRepository: UserRepository
        private set
    lateinit var householdRepository: HouseholdRepository
        private set
    lateinit var choreRepository: ChoreRepository
        private set
    lateinit var bulletinRepository: BulletinRepository
        private set

    fun init(context: Context) {
        val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = HouseflowDatabase.get(context, seedScope)
        userRepository = RoomUserRepository(db.userDao())
        householdRepository = RoomHouseholdRepository(db.householdDao(), db.membershipDao(), db.busyBlockDao())
        choreRepository = RoomChoreRepository(db.choreDao(), db.assignmentDao(), db.tradeRequestDao())
        bulletinRepository = RoomBulletinRepository(db.bulletinDao())
    }
}
