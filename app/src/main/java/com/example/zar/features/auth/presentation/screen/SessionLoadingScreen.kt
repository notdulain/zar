package com.example.zar.features.auth.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.theme.ZarGradients
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.auth.domain.model.AuthError

/** Z38 is visible only while the actual local session restore is pending; no minimum display timer. */
@Composable
fun SessionLoadingScreen(error: AuthError? = null, onRetry: () -> Unit = {}) {
    AuthSystemBars()
    val checking = stringResource(R.string.auth_checking_session)
    Box(Modifier.fillMaxSize().background(ZarGradients.dusk).testTag("session-loading")) {
        Column(Modifier.align(Alignment.Center).offset(y = (-4).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.zar_mark), contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.width(64.08.dp).height(105.12.dp))
            Spacer(Modifier.height(34.dp))
            if (error == null) {
                if (LocalZarMotion.current.allowsSpatialMotion) {
                    LinearProgressIndicator(modifier = Modifier.width(48.dp).height(2.dp).semantics { contentDescription = checking },
                        color = Color.White, trackColor = Color.White.copy(alpha = .2f))
                } else {
                    // Reduced motion has no looping indicator, but still announces truthful pending work.
                    Box(Modifier.width(48.dp).height(2.dp).background(Color.White.copy(alpha = .5f))
                        .semantics { contentDescription = checking })
                }
            } else {
                Text(errorText(error), style = ZarType.bodyCaption, color = Color.White,
                    modifier = Modifier.padding(horizontal = 32.dp).testTag("session-error"))
                TextButton(onClick = onRetry, modifier = Modifier.testTag("session-retry")) {
                    Text(stringResource(R.string.action_retry), color = Color.White)
                }
            }
        }
    }
}
