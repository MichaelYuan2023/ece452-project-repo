package com.example.houseflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.houseflow.model.BulletinPost
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.Household
import com.example.houseflow.model.Roommate
import com.example.houseflow.model.TradeRequest
import com.example.houseflow.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(
    entities = [
        User::class,
        Household::class,
        Roommate::class,
        BusyBlock::class,
        Chore::class,
        ChoreAssignment::class,
        BulletinPost::class,
        TradeRequest::class,
    ],
    // 5 adds no columns — it was bumped alongside the Maple Street demo
    // household and is kept only so devices already sitting at 5 aren't forced
    // through a downgrade. The seed itself no longer depends on the version:
    // see DemoHouseholdSeeder.seedIfMissing, which runs at startup instead.
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HouseflowDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun householdDao(): HouseholdDao
    abstract fun membershipDao(): MembershipDao
    abstract fun busyBlockDao(): BusyBlockDao
    abstract fun choreDao(): ChoreDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun bulletinDao(): BulletinDao
    abstract fun tradeRequestDao(): TradeRequestDao
    abstract fun seedClaimDao(): SeedClaimDao

    companion object {
        @Volatile
        private var INSTANCE: HouseflowDatabase? = null

        // seedScope runs the first-run seed off the main thread.
        //
        // Careful: onCreate below fires only when the database FILE is created.
        // Room does not call it after a destructive migration, so on any device
        // that has had the app installed before, this callback never runs again.
        // The demo household is therefore seeded from AppContainer.init instead
        // (DemoHouseholdSeeder.seedIfMissing); DatabaseSeeder is left on
        // onCreate because it only ever mattered for fresh installs.
        fun get(context: Context, seedScope: CoroutineScope): HouseflowDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HouseflowDatabase::class.java,
                    "houseflow.db"
                )
                    // No production data to preserve yet — destructive migration is
                    // fine while the schema is still evolving pre-release.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            // Fires only when the DB file is first created.
                            seedScope.launch {
                                INSTANCE?.let { DatabaseSeeder.seed(it) }
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
