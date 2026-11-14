package com.charlsemilbarquin.frameandfact.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    @Test
    fun usesUniqueSaltsAndVerifiesOnlyTheMatchingPassword() {
        val password = "SnapQuest2026".toCharArray()
        val first = PasswordHasher.hash(password)
        val second = PasswordHasher.hash(password)

        assertNotEquals(first.saltBase64, second.saltBase64)
        assertNotEquals(first.hashBase64, second.hashBase64)
        assertTrue(PasswordHasher.verify(password, first.saltBase64, first.hashBase64))
        assertFalse(PasswordHasher.verify("OtherPassword2".toCharArray(), first.saltBase64, first.hashBase64))
        assertFalse(first.hashBase64.contains("SnapQuest2026"))
    }
}
