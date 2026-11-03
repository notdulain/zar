package com.example.zar.features.plans.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zar.R
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.components.HeroScrollOffset
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.heroParallax
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.presentation.privacy.AmountMask
import com.example.zar.core.presentation.theme.GeistFamily
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.core.presentation.theme.ZarWhite
import com.example.zar.features.plans.domain.model.BackedGoalProgress
import com.example.zar.features.plans.domain.model.PlansOverview
import com.example.zar.features.plans.domain.model.SavingsGoal
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Plans hero from Figma Z04 (`160:2421`), exactly. A 420dp box drawn under the status bar with 32dp bottom corners,
 * and one vertical gradient from y = -9.82dp to y = 481dp of the hero with stops 0 / 0.10 / 0.70 / 1 (the navy mid-stop is
 * at 70%, so the text over the coral edge keeps its contrast). The box grows rather than clipping when text is larger.
 */
object PlansHeroSpec {
    val Height = 420.dp
    const val GRADIENT_START_DP = -9.82f
    const val GRADIENT_END_DP = 481f
    val StopFractions = floatArrayOf(0f, 0.10f, 0.70f, 1f)
    val StopColors = listOf(Color(0xFF05070B), Color(0xFF0B1524), Color(0xFF14304A), Color(0xFFF3A78F))
}

private val HeroTitle = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.64).sp)
private val HeroGoalName = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Light, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.56).sp)
private val HeroAmount = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-0.8).sp)
private val HeroBody15 = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp)
private val HeroBody14 = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
private val HeroMedium15 = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)

@Composable
internal fun PlansHeroBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = PlansHeroSpec.Height)
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        colorStops = PlansHeroSpec.StopFractions.zip(PlansHeroSpec.StopColors).toTypedArray(),
                        startY = PlansHeroSpec.GRADIENT_START_DP.dp.toPx(),
                        endY = PlansHeroSpec.GRADIENT_END_DP.dp.toPx(),
                    ),
                )
            }
            .padding(start = 22.dp, end = 22.dp, top = 62.dp, bottom = 28.dp),
        content = content,
    )
}

/** "Plans" on the left, the "+ New plan" pill on the right, vertically centred on the title. */
@Composable
internal fun PlansHeroHeader(onNewPlan: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.nav_plans), style = HeroTitle, color = ZarWhite)
        // The pill is 36dp tall; the touch target is 48dp.
        Box(
            Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onNewPlan).testTag("plans-new-plan"),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(ZarWhite.copy(alpha = 0.14f)).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZarIcon(R.drawable.ic_ph_plus_small, size = 16.dp, tint = ZarWhite)
                Text(stringResource(R.string.plans_new_plan), style = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp), color = ZarWhite)
            }
        }
    }
}

