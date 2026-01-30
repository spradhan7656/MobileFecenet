package com.example.mlfaceimplement

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UserDao {
    @Insert
    fun insert(user: UserEntity)

    @Query("SELECT * FROM users")
    fun getAll(): List<UserEntity>
}
