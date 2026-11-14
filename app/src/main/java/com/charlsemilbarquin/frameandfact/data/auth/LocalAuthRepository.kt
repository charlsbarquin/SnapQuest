package com.charlsemilbarquin.frameandfact.data.auth

import android.database.sqlite.SQLiteConstraintException
import com.charlsemilbarquin.frameandfact.data.local.history.AuthAccountDao
import com.charlsemilbarquin.frameandfact.data.local.history.AuthAccountEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

private val USERNAME_PATTERN = Regex("^[A-Za-z0-9._]{3,24}$")
private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

data class LocalAuthUser(val accountId: String, val fullName: String, val username: String)

sealed interface AuthOperation {
    data class Success(val user: LocalAuthUser) : AuthOperation
    data class Failure(val message: String) : AuthOperation
}

/** Authentication is device-local. No email verification or network identity is provided. */
class LocalAuthRepository(
    private val accounts: AuthAccountDao,
    private val sessions: EncryptedSessionStore,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun restoreSession(): LocalAuthUser? = withContext(Dispatchers.IO) {
        try {
            val id = sessions.readAccountId() ?: return@withContext null
            val account = accounts.findById(id) ?: run {
                sessions.clear()
                return@withContext null
            }
            account.toUser()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    suspend fun register(
        fullNameInput: String,
        usernameInput: String,
        emailInput: String,
        passwordInput: String,
        confirmPasswordInput: String
    ): AuthOperation = withContext(Dispatchers.IO) {
        val name = fullNameInput.trim()
        val username = usernameInput.trim()
        val email = emailInput.trim().takeIf(String::isNotEmpty)
        val password = passwordInput.toCharArray()
        val confirmation = confirmPasswordInput.toCharArray()
        try {
            val validation = validateRegistration(name, username, email, passwordInput, confirmPasswordInput)
            if (validation != null) return@withContext AuthOperation.Failure(validation)
            val normalizedUsername = username.lowercase(Locale.ROOT)
            val normalizedEmail = email?.lowercase(Locale.ROOT)
            if (accounts.findByUsername(normalizedUsername) != null) {
                return@withContext AuthOperation.Failure("That username is already registered.")
            }
            if (normalizedEmail != null && accounts.findByLoginIdentifier(normalizedEmail) != null) {
                return@withContext AuthOperation.Failure("That email address is already registered.")
            }
            val secret = PasswordHasher.hash(password)
            val user = LocalAuthUser(UUID.randomUUID().toString(), name, username)
            val inserted = accounts.insert(
                AuthAccountEntity(
                    accountId = user.accountId,
                    fullName = user.fullName,
                    username = user.username,
                    normalizedUsername = normalizedUsername,
                    normalizedEmail = normalizedEmail,
                    passwordSaltBase64 = secret.saltBase64,
                    passwordHashBase64 = secret.hashBase64,
                    createdAtEpochMillis = nowEpochMillis()
                )
            )
            if (inserted == -1L) AuthOperation.Failure("That username or email address is already registered.")
            else AuthOperation.Success(user)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SQLiteConstraintException) {
            AuthOperation.Failure("That username or email address is already registered.")
        } catch (_: Exception) {
            AuthOperation.Failure("The account could not be created. Please try again.")
        } finally {
            password.fill('\u0000')
            confirmation.fill('\u0000')
        }
    }

    suspend fun login(
        identifierInput: String,
        passwordInput: String,
        rememberMe: Boolean = true
    ): AuthOperation = withContext(Dispatchers.IO) {
        val identifier = identifierInput.trim().lowercase(Locale.ROOT)
        val password = passwordInput.toCharArray()
        try {
            if (identifier.isEmpty() || password.isEmpty()) {
                return@withContext AuthOperation.Failure("Enter your username or email and password.")
            }
            val account = accounts.findByLoginIdentifier(identifier)
                ?: return@withContext AuthOperation.Failure("Username/email or password is incorrect.")
            if (!PasswordHasher.verify(password, account.passwordSaltBase64, account.passwordHashBase64)) {
                return@withContext AuthOperation.Failure("Username/email or password is incorrect.")
            }
            val user = account.toUser()
            if (rememberMe) sessions.save(user.accountId) else sessions.clear()
            AuthOperation.Success(user)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AuthOperation.Failure("Sign in is temporarily unavailable. Please try again.")
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        try {
            sessions.clear()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    private fun validateRegistration(
        name: String,
        username: String,
        email: String?,
        password: String,
        confirmation: String
    ): String? = when {
        name.length !in 2..80 -> "Enter your full name (2–80 characters)."
        name.any(Char::isISOControl) -> "The full name contains an unsupported character."
        !USERNAME_PATTERN.matches(username) -> "Username must be 3–24 characters using letters, numbers, dots, or underscores."
        email != null && !EMAIL_PATTERN.matches(email) -> "Enter a valid email address or leave it blank."
        password.length !in 10..128 -> "Password must be 10–128 characters long."
        password.none(Char::isLetter) || password.none(Char::isDigit) -> "Password must include at least one letter and one number."
        password != confirmation -> "Passwords do not match."
        else -> null
    }

    private fun AuthAccountEntity.toUser() = LocalAuthUser(accountId, fullName, username)
}
