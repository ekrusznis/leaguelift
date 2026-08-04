package com.rally26.integration.core.application

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.config.IntegrationProperties
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class CredentialCipherTest {
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })

    @Test
    fun `encrypt uses authenticated encryption and decrypt restores plaintext`() {
        val cipher = CredentialCipher(IntegrationProperties(encryptionKey = key, encryptionKeyVersion = 3))

        val encrypted = cipher.encrypt("access-token-value", "provider:owner:connection")

        assertNotEquals("access-token-value", encrypted.ciphertext)
        assertEquals(3, encrypted.keyVersion)
        assertEquals("access-token-value", cipher.decrypt(encrypted.ciphertext, "provider:owner:connection", 3))
    }

    @Test
    fun `decrypt rejects a different aad context`() {
        val cipher = CredentialCipher(IntegrationProperties(encryptionKey = key))
        val encrypted = cipher.encrypt("secret", "connection-a")

        assertFailsWith<ServiceUnavailableException> {
            cipher.decrypt(encrypted.ciphertext, "connection-b", encrypted.keyVersion)
        }
    }
}
