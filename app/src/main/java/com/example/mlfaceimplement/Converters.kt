package com.example.mlfaceimplement

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromFloatArray(array: FloatArray): String =
        array.joinToString(",")

    @TypeConverter
    fun toFloatArray(data: String): FloatArray =
        data.split(",").map { it.toFloat() }.toFloatArray()
}
