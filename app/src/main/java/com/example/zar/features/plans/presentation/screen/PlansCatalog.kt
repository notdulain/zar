package com.example.zar.features.plans.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.components.ZarModalSheet
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.core.presentation.privacy.AmountMask
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.plans.domain.model.BackedGoalProgress
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.PlansOverview
import com.example.zar.features.plans.domain.model.SavingsGoal
import com.example.zar.features.transactions.presentation.activity.screen.SheetHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How many other plans sit beside the main goal on the Plans page before "See all" takes over. */
internal const val PLAN_CARDS_SHOWN = 2

/** The plans other than the main goal, in catalog order. */
internal fun PlansOverview.otherGoals(): List<SavingsGoal> = goals.filter { it.id != goal?.id }

/** "YOUR PLANS" with See all, and up to [PLAN_CARDS_SHOWN] other plans side by side (Figma Z04). Empty without other plans. */
@Composable
internal fun YourPlansSection(overview: PlansOverview, onSeeAll: () -> Unit, onOpen: (GoalId) -> Unit) {
    val others = overview.otherGoals()
    if (others.isEmpty()) return
    val colors = ZarTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd), modifier = Modifier.testTag("plans-your-plans")) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ZarEyebrow(stringResource(R.string.plans_your_plans))
            Box(
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onSeeAll).padding(horizontal = 12.dp).testTag("plans-see-all"),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.plans_see_all) + " →", style = ZarType.labelChip, color = colors.textSelected) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd)) {
            others.take(PLAN_CARDS_SHOWN).forEach { goal ->
                PlanCard(goal, overview.progressFor(goal.id), { onOpen(goal.id) }, Modifier.weight(1f))
            }
            if (others.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlanCard(goal: SavingsGoal, progress: BackedGoalProgress?, onClick: () -> Unit, modifier: Modifier) {
    val colors = ZarTheme.colors
    val open = stringResource(R.string.plans_open_plan, goal.name)
    ZarCard(modifier.semantics { contentDescription = open }.testTag("plan-card-${goal.id.value}"), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(goal.name, style = ZarType.bodyL.copy(lineHeight = 20.sp), color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(deadlineText(goal.targetDate), style = ZarType.bodyS, color = colors.textSecondary)
            Spacer(Modifier.height(4.dp))
            if (progress == null || progress.hasUnknownBalance) {
                Text(stringResource(R.string.plans_card_unknown_backing), style = ZarType.bodyS, color = colors.textSecondary)
            } else {
                Text(stringResource(R.string.plans_card_set_aside, progress.backed.formatWithCode()), style = ZarType.bodyM, color = colors.textPrimary)
                if (!AmountMask.isHidden) {
                    MiniTrack(progress.backedFraction(goal.target))
                    val percent = (progress.backedFraction(goal.target) * 100).toInt()
                    Text(stringResource(R.string.plans_card_percent, percent, goal.target.formatWithCode()), style = ZarType.bodyCaption, color = colors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun MiniTrack(fraction: Float) {
    val colors = ZarTheme.colors
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.borderDefault)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.accentSelected))
    }
}

@Composable
private fun deadlineText(date: LocalDate?): String =
    date?.let { stringResource(R.string.plans_by_month, it.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))) }
        ?: stringResource(R.string.plans_no_deadline)

/**
 * Every plan, with the main goal marked. Selecting a row makes it the main goal and moves only the pointer; the row's
 * chevron-less tap target for details is the name area of the card on the page, so here a row is one action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AllPlansSheet(
    overview: PlansOverview,
    isSaving: Boolean,
    onSelectMain: (GoalId) -> Unit,
    onOpen: (GoalId) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ZarTheme.colors
    ZarModalSheet(onDismissRequest = onDismiss) { dismiss ->
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = ZarDimens.SheetGutter).padding(bottom = 20.dp).testTag("all-plans-sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(stringResource(R.string.plans_all_plans_title), onClose = dismiss)
            Text(stringResource(R.string.plans_choose_main_hint), style = ZarType.bodyS, color = colors.textSecondary)
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                overview.goals.forEach { goal ->
                    val isMain = goal.id == overview.goal?.id
                    val progress = overview.progressFor(goal.id)
                    val mainLabel = stringResource(R.string.plans_selected_main, goal.name)
                    Row(
                        Modifier.fillMaxWidth().clip(ZarShapes.md)
                            .background(if (isMain) colors.selectedTint else colors.subtle)
                            .selectable(selected = isMain, enabled = !isSaving, role = Role.RadioButton) { onSelectMain(goal.id) }
                            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                            .semantics { if (isMain) contentDescription = mainLabel }
                            .testTag("all-plans-row-${goal.id.value}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isMain, onClick = null, enabled = !isSaving, colors = RadioButtonDefaults.colors(selectedColor = colors.textSelected))
                        Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(goal.name, style = ZarType.bodyM, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (progress == null || progress.hasUnknownBalance) stringResource(R.string.plans_card_unknown_backing)
                                else stringResource(R.string.plans_card_set_aside, progress.backed.formatWithCode()),
                                style = ZarType.bodyS, color = colors.textSecondary,
                            )
                        }
                        if (isMain) Text(stringResource(R.string.plans_main_badge), style = ZarType.labelChip, color = colors.textSelected, modifier = Modifier.padding(horizontal = 8.dp))
                        Box(
                            Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { onOpen(goal.id) }.padding(horizontal = 12.dp)
                                .testTag("all-plans-open-${goal.id.value}"),
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(R.string.plans_view_goal), style = ZarType.labelChip, color = colors.textSelected) }
                    }
                }
            }
        }
    }
}

/** One plan's progress with its actions: set aside money, edit, and make it the main goal. Addressed by [goal]'s ID. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanDetailSheet(
    goal: SavingsGoal,
    progress: BackedGoalProgress?,
    isMain: Boolean,
    canAllocate: Boolean,
    isSaving: Boolean,
    onSetAside: () -> Unit,
    onEdit: () -> Unit,
    onMakeMain: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ZarTheme.colors
    ZarModalSheet(onDismissRequest = onDismiss) { dismiss ->
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = ZarDimens.SheetGutter).padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState()).testTag("plan-detail-sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(goal.name, onClose = dismiss)
            Text(
                stringResource(R.string.plans_of_target, goal.target.formatWithCode()) + " · " + deadlineText(goal.targetDate),
                style = ZarType.bodyS, color = colors.textSecondary,
            )
            if (progress != null) GoalProgressDetails(goal, progress)
            ZarButton(stringResource(R.string.plans_allocate_action), onSetAside, Modifier.fillMaxWidth().testTag("plan-detail-set-aside"), enabled = canAllocate && !isSaving)
            ZarButton(stringResource(R.string.plans_edit_plan), onEdit, Modifier.fillMaxWidth().testTag("plan-detail-edit"), style = ZarButtonStyle.Bounded, enabled = !isSaving)
            if (!isMain) ZarButton(stringResource(R.string.plans_make_main), onMakeMain, Modifier.fillMaxWidth().testTag("plan-detail-make-main"), style = ZarButtonStyle.Bounded, enabled = !isSaving)
        }
    }
}
