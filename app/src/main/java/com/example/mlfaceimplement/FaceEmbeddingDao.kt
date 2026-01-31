package com.example.mlfaceimplement

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FaceEmbeddingDao {

    @Insert
    fun insertAll(list: List<FaceEmbeddingEntity>)

    @Query("SELECT * FROM face_embeddings")
    fun getAll(): List<FaceEmbeddingEntity>

    @Query("SELECT * FROM face_embeddings WHERE userOwnerId = :uid")
    fun getForUser(uid: Int): List<FaceEmbeddingEntity>
}