package com.example.zar.features.plans.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.components.sheetBackScrim
import com.example.zar.core.presentation.components.sheetBackSlide
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.core.presentation.privacy.amountFieldTransformation
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.plans.presentation.viewmodel.PlansMessage
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The kinds of plan Zar can create today, each backed by a real flow. Nothing here is a placeholder. */
enum class NewPlanType { Goal, Limit, Bill, Project }

/** Months from [today] to [by], at least one, for "each month to reach it". */
internal fun monthsToReach(today: LocalDate, by: LocalDate): Long = ChronoUnit.MONTHS.between(today, by).coerceAtLeast(1)

/**
 * The Z21 "New plan" sheet. The type chips map to the flows that exist: a savings goal (any number can coexist; each save
 * creates its own plan and never touches another), the weekly limit, a bill and a freelance project. [onDismiss] slides the
 * sheet right (see `SheetBackHost`); the other callbacks close it and open the real editor or form. A failed save keeps the
 * form as typed and shows [message]; retrying reuses the same plan ID.
 */
@Composable
fun NewPlanSheet(
    hasWeeklyLimit: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onCreateGoal: (name: String, target: String, date: LocalDate?) -> Unit,
    onSetLimit: () -> Unit,
    onAddBill: () -> Unit,
    onAddProject: () -> Unit,
    message: PlansMessage? = null,
) {
    val colors = ZarTheme.colors
    var type by rememberSaveable { mutableStateOf(NewPlanType.Goal) }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.fillMaxSize().sheetBackScrim().background(colors.scrim.copy(alpha = 0.64f)).clickable(onClick = onDismiss))
        Column(
            Modifier.align(Alignment.BottomCenter).sheetBackSlide().fillMaxWidth().heightIn(max = maxHeight - 48.dp)
                .clip(ZarShapes.sheet).background(colors.surface).pointerInput(Unit) { detectTapGestures {} }
                .navigationBarsPadding().padding(top = 10.dp).testTag("new-plan-sheet"),
        ) {
            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(colors.handle))
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 2.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.new_plan_title), style = ZarType.headingSheet, color = colors.textPrimary)
                val close = stringResource(R.string.new_plan_close)
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = close, onClick = onDismiss)
                        .semantics { contentDescription = close }.testTag("new-plan-close"),
                    Alignment.Center,
                ) { ZarIcon(R.drawable.ic_ph_x, size = 20.dp) }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ZarEyebrow(stringResource(R.string.new_plan_type))
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ZarChip(stringResource(R.string.new_plan_type_goal), type == NewPlanType.Goal, { type = NewPlanType.Goal }, Modifier.testTag("new-plan-type-goal"))
                        ZarChip(stringResource(R.string.new_plan_type_limit), type == NewPlanType.Limit, { type = NewPlanType.Limit }, Modifier.testTag("new-plan-type-limit"))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ZarChip(stringResource(R.string.new_plan_type_bill), type == NewPlanType.Bill, { type = NewPlanType.Bill }, Modifier.testTag("new-plan-type-bill"))
                        ZarChip(stringResource(R.string.new_plan_type_project), type == NewPlanType.Project, { type = NewPlanType.Project }, Modifier.testTag("new-plan-type-project"))
                    }
                }
                when (type) {
                    NewPlanType.Goal -> GoalForm(isSaving, message, onDismiss, onCreateGoal)
                    NewPlanType.Limit -> PlanExplainer(
                        title = stringResource(if (hasWeeklyLimit) R.string.new_plan_limit_title_set else R.string.new_plan_limit_title),
                        body = stringResource(R.string.new_plan_limit_body),
                        action = stringResource(if (hasWeeklyLimit) R.string.new_plan_limit_edit else R.string.new_plan_limit_set),
                        tag = "new-plan-set-limit",
                        onAction = onSetLimit,
                    )
                    NewPlanType.Bill -> PlanExplainer(
                        title = stringResource(R.string.new_plan_bill_title),
                        body = stringResource(R.string.new_plan_bill_body),
                        action = stringResource(R.string.new_plan_bill_action),
                        tag = "new-plan-add-bill",
                        onAction = onAddBill,
                    )
                    NewPlanType.Project -> PlanExplainer(
                        title = stringResource(R.string.new_plan_project_title),
                        body = stringResource(R.string.new_plan_project_body),
                        action = stringResource(R.string.new_plan_project_action),
                        tag = "new-plan-add-project",
                        onAction = onAddProject,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanExplainer(title: String, body: String, action: String, tag: String, onAction: () -> Unit) {
    val colors = ZarTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = ZarType.bodyL.copy(lineHeight = 22.sp), color = colors.textPrimary)
        Text(body, style = ZarType.bodyS.copy(lineHeight = 20.sp), color = colors.textSecondary)
    }
    ZarButton(action, onAction, Modifier.fillMaxWidth().testTag(tag))
}

@Composable
private fun GoalForm(isSaving: Boolean, message: PlansMessage?, onCancel: () -> Unit, onCreate: (String, String, LocalDate?) -> Unit) {
    val colors = ZarTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val targetMoney = FiatMoney.parse(target, Currencies.LKR)
    val valid = name.isNotBlank() && targetMoney != null && targetMoney.minorUnits > 0
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.textSelected, focusedLabelColor = colors.textSelected, cursorColor = colors.textSelected,
    )
    OutlinedTextField(
        name, { name = it }, Modifier.fillMaxWidth().testTag("new-plan-name"),
        label = { Text(stringResource(R.string.new_plan_name)) }, singleLine = true, shape = ZarShapes.md, colors = fieldColors, enabled = !isSaving,
    )
    OutlinedTextField(
        target, { target = it }, Modifier.fillMaxWidth().testTag("new-plan-target"),
        label = { Text(stringResource(R.string.new_plan_target)) }, prefix = { Text("LKR ") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
        visualTransformation = amountFieldTransformation(), shape = ZarShapes.md, colors = fieldColors, enabled = !isSaving,
    )
    GoalDateField(date, { date = it }, enabled = !isSaving)
    val chosen = date
    val perMonth = if (valid && chosen != null) {
        FiatMoney(targetMoney!!.minorUnits / monthsToReach(ZarTime.today(), chosen), Currencies.LKR).formatWithCode()
    } else {
        "—"
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.new_plan_per_month), style = ZarType.bodyS, color = colors.textSecondary)
            Text(perMonth, style = ZarType.bodyS, color = colors.textPrimary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.new_plan_reserved), style = ZarType.bodyS, color = colors.textSecondary)
            Text(stringResource(R.string.new_plan_nothing), style = ZarType.bodyS, color = colors.textPrimary)
        }
    }
    if (message != null) Box(Modifier.testTag("new-plan-error")) { DialogError(message) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ZarButton(stringResource(R.string.action_cancel), onCancel, Modifier.weight(1f).testTag("new-plan-cancel"), style = ZarButtonStyle.Soft, enabled = !isSaving)
        ZarButton(
            stringResource(R.string.new_plan_create), { onCreate(name.trim(), target, date) },
            Modifier.weight(1f).testTag("new-plan-create"), enabled = valid && !isSaving,
        )
    }
}
