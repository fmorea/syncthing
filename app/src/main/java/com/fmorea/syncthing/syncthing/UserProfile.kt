package com.fmorea.syncthing.syncthing

import com.google.gson.Gson
import java.io.File
import android.util.Log

data class UserProfile(
    val deviceId: String,
    val discloserId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val country: String = "",
    val address: String = "",
    val gender: String = "",
    val height: String = "",
    val photoPath: String? = null,
    val publicKey: String? = null
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
            val p1 = if (myInfo.exists()) loadFromFile(myInfo, targetDeviceId, myDeviceId) else null
            if (p1?.publicKey != null) return p1

            // 2. Their disclosure about themselves
            val selfInfo = File(rootDir, "${targetDeviceId}_${targetDeviceId}.INFO")
            val p2 = if (selfInfo.exists()) loadFromFile(selfInfo, targetDeviceId, targetDeviceId) else null
            if (p2?.publicKey != null) return p2

            // 3. Fallback to old format or first found
            val oldInfo = File(rootDir, "${targetDeviceId}.INFO")
            val p3 = if (oldInfo.exists()) loadFromFile(oldInfo, targetDeviceId, "") else null
            if (p3?.publicKey != null) return p3

            val match = rootDir.listFiles { _, name -> name.startsWith("${targetDeviceId}_") && name.endsWith(".INFO") }
                ?.firstOrNull()
            val pMatch = if (match != null) {
                val discloser = match.name.removeSuffix(".INFO").substringAfterLast("_")
                loadFromFile(match, targetDeviceId, discloser)
            } else null
            
            val bestProfile = p1 ?: p2 ?: p3 ?: pMatch ?: UserProfile(targetDeviceId)
            
            // IF PUBLIC KEY IS STILL MISSING, SEARCH IN .NET FILES AS FALLBACK
            if (bestProfile.publicKey == null) {
                val netKey = findPublicKeyInNetFiles(targetDeviceId, rootDir)
                if (netKey != null) return bestProfile.copy(publicKey = netKey)
            }

            return bestProfile
        }

        private fun findPublicKeyInNetFiles(targetDeviceId: String, rootDir: File): String? {
            // Search all .net files involving this device
            val netFiles = rootDir.listFiles { _, name -> 
                name.endsWith(".net") && (name.startsWith("${targetDeviceId}_") || name.contains("_$targetDeviceId.net"))
            } ?: return null
            
            netFiles.forEach { file ->
                try {
                    val content = file.readText().trim()
                    if (content.contains(":")) {
                        val key = content.substringAfter(":")
                        if (key.isNotBlank()) return key
                    }
                } catch (e: Exception) {}
            }
            return null
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

        /**
         * Migrates legacy profiles (targetDeviceId.INFO) to the new format (targetDeviceId_discloserId.INFO).
         * Since we don't know the discloser for old files, we assume discloserId = targetDeviceId (Self-disclosure).
         */
        fun migrateLegacyProfiles(rootDir: File) {
            val legacyFiles = rootDir.listFiles { _, name -> 
                name.endsWith(".INFO") && !name.contains("_")
            } ?: return

            val extensions = listOf("jpg", "jpeg", "png", "webp")

            legacyFiles.forEach { file ->
                try {
                    val deviceId = file.name.removeSuffix(".INFO")
                    val profile = loadFromFile(file, deviceId, deviceId)
                    
                    // Save to new format
                    save(profile, deviceId, rootDir)
                    
                    // Migrate photo if exists
                    for (ext in extensions) {
                        val oldPhoto = File(rootDir, "$deviceId.$ext")
                        if (oldPhoto.exists()) {
                            val newPhoto = File(rootDir, "${deviceId}_${deviceId}.$ext")
                            if (!newPhoto.exists()) {
                                oldPhoto.copyTo(newPhoto)
                            }
                            oldPhoto.delete()
                        }
                    }
                    
                    // Delete legacy info file
                    file.delete()
                    Log.d("UserProfile", "Migrated legacy profile for $deviceId")
                } catch (e: Exception) {
                    Log.e("UserProfile", "Failed to migrate ${file.name}", e)
                }
            }
        }
    }
}
