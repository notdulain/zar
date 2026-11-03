package com.example.zar.features.auth.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.core.presentation.theme.ZarGradients
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RegisterContent(
    state: RegisterUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSignIn: () -> Unit,
) {
    AuthSystemBars()
    val busy = state.isSubmitting
    val passwordFocus = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmationVisible by rememberSaveable { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    BoxWithConstraints(Modifier.fillMaxSize().background(ZarGradients.dusk)) {
        val topSpace = minOf(if (imeVisible) 192.dp else 232.dp, maxHeight * .254f)
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp)) {
            Spacer(Modifier.height(topSpace))
            Text(stringResource(R.string.auth_register_headline), style = ZarType.displayTitle.copy(lineHeight = 38.sp), color = Color.White)
            Spacer(Modifier.height(64.dp))
            UnderlineField(state.email, onEmailChange, stringResource(R.string.auth_email), stringResource(R.string.auth_email),
                !busy, "email", KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                KeyboardActions(onNext = { passwordFocus.requestFocus() }), ContentType.EmailAddress,
                problem = state.emailProblem)
            Spacer(Modifier.height(20.dp))
            UnderlineField(state.password, onPasswordChange, stringResource(R.string.auth_password),
                stringResource(R.string.auth_registration_password_placeholder, CredentialsValidator.MIN_PASSWORD_LENGTH),
                !busy, "password", KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                KeyboardActions(onNext = { confirmationFocus.requestFocus() }), ContentType.NewPassword,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                problem = state.passwordProblem, modifier = Modifier.focusRequester(passwordFocus),
                trailing = { PasswordVisibility(passwordVisible, !busy, "password-toggle") { passwordVisible = !passwordVisible } })
            Spacer(Modifier.height(20.dp))
            UnderlineField(state.confirmation, onConfirmationChange, stringResource(R.string.auth_confirm_password),
                stringResource(R.string.auth_confirm_password), !busy, "confirm-password",
                KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                KeyboardActions(onDone = { onSubmit() }), ContentType.NewPassword,
                visualTransformation = if (confirmationVisible) VisualTransformation.None else PasswordVisualTransformation(),
                problem = state.confirmationProblem, modifier = Modifier.focusRequester(confirmationFocus),
                trailing = { PasswordVisibility(confirmationVisible, !busy, "confirmation-toggle") { confirmationVisible = !confirmationVisible } })
            if (state.error != null) {
                Text(errorText(state.error), style = ZarType.bodyCaption, color = Color(0xFFDD8468),
                    modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("auth-error"))
            }
            Spacer(Modifier.height(36.dp))
            AuthSubmitButton(stringResource(if (busy) R.string.auth_registering else R.string.auth_register_action), busy, onSubmit)
            Spacer(Modifier.height(120.dp))
        }
        if (!imeVisible) {
            Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp)
                .heightIn(min = 48.dp).clickable(enabled = !busy, role = Role.Button, onClick = onSignIn).testTag("to-sign-in"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.auth_already_have_account), style = ZarType.bodyS, color = Color.White.copy(alpha = .7f))
                Text(stringResource(R.string.auth_sign_in_action), style = ZarType.bodyS.copy(textDecoration = TextDecoration.Underline), color = Color.White)
            }
        }
    }
}

@Composable
internal fun PasswordVisibility(visible: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).testTag(tag)) {
        Icon(painterResource(if (visible) R.drawable.ic_ph_eye_slash else R.drawable.ic_ph_eye),
            contentDescription = stringResource(if (visible) R.string.auth_hide else R.string.auth_show),
            modifier = Modifier.size(18.dp), tint = Color.White)
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
