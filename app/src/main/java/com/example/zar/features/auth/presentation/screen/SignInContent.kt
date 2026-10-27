package com.example.zar.features.auth.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import com.example.zar.R
import com.example.zar.core.presentation.theme.GeistFamily
import com.example.zar.core.presentation.theme.GeistMonoFamily
import com.example.zar.core.presentation.theme.ZarGradients
import com.example.zar.core.presentation.theme.ZarLightColors
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.auth.domain.model.CredentialProblem
import com.example.zar.features.auth.presentation.viewmodel.SignInUiState

private val White = Color.White
private val Title = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.64).sp, color = White)
private val FieldText = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.16).sp, color = White)
private val FieldLabel = TextStyle(fontFamily = GeistMonoFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.44.sp)
private val Message = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = (-0.14).sp)
private val ErrorDot = Color(0xFFDD8468)

/**
 * The Figma sign-in (Z39–Z42, Z188) on the dusk gradient, both themes alike: big headline, two underlined fields with a
 * floating mono label once filled, an eye toggle and Forgot? on the password row, one message row (an error, a field
 * problem or the reset confirmation, announced as a live region), a full-width pill button and the sign-up link at the
 * foot. The form scrolls and lifts with the keyboard; the footer stays where it is and the keyboard covers it.
 * The state, validation and actions are the existing view model's.
 */
@Composable
internal fun SignInContent(
    state: SignInUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val busy = state.isSubmitting
    val colors = ZarTheme.colors
    val isLight = colors.canvas == ZarLightColors.canvas
    val message: Pair<String, Color>? = when {
        state.error != null -> errorText(state.error) to ErrorDot
        state.emailProblem != null -> problemText(state.emailProblem) to ErrorDot
        state.passwordProblem != null -> problemText(state.passwordProblem) to ErrorDot
        state.resetSentTo != null -> stringResource(R.string.auth_reset_sent, state.resetSentTo) to colors.fresh
        else -> null
    }
    Box(Modifier.fillMaxSize().background(ZarGradients.dusk)) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
        ) {
            Spacer(Modifier.height(232.dp))
            Text(stringResource(R.string.auth_sign_in_headline), style = Title)
            Spacer(Modifier.height(64.dp))
            UnderlineField(
                value = state.email, onValueChange = onEmailChange, label = stringResource(R.string.auth_email),
                placeholder = stringResource(R.string.auth_email), enabled = !busy, testTag = "email",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(),
                contentType = ContentType.EmailAddress,
            )
            Spacer(Modifier.height(20.dp))
            var visible by rememberSaveable { mutableStateOf(false) }
            UnderlineField(
                value = state.password, onValueChange = onPasswordChange, label = stringResource(R.string.auth_password),
                placeholder = stringResource(R.string.auth_password), enabled = !busy, testTag = "password",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onAny = { onSubmit() }),
                contentType = ContentType.Password,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    val toggle = stringResource(if (visible) R.string.auth_hide else R.string.auth_show)
                    Box(
                        Modifier.size(48.dp).clickable(enabled = !busy, role = Role.Button) { visible = !visible }
                            .semantics { contentDescription = toggle }.testTag("password-toggle"),
                        Alignment.Center,
                    ) {
                        Icon(
                            painterResource(if (visible) R.drawable.ic_ph_eye_slash else R.drawable.ic_ph_eye),
                            contentDescription = null, modifier = Modifier.size(18.dp), tint = White.copy(alpha = if (busy) 0.5f else 1f),
                        )
                    }
                    Box(
                        Modifier.heightIn(min = 48.dp).clickable(enabled = !busy, role = Role.Button, onClick = onForgotPassword).padding(horizontal = 4.dp)
                            .testTag("forgot-password"),
                        Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.auth_forgot_short),
                            style = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
                            color = White.copy(alpha = if (busy) 0.35f else 0.7f),
                        )
                    }
                },
            )
            if (message == null) {
                Spacer(Modifier.height(44.dp))
            } else {
                Spacer(Modifier.height(28.dp))
                Row(
                    Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(message.second))
                    Text(
                        message.first, style = Message, color = White.copy(alpha = 0.85f),
                        modifier = Modifier.testTag(if (state.resetSentTo != null && state.error == null) "reset-sent" else "auth-error"),
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(CircleShape)
                    // Light: white on the dark gradient. Dark: the accent blue with a white label.
                    .background(if (isLight) White else colors.accentPrimary)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onSubmit)
                    .testTag("submit"),
                Alignment.Center,
            ) {
                Text(
                    stringResource(if (busy) R.string.auth_signing_in else R.string.auth_sign_in_action),
                    style = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
                    color = if (isLight) Color(0xFF0E0F12) else White,
                )
            }
            Spacer(Modifier.height(240.dp))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp)
                .heightIn(min = 48.dp).clickable(enabled = !busy, role = Role.Button, onClick = onCreateAccount)
                .testTag("to-register"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.auth_new_here),
                style = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = (-0.14).sp),
                color = White.copy(alpha = if (busy) 0.35f else 0.7f),
            )
            Text(
                stringResource(R.string.auth_create_account_link),
                style = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, textDecoration = TextDecoration.Underline),
                color = White.copy(alpha = if (busy) 0.35f else 1f),
            )
        }
    }
}

/**
 * A 40dp field: 16sp white text on a 1dp white-at-25% underline (2dp white while focused), a placeholder at 45% while empty
 * and, once filled, a mono label 10dp above. Not a Material outlined field; the semantics (content type for autofill, a
 * password transformation, IME actions) are the platform's.
 */
@Composable
private fun UnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    testTag: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    contentType: ContentType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable () -> Unit = {},
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val dim = if (enabled) 1f else 0.5f
    Box(Modifier.fillMaxWidth().height(40.dp)) {
        if (value.isNotEmpty()) {
            Text(label.uppercase(), style = FieldLabel, color = White.copy(alpha = 0.5f * dim), modifier = Modifier.offset(y = (-10).dp))
        }
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, style = FieldText, color = White.copy(alpha = 0.45f * dim))
                BasicTextField(
                    value = value, onValueChange = onValueChange, enabled = enabled, singleLine = true,
                    textStyle = FieldText.copy(color = White.copy(alpha = dim)),
                    cursorBrush = SolidColor(White),
                    keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation, interactionSource = source,
                    modifier = Modifier.fillMaxWidth().testTag(testTag).semantics { this.contentType = contentType },
                )
            }
            trailing()
        }
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(if (focused) 2.dp else 1.dp)
                .background(if (focused) White else White.copy(alpha = 0.25f)),
        )
    }
}
