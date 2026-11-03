package com.example.myrecipe.data

import android.net.Uri
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromUri(value: Uri?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toUri(value: String?): Uri? {
        return value?.let { Uri.parse(it) }
    }
}
