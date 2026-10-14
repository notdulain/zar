package com.example.zar.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zar.features.auth.domain.model.SessionState
import com.example.zar.features.auth.presentation.screen.RegisterScreen
import com.example.zar.features.auth.presentation.screen.SessionLoadingScreen
import com.example.zar.features.auth.presentation.screen.SignInScreen
import com.example.zar.features.auth.presentation.viewmodel.SessionViewModel

/**
 * Chooses between the signed-out screens and the signed-in app. Signing out removes the whole
 * signed-in graph, so no screen, back-stack entry or ViewModel of the previous user survives.
 */
@Composable
fun ZarRoot(viewModel: SessionViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    when (val current = session) {
        SessionState.Loading -> SessionLoadingScreen()
        SessionState.SignedOut -> AuthNavHost()
        // A different user gets a brand-new graph.
        is SessionState.SignedIn -> key(current.user.id) { ZarNavHost() }
    }
}

@Composable
private fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SignInRoute) {
        composable<SignInRoute> {
            SignInScreen(onCreateAccount = { navController.navigate(RegisterRoute) })
        }
        composable<RegisterRoute> {
            RegisterScreen(onSignIn = { navController.popBackStack() })
        }
    }
}
