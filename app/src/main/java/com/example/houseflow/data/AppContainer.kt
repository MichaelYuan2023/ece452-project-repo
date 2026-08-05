package com.example.houseflow.data

import android.content.Context
import com.example.houseflow.data.local.HouseflowDatabase
import com.example.houseflow.data.repository.AuthRepository
import com.example.houseflow.data.repository.BulletinRepository
import com.example.houseflow.data.repository.ChoreRepository
import com.example.houseflow.data.repository.ExpenseRepository
import com.example.houseflow.data.repository.FirebaseAuthRepository
import com.example.houseflow.data.repository.HouseholdRepository
import com.example.houseflow.data.repository.PointsRepository
import com.example.houseflow.data.repository.RoomBulletinRepository
import com.example.houseflow.data.repository.RoomChoreRepository
import com.example.houseflow.data.repository.RoomExpenseRepository
import com.example.houseflow.data.repository.RoomHouseholdRepository
import com.example.houseflow.data.repository.RoomPointsRepository
import com.example.houseflow.data.repository.RoomUserRepository
import com.example.houseflow.data.repository.UserRepository
import com.example.houseflow.notification.AndroidNotificationDispatcher
import com.example.houseflow.notification.NotificationDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    lateinit var pointsRepository: PointsRepository
        private set
    lateinit var expenseRepository: ExpenseRepository
        private set
    lateinit var notificationDispatcher: NotificationDispatcher
        private set

    fun init(context: Context) {
        val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = HouseflowDatabase.get(context, seedScope)
        userRepository = RoomUserRepository(db.userDao())
        householdRepository = RoomHouseholdRepository(db.householdDao(), db.membershipDao(), db.busyBlockDao())
        choreRepository = RoomChoreRepository(db.choreDao(), db.assignmentDao(), db.tradeRequestDao())
        bulletinRepository = RoomBulletinRepository(db.bulletinDao())
        pointsRepository = RoomPointsRepository(db.pointsDao())
        expenseRepository = RoomExpenseRepository(db.expenseDao(), db.expenseShareDao(), db.settlementDao())
        notificationDispatcher = AndroidNotificationDispatcher(context.applicationContext)

        // Warm up the database off the main thread at startup. Opening it triggers
        // any first-run seed / destructive-migration reseed (both run on seedScope),
        // so demo data is populated before the user signs in and the ViewModel
        // reads their household — otherwise a reseed triggered lazily by the first
        // post-login query races restoreHousehold and the user lands on the
        // "choose a household" gate with an empty list.
        seedScope.launch { runCatching { db.userDao().getAll() } }
    }
}
