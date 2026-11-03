package com.example.zar.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zar.core.presentation.components.LocalNavCapsuleController
import com.example.zar.core.presentation.components.LocalNavSession
import com.example.zar.core.presentation.components.NavCapsuleController
import com.example.zar.core.presentation.components.rememberNavSession
import com.example.zar.features.auth.domain.model.SessionState
import com.example.zar.features.auth.presentation.screen.RegisterScreen
import com.example.zar.features.auth.presentation.screen.SessionLoadingScreen
import com.example.zar.features.auth.presentation.screen.SignInScreen
import com.example.zar.features.auth.presentation.viewmodel.SessionViewModel
import com.example.zar.features.rules.presentation.screen.RuleOfferHost

/**
 * Chooses between the signed-out screens and the signed-in app. Signing out removes the whole
 * signed-in graph, so no screen, back-stack entry or ViewModel of the previous user survives.
 */
@Composable
fun ZarRoot(
    openCatchUp: Boolean = false,
    onCatchUpOpened: () -> Unit = {},
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    when (val current = session) {
        SessionState.Loading -> SessionLoadingScreen()
        SessionState.SignedOut -> AuthNavHost()
        // A different user gets a brand-new graph.
        is SessionState.SignedIn -> key(current.user.id) {
            // Inside the key, so a different user (or signing out and in) starts a new navigation session.
            // The selected-capsule mover lives here too, so it is retained across every top-level route composition.
            CompositionLocalProvider(
                LocalNavSession provides rememberNavSession(),
                LocalNavCapsuleController provides remember { NavCapsuleController() },
            ) {
                Box {
                    ZarNavHost(openCatchUp = openCatchUp, onCatchUpOpened = onCatchUpOpened)
                    // The "Remember this choice" offer floats above every signed-in screen.
                    RuleOfferHost()
                }
            }
        }
    }
}

@Composable
private fun AuthNavHost() {
    val navController = rememberNavController()
    // Sign in -> Register is a hierarchical push and Back reverses it, by the same policy as the signed-in graph.
    val transitions = rememberZarRouteTransitions(navController)
    NavHost(
        navController = navController,
        startDestination = SignInRoute,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
        // The Back gesture previews the same rightward slide as the toolbar Back button, never the default scale-down.
        predictivePopEnterTransition = { transitions.popEnter(this) },
        predictivePopExitTransition = { transitions.popExit(this) },
    ) {
        composable<SignInRoute> {
            SignInScreen(onCreateAccount = { navController.navigate(RegisterRoute) })
        }
        composable<RegisterRoute> {
            RegisterScreen(onSignIn = { navController.popBackStack() })
        }
    }
}
