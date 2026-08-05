package com.rally26.integration.core.application

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.config.IntegrationProperties
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM envelope encryption for organization/user provider credentials. */
@Component
class CredentialCipher(
    private val properties: IntegrationProperties,
) {
    private val secureRandom = SecureRandom()

    data class EncryptedValue(
        val ciphertext: String,
        val keyVersion: Int,
    )

    fun encrypt(
        plaintext: String,
        aadContext: String,
    ): EncryptedValue {
        val key = configuredKey()
        val nonce = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aadContext.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedValue(
            ciphertext = Base64.getEncoder().encodeToString(nonce + encrypted),
            keyVersion = properties.encryptionKeyVersion,
        )
    }

    fun decrypt(
        ciphertext: String,
        aadContext: String,
        keyVersion: Int,
    ): String {
        if (keyVersion != properties.encryptionKeyVersion) {
            throw ServiceUnavailableException(
                "INTEGRATION_KEY_VERSION_UNAVAILABLE",
                "The integration credential uses an unavailable encryption key version.",
            )
        }
        val bytes =
            try {
                Base64.getDecoder().decode(ciphertext)
            } catch (_: IllegalArgumentException) {
                throw ServiceUnavailableException("INTEGRATION_CREDENTIAL_INVALID", "The integration credential could not be decrypted.")
            }
        if (bytes.size <= NONCE_BYTES) {
            throw ServiceUnavailableException("INTEGRATION_CREDENTIAL_INVALID", "The integration credential could not be decrypted.")
        }
        return try {
            val nonce = bytes.copyOfRange(0, NONCE_BYTES)
            val payload = bytes.copyOfRange(NONCE_BYTES, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, configuredKey(), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(aadContext.toByteArray(Charsets.UTF_8))
            cipher.doFinal(payload).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            throw ServiceUnavailableException("INTEGRATION_CREDENTIAL_INVALID", "The integration credential could not be decrypted.")
        }
    }

    fun validateConfiguredKey(): Boolean = runCatching { configuredKey() }.isSuccess

    private fun configuredKey(): SecretKeySpec {
        if (properties.encryptionKey.isBlank()) {
            throw ServiceUnavailableException(
                "INTEGRATION_ENCRYPTION_NOT_CONFIGURED",
                "Integration credential encryption is not configured.",
            )
        }
        val decoded =
            try {
                Base64.getDecoder().decode(properties.encryptionKey)
            } catch (_: IllegalArgumentException) {
                throw ServiceUnavailableException(
                    "INTEGRATION_ENCRYPTION_NOT_CONFIGURED",
                    "Integration credential encryption is not configured correctly.",
                )
            }
        if (decoded.size != KEY_BYTES) {
            throw ServiceUnavailableException(
                "INTEGRATION_ENCRYPTION_NOT_CONFIGURED",
                "Integration credential encryption requires a 32-byte base64 key.",
            )
        }
        return SecretKeySpec(decoded, "AES")
    }

    private companion object {
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
    }
}
