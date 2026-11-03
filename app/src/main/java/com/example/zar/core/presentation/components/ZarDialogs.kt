package com.example.zar.core.presentation.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.zar.R
import com.example.zar.core.presentation.motion.LocalZarMotion

/**
 * Sets the exit animation of the enclosing dialog window: the platform's default zooms and fades the dialog out, which
 * Back must not do. With full motion the dialog leaves toward the physical right (see `Theme.Zar.Dialog`, which is the
 * same style); with motion reduced or off it simply disappears. Call it first inside any dialog window's content.
 */
@Composable
fun ZarDialogWindowMotion() {
    val spatial = LocalZarMotion.current.allowsSpatialMotion
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.setWindowAnimations(if (spatial) R.style.ZarDialogWindowAnimation else R.style.ZarDialogWindowAnimationNone)
    }
}

/** Material [AlertDialog] whose Back, Cancel and outside-tap dismissal leaves rightward with no fade or zoom. */
@Composable
fun ZarAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    containerColor: Color = AlertDialogDefaults.containerColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { ZarDialogWindowMotion(); confirmButton() },
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        containerColor = containerColor,
        titleContentColor = titleContentColor,
    )
}

/** Material [DatePickerDialog] with the same rightward Back as every other app-owned window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZarDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    tonalElevation: Dp = DatePickerDefaults.TonalElevation,
    colors: DatePickerColors = DatePickerDefaults.colors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { ZarDialogWindowMotion(); confirmButton() },
        modifier = modifier,
        dismissButton = dismissButton,
        tonalElevation = tonalElevation,
        colors = colors,
        content = content,
    )
}

/** Compose [Dialog] with the same rightward Back as every other app-owned window. */
@Composable
fun ZarDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        ZarDialogWindowMotion()
        content()
    }
}
