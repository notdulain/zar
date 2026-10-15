package com.example.zar.features.auth.presentation.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.auth.domain.model.AuthError
import com.example.zar.features.auth.domain.model.CredentialsValidator
import com.example.zar.features.auth.presentation.viewmodel.RegisterUiState
import com.example.zar.features.auth.presentation.viewmodel.RegisterViewModel

@Composable
fun RegisterScreen(
    onSignIn: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RegisterContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmationChange = viewModel::onConfirmationChange,
        onSubmit = viewModel::submit,
        onSignIn = onSignIn,
    )
}

@Composable
private fun RegisterContent(
    state: RegisterUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSignIn: () -> Unit,
) {
    AuthScaffold(title = stringResource(R.string.auth_register_title)) {
        EmailField(state.email, onEmailChange, state.emailProblem, enabled = !state.isSubmitting)
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.auth_password),
            problem = state.passwordProblem,
            enabled = !state.isSubmitting,
            isNewPassword = true,
            imeAction = ImeAction.Next,
            onImeAction = {},
            testTag = "password",
            hint = stringResource(R.string.auth_password_hint, CredentialsValidator.MIN_PASSWORD_LENGTH),
        )
        PasswordField(
            value = state.confirmation,
            onValueChange = onConfirmationChange,
            label = stringResource(R.string.auth_confirm_password),
            problem = state.confirmationProblem,
            enabled = !state.isSubmitting,
            isNewPassword = true,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
            testTag = "confirm-password",
        )
        AuthErrorText(state.error)
        PrimaryAuthButton(stringResource(R.string.auth_register_action), state.isSubmitting, onSubmit)
        TextButton(
            onClick = onSignIn,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().testTag("to-sign-in"),
        ) {
            Text(stringResource(R.string.auth_to_sign_in))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterPreview() {
    ZarTheme {
        RegisterContent(
            state = RegisterUiState(email = "dulain@example.com", error = AuthError.EmailAlreadyInUse()),
            onEmailChange = {}, onPasswordChange = {}, onConfirmationChange = {}, onSubmit = {}, onSignIn = {},
        )
    }
}
