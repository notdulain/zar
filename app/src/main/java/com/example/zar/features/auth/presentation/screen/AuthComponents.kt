package com.example.zar.features.auth.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.auth.domain.model.AuthError
import com.example.zar.features.auth.domain.model.CredentialProblem
import com.example.zar.features.auth.domain.model.CredentialsValidator

/** Shared layout for signed-out screens: a dark header block above a scrollable form. */
@Composable
internal fun AuthScaffold(title: String, content: @Composable () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(32.dp))
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name).uppercase(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
                Text(
                    text = stringResource(R.string.auth_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f),
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title.uppercase(), style = MaterialTheme.typography.titleMedium)
                content()
            }
        }
    }
}

@Composable
internal fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    problem: CredentialProblem?,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.auth_email)) },
        isError = problem != null,
        supportingText = problem?.let { { Text(problemText(it)) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("email")
            .semantics { contentType = ContentType.EmailAddress },
    )
}

@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    problem: CredentialProblem?,
    enabled: Boolean,
    isNewPassword: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    testTag: String,
    hint: String? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = problem != null,
        supportingText = (problem?.let { problemText(it) } ?: hint)?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(stringResource(if (visible) R.string.auth_hide else R.string.auth_show))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics { contentType = if (isNewPassword) ContentType.NewPassword else ContentType.Password },
    )
}

@Composable
internal fun PrimaryAuthButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = ZarTheme.colors.accentPrimary,
            contentColor = ZarTheme.colors.textOnInverse,
            disabledContainerColor = ZarTheme.colors.accentPrimary,
            disabledContentColor = ZarTheme.colors.textOnInverse,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("submit"),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = ZarTheme.colors.textOnInverse, modifier = Modifier.size(20.dp))
            } else {
                Text(text)
            }
        }
    }
}

@Composable
internal fun AuthErrorText(error: AuthError?) {
    if (error == null) return
    Text(
        text = errorText(error),
        color = ZarTheme.colors.textAttentionStrong,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("auth-error"),
    )
}

@Composable
private fun problemText(problem: CredentialProblem): String = when (problem) {
    CredentialProblem.EMAIL_BLANK -> stringResource(R.string.auth_problem_email_blank)
    CredentialProblem.EMAIL_INVALID -> stringResource(R.string.auth_problem_email_invalid)
    CredentialProblem.PASSWORD_BLANK -> stringResource(R.string.auth_problem_password_blank)
    CredentialProblem.PASSWORD_TOO_SHORT ->
        stringResource(R.string.auth_problem_password_short, CredentialsValidator.MIN_PASSWORD_LENGTH)
    CredentialProblem.PASSWORDS_DIFFER -> stringResource(R.string.auth_problem_passwords_differ)
}

@Composable
private fun errorText(error: AuthError): String = stringResource(
    when (error) {
        is AuthError.WrongCredentials -> R.string.auth_error_wrong_credentials
        is AuthError.EmailAlreadyInUse -> R.string.auth_error_email_in_use
        is AuthError.WeakPassword -> R.string.auth_error_weak_password
        is AuthError.InvalidEmail -> R.string.auth_error_invalid_email
        is AuthError.AccountDisabled -> R.string.auth_error_disabled
        is AuthError.TooManyAttempts -> R.string.auth_error_too_many
        is AuthError.Network -> R.string.auth_error_network
        is AuthError.Unknown -> R.string.auth_error_unknown
    },
)