@Composable
internal fun GoalHero(
    overview: PlansOverview,
    onNewPlan: () -> Unit,
    onEditGoal: () -> Unit,
    onAllocate: () -> Unit,
    heroOffset: HeroScrollOffset,
    onChangeMain: () -> Unit = {},
    onViewGoal: () -> Unit = {},
) {
    val goal = overview.goal
    val progress = overview.progress
    val textLayer = Modifier.heroParallax(heroOffset, ZarMotion.PARALLAX_TEXT, ZarMotion.ParallaxTextCap)
    PlansHeroBox(Modifier.testTag("plans-hero")) {
        Column(textLayer) {
            PlansHeroHeader(onNewPlan)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(ZarWhite.copy(alpha = 0.14f)), Alignment.Center) {
                    ZarIcon(R.drawable.ic_ph_piggy_bank, size = 20.dp, tint = ZarWhite)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(goal?.name ?: stringResource(R.string.plans_goal_name), style = HeroGoalName, color = ZarWhite, maxLines = 2)
                    if (goal != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.plans_main_goal), style = HeroBody14, color = ZarWhite.copy(alpha = 0.75f))
                            Box(
                                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onChangeMain)
                                    .padding(horizontal = 10.dp).testTag("plans-change-main"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.plans_change),
                                    style = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
                                    color = ZarWhite,
                                )
                            }
                        }
                    } else {
                        Text(stringResource(R.string.plans_not_created_yet), style = HeroBody14, color = ZarWhite.copy(alpha = 0.75f))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                progress?.backed?.formatWithCode() ?: FiatMoney(0, Currencies.LKR).formatWithCode(),
                style = HeroAmount, color = ZarWhite, modifier = Modifier.testTag("plans-backed"), maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                goal?.let { goalTarget(it) } ?: stringResource(R.string.plans_goal_not_created),
                style = HeroBody15, color = ZarWhite.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(22.dp))
            if (!AmountMask.isHidden) {
                // With no goal the track is drawn empty, as Figma Z196 does.
                HeroTrack(if (goal != null && progress != null) progress.backedFraction(goal.target) else 0f)
            } else {
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    goal == null -> stringResource(R.string.plans_create_note)
                    goal.allocations.isEmpty() -> stringResource(R.string.plans_nothing_allocated)
                    progress?.hasUnknownBalance == true -> stringResource(R.string.plans_unknown_backing)
                    progress?.isApproximate == true -> stringResource(R.string.plans_estimated_backing)
                    else -> stringResource(R.string.plans_recorded_backing)
                },
                style = HeroBody14, color = ZarWhite.copy(alpha = 0.85f),
            )
        }
        Spacer(Modifier.height(24.dp))
        val enabled = goal == null || overview.allocationAccounts.isNotEmpty()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (goal != null) {
                Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onViewGoal).testTag("plans-view-goal"), contentAlignment = Alignment.CenterStart) {
                    Text(stringResource(R.string.plans_view_goal) + " →", style = HeroMedium15, color = ZarWhite)
                }
            }
            Box(
                Modifier.heightIn(min = 48.dp)
                    .clickable(enabled = enabled, role = Role.Button) { if (goal == null) onEditGoal() else onAllocate() }
                    .testTag("plans-set-aside"),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    stringResource(if (goal == null) R.string.plans_create_action else R.string.plans_allocate_action) + " →",
                    style = HeroMedium15,
                    color = ZarWhite.copy(alpha = if (enabled) 1f else 0.4f),
                )
            }
        }
        if (goal != null && overview.allocationAccounts.isEmpty()) {
            Text(stringResource(R.string.plans_no_accounts), style = ZarType.bodyS, color = ZarWhite.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun goalTarget(goal: SavingsGoal): String {
    val of = stringResource(R.string.plans_of_target, goal.target.formatWithCode())
    val date = goal.targetDate ?: return of
    return of + " · " + stringResource(R.string.plans_by_date, date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())))
}

/** 8dp track, white at 35%, filled white. */
@Composable
private fun HeroTrack(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(ZarWhite.copy(alpha = 0.35f)).testTag("plans-backed-progress")) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(ZarWhite))
    }
}

@Composable
internal fun LoadingHero(heroOffset: HeroScrollOffset, onNewPlan: () -> Unit) = PlansHeroBox {
    PlansHeroHeader(onNewPlan)
    Spacer(Modifier.height(48.dp))
    CircularProgressIndicator(color = ZarWhite, modifier = Modifier.align(Alignment.CenterHorizontally))
}

@Composable
internal fun ErrorHero(onRetry: () -> Unit, heroOffset: HeroScrollOffset, onNewPlan: () -> Unit) = PlansHeroBox {
    PlansHeroHeader(onNewPlan)
    Spacer(Modifier.height(32.dp))
    Text(stringResource(R.string.plans_load_failed), style = ZarType.bodyL, color = ZarWhite)
    Spacer(Modifier.height(12.dp))
    ZarButton(stringResource(R.string.action_retry), onRetry, style = ZarButtonStyle.Soft)
}
