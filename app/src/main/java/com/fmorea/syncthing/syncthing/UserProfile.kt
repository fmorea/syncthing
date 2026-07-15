package com.fmorea.syncthing.syncthing

import com.google.gson.Gson
import java.io.File

data class UserProfile(
    val deviceId: String,
    val discloserId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val country: String = "",
    val address: String = "",
    val gender: String = "",
    val height: String = "",
    val photoPath: String? = null
) {
    fun getDisplayName(): String {
        return if (firstName.isNotBlank() || lastName.isNotBlank()) {
            "$firstName $lastName".trim()
        } else {
            deviceId.take(8)
        }
    }

    companion object {
        private val gson = Gson()

        /**
         * Loads the best profile for a device.
         * Priority: 
         * 1. Local (Me assigned to them)
         * 2. Self (They assigned to themselves)
         * 3. Any other
         */
        fun load(targetDeviceId: String, myDeviceId: String, rootDir: File): UserProfile {
            // 1. My disclosure about them
            val myInfo = File(rootDir, "${targetDeviceId}_${myDeviceId}.INFO")
            if (myInfo.exists()) return loadFromFile(myInfo, targetDeviceId, myDeviceId)

            // 2. Their disclosure about themselves
            val selfInfo = File(rootDir, "${targetDeviceId}_${targetDeviceId}.INFO")
            if (selfInfo.exists()) return loadFromFile(selfInfo, targetDeviceId, targetDeviceId)

            // 3. Fallback to old format or first found
            val oldInfo = File(rootDir, "${targetDeviceId}.INFO")
            if (oldInfo.exists()) return loadFromFile(oldInfo, targetDeviceId, "")

            val match = rootDir.listFiles { _, name -> name.startsWith("${targetDeviceId}_") && name.endsWith(".INFO") }
                ?.firstOrNull()
            if (match != null) {
                val discloser = match.name.removeSuffix(".INFO").substringAfterLast("_")
                return loadFromFile(match, targetDeviceId, discloser)
            }

            return UserProfile(targetDeviceId)
        }

        fun loadAll(targetDeviceId: String, rootDir: File): List<UserProfile> {
            val profiles = mutableListOf<UserProfile>()
            rootDir.listFiles { _, name -> name.startsWith("${targetDeviceId}_") && name.endsWith(".INFO") }
                ?.forEach { file ->
                    val discloser = file.name.removeSuffix(".INFO").substringAfterLast("_")
                    profiles.add(loadFromFile(file, targetDeviceId, discloser))
                }
            
            // Also check old format
            val oldInfo = File(rootDir, "${targetDeviceId}.INFO")
            if (oldInfo.exists()) profiles.add(loadFromFile(oldInfo, targetDeviceId, ""))
            
            return profiles.distinctBy { it.discloserId }
        }

        fun delete(deviceId: String, discloserId: String, rootDir: File) {
            val infoFile = File(rootDir, "${deviceId}_${discloserId}.INFO")
            if (infoFile.exists()) infoFile.delete()
            
            val oldInfo = File(rootDir, "${deviceId}.INFO")
            if (discloserId == "" && oldInfo.exists()) oldInfo.delete()

            // Also delete photos
            val extensions = listOf("jpg", "jpeg", "png", "webp")
            for (ext in extensions) {
                File(rootDir, "${deviceId}_${discloserId}.$ext").delete()
                if (discloserId == "") File(rootDir, "${deviceId}.$ext").delete()
            }
        }

        fun loadFromFile(file: File, deviceId: String = "", discloserId: String = ""): UserProfile {
            var dId = deviceId
            var discId = discloserId
            
            if (dId.isBlank() || discId.isBlank()) {
                val nameParts = file.name.removeSuffix(".INFO").split("_")
                if (nameParts.size >= 2) {
                    if (dId.isBlank()) dId = nameParts[0]
                    if (discId.isBlank()) discId = nameParts[1]
                } else if (nameParts.size == 1 && dId.isBlank()) {
                    dId = nameParts[0]
                }
            }

            return try {
                gson.fromJson(file.readText(), UserProfile::class.java).copy(deviceId = dId, discloserId = discId)
            } catch (e: Exception) {
                UserProfile(dId, discId)
            }
        }

        fun save(profile: UserProfile, discloserId: String, rootDir: File) {
            val fileName = "${profile.deviceId}_${discloserId}.INFO"
            val infoFile = File(rootDir, fileName)
            infoFile.writeText(gson.toJson(profile.copy(discloserId = discloserId)))
        }

        fun findPhoto(targetDeviceId: String, myDeviceId: String, rootDir: File): File? {
            val extensions = listOf("jpg", "jpeg", "png", "webp")
            
            // Priority same as INFO
            // 1. My disclosure
            for (ext in extensions) {
                val f = File(rootDir, "${targetDeviceId}_${myDeviceId}.$ext")
                if (f.exists()) return f
            }
            // 2. Self disclosure
            for (ext in extensions) {
                val f = File(rootDir, "${targetDeviceId}_${targetDeviceId}.$ext")
                if (f.exists()) return f
            }
            // 3. Old format
            for (ext in extensions) {
                val f = File(rootDir, "${targetDeviceId}.$ext")
                if (f.exists()) return f
            }
            // 4. Any
            val match = rootDir.listFiles { _, name -> 
                val base = name.substringBeforeLast(".")
                base.startsWith("${targetDeviceId}_") && name.substringAfterLast(".").lowercase() in extensions
            }?.firstOrNull()
            
            return match
        }
    }
}
