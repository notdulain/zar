package com.example.zar.features.auth.presentation.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.ZarSprings

/** One bounded entrance per route instance. Text, keyboard and theme updates never restart it. */
@Composable
internal fun Modifier.authEntrance(): Modifier {
    val policy = LocalZarMotion.current
    var entered by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (entered || !policy.allowsSpatialMotion) 1f else 0f) }
    val rise = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(policy.allowsSpatialMotion) {
        entered = true
        if (policy.allowsSpatialMotion) progress.animateTo(1f, policy.spring(ZarSprings.Settle))
        else progress.snapTo(1f)
    }
    return graphicsLayer {
        translationY = if (policy.allowsSpatialMotion) (1f - progress.value) * rise else 0f
        alpha = if (policy.allowsSpatialMotion) .88f + .12f * progress.value else 1f
    }
}
