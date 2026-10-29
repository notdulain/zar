package com.example.zar.features.auth.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.zar.core.presentation.components.findActivity
import androidx.compose.runtime.getValue
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
    // The gradient is dark in both themes, so the status-bar glyphs are light.
    val activity = LocalContext.current.findActivity()
    val view = LocalView.current
    DisposableEffect(activity, view) {
        activity?.let { WindowCompat.getInsetsController(it.window, view).isAppearanceLightStatusBars = false }
        onDispose { }
    }
    SignInContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onForgotPassword = viewModel::sendReset,
        onCreateAccount = onCreateAccount,
    )
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
