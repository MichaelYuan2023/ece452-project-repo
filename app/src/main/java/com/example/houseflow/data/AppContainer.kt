package com.example.houseflow.data

import com.example.houseflow.data.repository.ChoreRepository
import com.example.houseflow.data.repository.HouseholdRepository
import com.example.houseflow.data.repository.InMemoryChoreRepository
import com.example.houseflow.data.repository.InMemoryHouseholdRepository

// Migration seam: swap out the in-memory implementations here for Room-backed ones.
// ViewModels only ever see the interfaces, so nothing else needs to change.
object AppContainer {
    val householdRepository: HouseholdRepository = InMemoryHouseholdRepository()
    val choreRepository: ChoreRepository = InMemoryChoreRepository()
}
