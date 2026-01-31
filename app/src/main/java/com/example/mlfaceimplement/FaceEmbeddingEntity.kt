package com.example.mlfaceimplement

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "face_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userOwnerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userOwnerId")]
)
data class FaceEmbeddingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userOwnerId: Int,
    val embedding: FloatArray
)
