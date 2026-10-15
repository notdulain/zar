package com.example.zar.features.freelance.presentation.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.freelance.domain.FreelanceProblem
import com.example.zar.features.freelance.presentation.viewmodel.FreelanceMessage
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.income.screen.ZarMono
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal fun Money.display(): String = IncomeFormat.withCode(this)

internal fun List<Money>.display(): String = joinToString(" + ") { it.display() }

@StringRes
internal fun FreelanceMessage.textRes(): Int = when (this) {
    FreelanceMessage.NOT_SIGNED_IN -> R.string.freelance_message_not_signed_in
    FreelanceMessage.ARCHIVED -> R.string.freelance_message_archived
    FreelanceMessage.CANCELLED -> R.string.freelance_message_cancelled
    FreelanceMessage.NOT_FOUND -> R.string.project_not_found
    FreelanceMessage.FAILED -> R.string.freelance_message_failed
}

/** The first problem worth showing under an amount field. */
@StringRes
internal fun Set<FreelanceProblem>.amountErrorRes(): Int? = when {
    FreelanceProblem.AMOUNT_MISSING in this -> R.string.freelance_error_amount_missing
    FreelanceProblem.AMOUNT_INVALID in this -> R.string.freelance_error_amount_invalid
    FreelanceProblem.MORE_THAN_RECEIPT in this -> R.string.freelance_error_more_than_receipt
    FreelanceProblem.MORE_THAN_REMAINING in this -> R.string.freelance_error_more_than_remaining
    else -> null
}

@StringRes
internal fun Set<FreelanceProblem>.nameErrorRes(): Int? = when {
    FreelanceProblem.NAME_MISSING in this -> R.string.freelance_error_name_missing
    FreelanceProblem.NAME_TOO_LONG in this -> R.string.income_error_too_long
    else -> null
}

@Composable
internal fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
}

@Composable
internal fun CenteredText(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, fontFamily = ZarMono)
    }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontFamily = ZarMono, modifier = modifier)
}

/** A date button that opens Material's picker. Any date can be picked (milestones may be overdue). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(label: String, date: LocalDate, enabled: Boolean, onSelect: (LocalDate) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(label)
        OutlinedButton(onClick = { showPicker = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(IncomeFormat.fullDate(date), fontFamily = ZarMono)
        }
    }
    if (!showPicker) return
    val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = { showPicker = false },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                showPicker = false
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}
