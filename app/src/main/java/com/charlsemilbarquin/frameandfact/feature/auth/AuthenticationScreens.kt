package com.charlsemilbarquin.frameandfact.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestMark

@Composable
fun AuthenticationGate(viewModel: AuthViewModel) {
    val screen by viewModel.entryScreen.collectAsState()
    val feedback by viewModel.feedback.collectAsState()
    when (screen) {
        AuthEntryScreen.LOGIN -> LoginScreen(
            feedback = feedback,
            onLogin = viewModel::login,
            onRegister = viewModel::showRegistration
        )
        AuthEntryScreen.REGISTRATION -> RegistrationScreen(
            feedback = feedback,
            onSubmit = viewModel::register,
            onLogin = viewModel::showLogin
        )
    }
}

@Composable
internal fun LoginScreen(
    feedback: AuthFeedback,
    onLogin: (String, String, Boolean) -> Unit,
    onRegister: () -> Unit
) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var rememberMe by rememberSaveable { mutableStateOf(true) }
    AuthPage {
        Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
        Text("Sign in to continue to your local SnapQuest account.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it },
            label = { Text("Username or email") },
            singleLine = true,
            enabled = !feedback.isBusy,
            modifier = Modifier.fillMaxWidth().testTag("login-identifier")
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            enabled = !feedback.isBusy,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }, enabled = !feedback.isBusy) {
                    Text(if (passwordVisible) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("login-password")
        )
        Row(
            modifier = Modifier.fillMaxWidth().clickable(
                enabled = !feedback.isBusy,
                role = Role.Checkbox,
                onClick = { rememberMe = !rememberMe }
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = rememberMe,
                onCheckedChange = { rememberMe = it },
                enabled = !feedback.isBusy,
                modifier = Modifier.testTag("remember-me-checkbox")
            )
            Text("Remember Me", color = MaterialTheme.colorScheme.onSurface)
        }
        FeedbackText(feedback)
        Button(
            onClick = { onLogin(identifier, password, rememberMe) },
            enabled = !feedback.isBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("login-submit")
        ) {
            if (feedback.isBusy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
            else Text("Log in")
        }
        TextButton(onClick = onRegister, enabled = !feedback.isBusy) { Text("Create an account") }
        Text("Accounts are stored on this device, are not verified online or synced, and share this installation's game history, profiles, statistics, and reports.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun RegistrationScreen(
    feedback: AuthFeedback,
    onSubmit: (String, String, String, String, String) -> Unit,
    onLogin: () -> Unit
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordsVisible by rememberSaveable { mutableStateOf(false) }
    AuthPage {
        Text("Create your account", style = MaterialTheme.typography.headlineMedium)
        Text("This local sign-in is separate from multiplayer player profiles. Game history, profiles, statistics, and reports belong to the signed-in account and stay on this installation; nothing syncs online.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name") }, singleLine = true, enabled = !feedback.isBusy, modifier = Modifier.fillMaxWidth().testTag("register-full-name"))
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, enabled = !feedback.isBusy, modifier = Modifier.fillMaxWidth().testTag("register-username"))
        OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") }, singleLine = true, enabled = !feedback.isBusy, modifier = Modifier.fillMaxWidth().testTag("register-email"))
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") }, singleLine = true, enabled = !feedback.isBusy,
            visualTransformation = if (passwordsVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("register-password")
        )
        OutlinedTextField(
            confirmation, { confirmation = it }, label = { Text("Confirm password") }, singleLine = true, enabled = !feedback.isBusy,
            visualTransformation = if (passwordsVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("register-confirm-password")
        )
        TextButton(onClick = { passwordsVisible = !passwordsVisible }, enabled = !feedback.isBusy) { Text(if (passwordsVisible) "Hide passwords" else "Show passwords") }
        Text("Use 10–128 characters with at least one letter and one number.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FeedbackText(feedback)
        Button(
            onClick = { onSubmit(fullName, username, email, password, confirmation) },
            enabled = !feedback.isBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("register-submit")
        ) {
            if (feedback.isBusy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
            else Text("Create account")
        }
        TextButton(onClick = onLogin, enabled = !feedback.isBusy) { Text("Already have an account? Log in") }
    }
}

@Composable
private fun AuthPage(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SnapQuestMark(Modifier.height(56.dp))
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

@Composable
private fun FeedbackText(feedback: AuthFeedback) {
    feedback.message?.let { message ->
        Text(
            message,
            color = if (feedback.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().testTag("auth-feedback")
        )
    }
}
