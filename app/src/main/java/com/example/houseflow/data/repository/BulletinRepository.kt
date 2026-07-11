package com.example.houseflow.data.repository

import com.example.houseflow.model.BulletinPost

// House Bulletin posts. Extracted from AppViewModel in HF-3 so they persist.
// Backed by Room.
interface BulletinRepository {
    suspend fun getPosts(householdId: String): List<BulletinPost>
    suspend fun addPost(post: BulletinPost)
    suspend fun deletePost(postId: String)
}
