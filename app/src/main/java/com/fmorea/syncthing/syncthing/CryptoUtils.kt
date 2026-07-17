package com.fmorea.syncthing.syncthing

import android.util.Base64
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val ALGORITHM = "EC"
    private const val KEY_AGREEMENT_ALGORITHM = "ECDH"
    private const val ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding"

    fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM)
        keyPairGenerator.initialize(256)
        return keyPairGenerator.generateKeyPair()
    }

    fun getSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM)
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

    fun encrypt(data: String, sharedSecret: ByteArray): String {
        val secretKey = SecretKeySpec(sharedSecret.copyOf(16), "AES")
        val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val combined = iv + encryptedData
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encryptedDataWithIv: String, sharedSecret: ByteArray): String {
        val combined = Base64.decode(encryptedDataWithIv, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 16)
        val encryptedData = combined.copyOfRange(16, combined.size)
        val secretKey = SecretKeySpec(sharedSecret.copyOf(16), "AES")
        val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        return String(cipher.doFinal(encryptedData), Charsets.UTF_8)
    }

    fun publicKeyToString(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun stringToPublicKey(publicKeyString: String): PublicKey {
        val keyBytes = Base64.decode(publicKeyString, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(ALGORITHM)
        return keyFactory.generatePublic(spec)
    }
    
    fun privateKeyToString(privateKey: PrivateKey): String {
        return Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
    }

    fun stringToPrivateKey(privateKeyString: String): PrivateKey {
        val keyBytes = Base64.decode(privateKeyString, Base64.NO_WRAP)
        val spec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(ALGORITHM)
        return keyFactory.generatePrivate(spec)
    }
}
