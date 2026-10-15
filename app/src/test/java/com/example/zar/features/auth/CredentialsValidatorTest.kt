package com.example.zar.features.auth

import com.example.zar.features.auth.domain.model.CredentialProblem
import com.example.zar.features.auth.domain.model.CredentialsValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialsValidatorTest {

    @Test
    fun acceptsNormalEmailsWithSurroundingSpaces() {
        assertNull(CredentialsValidator.checkEmail("  dulain@example.lk "))
    }

    @Test
    fun rejectsBlankAndMalformedEmails() {
        assertEquals(CredentialProblem.EMAIL_BLANK, CredentialsValidator.checkEmail(" "))
        assertEquals(CredentialProblem.EMAIL_INVALID, CredentialsValidator.checkEmail("dulain@"))
        assertEquals(CredentialProblem.EMAIL_INVALID, CredentialsValidator.checkEmail("dulain example.com"))
    }

    @Test
    fun signInOnlyNeedsSomePassword() {
        assertTrue(CredentialsValidator.forSignIn("a@b.co", "x").isValid)
        assertEquals(CredentialProblem.PASSWORD_BLANK, CredentialsValidator.forSignIn("a@b.co", "").password)
    }

    @Test
    fun registrationNeedsLongMatchingPasswords() {
        val short = CredentialsValidator.forRegistration("a@b.co", "1234567", "1234567")
        assertEquals(CredentialProblem.PASSWORD_TOO_SHORT, short.password)
        val differ = CredentialsValidator.forRegistration("a@b.co", "12345678", "12345679")
        assertEquals(CredentialProblem.PASSWORDS_DIFFER, differ.confirmation)
        assertTrue(CredentialsValidator.forRegistration("a@b.co", "12345678", "12345678").isValid)
    }
}
