package com.fmorea.syncthing.syncthing

import com.google.gson.Gson
import java.io.File
import java.util.UUID

data class CalendarEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val date: Long, // Epoch timestamp for the day
    val startTime: String? = null, // HH:mm
    val endTime: String? = null,   // HH:mm
    val creatorId: String,
    val lastModifierId: String = creatorId,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
) {
    fun toFile(directory: File) {
        // Use createdAt as the filename prefix for stable identity and correct chat timestamp
        val fileName = "${createdAt}_${creatorId}_${id.take(8)}.cal"
        val file = File(directory, fileName)
        file.writeText(Gson().toJson(this))
    }

    companion object {
        fun fromFile(file: File): CalendarEvent? {
            return try {
                Gson().fromJson(file.readText(), CalendarEvent::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
