package com.example.zar.features.auth.presentation.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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
import com.example.zar.features.auth.presentation.viewmodel.SignInUiState
import com.example.zar.features.auth.presentation.viewmodel.SignInViewModel

@Composable
fun SignInScreen(
    onCreateAccount: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SignInContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onForgotPassword = viewModel::sendReset,
        onCreateAccount = onCreateAccount,
    )
}

@Composable
private fun SignInContent(
    state: SignInUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    AuthScaffold(title = stringResource(R.string.auth_sign_in_title)) {
        EmailField(state.email, onEmailChange, state.emailProblem, enabled = !state.isSubmitting)
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.auth_password),
            problem = state.passwordProblem,
            enabled = !state.isSubmitting,
            isNewPassword = false,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
            testTag = "password",
        )
        AuthErrorText(state.error)
        state.resetSentTo?.let {
            Text(
                text = stringResource(R.string.auth_reset_sent, it),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("reset-sent"),
            )
        }
        PrimaryAuthButton(stringResource(R.string.auth_sign_in_action), state.isSubmitting, onSubmit)
        TextButton(onClick = onForgotPassword, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_forgot_password))
        }
        TextButton(
            onClick = onCreateAccount,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().testTag("to-register"),
        ) {
            Text(stringResource(R.string.auth_to_register))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignInPreview() {
    ZarTheme {
        SignInContent(
            state = SignInUiState(email = "dulain@example.com", error = AuthError.WrongCredentials()),
            onEmailChange = {}, onPasswordChange = {}, onSubmit = {}, onForgotPassword = {}, onCreateAccount = {},
        )
    }
}
