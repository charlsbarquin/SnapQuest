package com.charlsemilbarquin.frameandfact.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2-HMAC-SHA256 with a per-account 128-bit salt and a 256-bit output. */
object PasswordHasher {
    const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    data class Hash(val saltBase64: String, val hashBase64: String)

    fun hash(password: CharArray): Hash {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val derived = derive(password, salt)
        return Hash(Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(derived))
    }

    fun verify(password: CharArray, saltBase64: String, expectedHashBase64: String): Boolean = try {
        val salt = Base64.getDecoder().decode(saltBase64)
        val expected = Base64.getDecoder().decode(expectedHashBase64)
        MessageDigest.isEqual(derive(password, salt), expected)
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
