package com.charlsemilbarquin.frameandfact.feature.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.charlsemilbarquin.frameandfact.data.auth.AuthOperation
import com.charlsemilbarquin.frameandfact.data.auth.EncryptedSessionStore
import com.charlsemilbarquin.frameandfact.data.auth.LocalAuthRepository
import com.charlsemilbarquin.frameandfact.data.auth.LocalAuthUser
import com.charlsemilbarquin.frameandfact.data.local.history.GameHistoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AuthSessionState {
    data object Loading : AuthSessionState
    data object SignedOut : AuthSessionState
    data class SignedIn(val user: LocalAuthUser) : AuthSessionState
}

enum class AuthEntryScreen { LOGIN, REGISTRATION }

data class AuthFeedback(val message: String? = null, val isError: Boolean = false, val isBusy: Boolean = false)

class AuthViewModel internal constructor(
    application: Application,
    private val repository: LocalAuthRepository
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        LocalAuthRepository(
            GameHistoryDatabase.getInstance(application.applicationContext).authAccountDao(),
            EncryptedSessionStore(application.applicationContext)
        )
    )
    private val _session = MutableStateFlow<AuthSessionState>(AuthSessionState.Loading)
    val session: StateFlow<AuthSessionState> = _session.asStateFlow()
    private val _entryScreen = MutableStateFlow(AuthEntryScreen.LOGIN)
    val entryScreen: StateFlow<AuthEntryScreen> = _entryScreen.asStateFlow()
    private val _feedback = MutableStateFlow(AuthFeedback())
    val feedback: StateFlow<AuthFeedback> = _feedback.asStateFlow()

    init {
        viewModelScope.launch {
            val user = withContext(Dispatchers.IO) { repository.restoreSession() }
            _session.value = user?.let(AuthSessionState::SignedIn) ?: AuthSessionState.SignedOut
        }
    }

    fun showRegistration() {
        _entryScreen.value = AuthEntryScreen.REGISTRATION
        _feedback.value = AuthFeedback()
    }

    fun showLogin() {
        _entryScreen.value = AuthEntryScreen.LOGIN
        _feedback.value = AuthFeedback()
    }

    fun register(fullName: String, username: String, email: String, password: String, confirmation: String) {
        if (_feedback.value.isBusy) return
        viewModelScope.launch {
            _feedback.value = AuthFeedback(isBusy = true)
            when (val result = repository.register(fullName, username, email, password, confirmation)) {
                is AuthOperation.Success -> {
                    _entryScreen.value = AuthEntryScreen.LOGIN
                    _feedback.value = AuthFeedback("Account created. Sign in to continue.")
                }
                is AuthOperation.Failure -> _feedback.value = AuthFeedback(result.message, isError = true)
            }
        }
    }

    fun login(identifier: String, password: String, rememberMe: Boolean = true) {
        if (_feedback.value.isBusy) return
        viewModelScope.launch {
            _feedback.value = AuthFeedback(isBusy = true)
            when (val result = repository.login(identifier, password, rememberMe)) {
                is AuthOperation.Success -> {
                    _feedback.value = AuthFeedback()
                    _session.value = AuthSessionState.SignedIn(result.user)
                }
                is AuthOperation.Failure -> _feedback.value = AuthFeedback(result.message, isError = true)
            }
        }
    }

    fun logout() {
        if (_feedback.value.isBusy) return
        viewModelScope.launch {
            _feedback.value = AuthFeedback(isBusy = true)
            if (repository.logout()) {
                _feedback.value = AuthFeedback()
                _session.value = AuthSessionState.SignedOut
                _entryScreen.value = AuthEntryScreen.LOGIN
            } else {
                _feedback.value = AuthFeedback("Could not securely end the session. Please try again.", isError = true)
            }
        }
    }
}
