package com.fmorea.syncthing.syncthing

import java.io.File

/**
 * Rappresenta un messaggio o un allegato scambiato tramite il Global Network (LinkThing).
 * I messaggi sono salvati come file con formato: timestamp_deviceId[_originalName].ext
 */
data class LinkThingMessage(
    val fileName: String,
    val timestamp: Long,
    val deviceId: String,
    val content: String,
    val isLocal: Boolean = false,
    val isAttachment: Boolean = false,
    val file: File? = null,
    val dateHeader: String? = null, // Intestazione della data calcolata per la UI
    val replyToTimestamp: Long? = null,
    val replyToDeviceId: String? = null,
    val acknowledgments: Map<String, Long> = emptyMap(), // receiverId -> ackTimestamp
    val isMail: Boolean = false,
    val recipientId: String? = null,
    val isDecrypted: Boolean = true
) {
    /**
     * Identificativo univoco del messaggio (timestamp_deviceId).
     */
    val msgId: String get() = "${timestamp}_$deviceId"

    /**
     * Identificatore unico per deduplicazione e chiavi LazyColumn.
     * Usiamo il nome del file che è garantito essere unico nella cartella.
     */
    val uniqueId: String get() = fileName

    /**
     * Nome da visualizzare per gli allegati (estratto dal nome del file).
     */
    val displayName: String get() = if (isAttachment) {
        val name = file?.name ?: "sconosciuto"
        // Estraiamo la parte dopo l'ultimo underscore se presente
        if (name.contains("_")) name.substringAfterLast("_") else name
    } else content

    companion object {
        /**
         * Crea un oggetto LinkThingMessage da un file, validandone il formato.
         */
        fun fromFile(file: File, localDeviceId: String): LinkThingMessage? {
            try {
                val name = file.name
                
                // Escludiamo file di sistema o metadati del network
                if (name.endsWith(".net") || name.endsWith(".INFO") || 
                    name.startsWith(".") || name.contains(".syncthing.")) return null
                
                // Formato atteso: unix_timestamp_device_id_...
                val parts = name.split("_")
                if (parts.size < 2) return null

                // Validazione timestamp (deve essere un numero)
                val timestamp = parts[0].toLongOrNull() ?: return null
                
                // Device ID (rimuoviamo eventuale estensione se è un file .msg semplice)
                val deviceId = if (parts[1].contains(".")) parts[1].split(".")[0] else parts[1]
                
                var replyTs: Long? = null
                var replyId: String? = null
                
                if (name.endsWith(".msg") && parts.size >= 4) {
                    replyTs = parts[2].toLongOrNull()
                    replyId = if (parts[3].contains(".")) parts[3].split(".")[0] else parts[3]
                }

                val isMsg = name.endsWith(".msg")
                val isChess = name.endsWith(".chess")
                val isCal = name.endsWith(".cal")
                val isMail = name.endsWith(".mail")
                
                var recipientId: String? = null
                if (isMail && parts.size >= 3) {
                    recipientId = if (parts[2].contains(".")) parts[2].split(".")[0] else parts[2]
                }

                // Contenuto testuale o descrizione dell'allegato
                val content = when {
                    isMsg || isMail -> file.readText(Charsets.UTF_8)
                    isChess -> "Sfida a scacchi"
                    isCal -> {
                        val event = CalendarEvent.fromFile(file)
                        if (event != null) "Nuovo Evento: ${event.title}"
                        else "Evento condiviso"
                    }
                    else -> "Allegato: ${file.name}"
                }

                return LinkThingMessage(
                    fileName = name,
                    timestamp = timestamp,
                    deviceId = deviceId,
                    content = content,
                    isLocal = deviceId == localDeviceId,
                    isAttachment = !isMsg && !isMail,
                    file = file,
                    replyToTimestamp = replyTs,
                    replyToDeviceId = replyId,
                    isMail = isMail,
                    recipientId = recipientId,
                    isDecrypted = !isMail // Encrypted mails start as not decrypted
                )
            } catch (e: Exception) {
                return null
            }
        }
    }
}
