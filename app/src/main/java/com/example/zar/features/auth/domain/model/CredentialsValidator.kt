package com.example.zar.features.auth.domain.model

enum class CredentialProblem { EMAIL_BLANK, EMAIL_INVALID, PASSWORD_BLANK, PASSWORD_TOO_SHORT, PASSWORDS_DIFFER }

data class CredentialCheck(
    val email: CredentialProblem? = null,
    val password: CredentialProblem? = null,
    val confirmation: CredentialProblem? = null,
) {
    val isValid: Boolean get() = email == null && password == null && confirmation == null
}

/** Checks input before contacting Firebase, so simple mistakes get instant, specific feedback. */
object CredentialsValidator {
    const val MIN_PASSWORD_LENGTH = 8

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

    fun forSignIn(email: String, password: String) = CredentialCheck(
        email = checkEmail(email),
        password = if (password.isEmpty()) CredentialProblem.PASSWORD_BLANK else null,
    )

    fun forRegistration(email: String, password: String, confirmation: String) = CredentialCheck(
        email = checkEmail(email),
        password = when {
            password.isEmpty() -> CredentialProblem.PASSWORD_BLANK
            password.length < MIN_PASSWORD_LENGTH -> CredentialProblem.PASSWORD_TOO_SHORT
            else -> null
        },
        confirmation = if (confirmation != password) CredentialProblem.PASSWORDS_DIFFER else null,
    )

    fun checkEmail(email: String): CredentialProblem? = when {
        email.isBlank() -> CredentialProblem.EMAIL_BLANK
        !EMAIL.matches(email.trim()) -> CredentialProblem.EMAIL_INVALID
        else -> null
    }
}
