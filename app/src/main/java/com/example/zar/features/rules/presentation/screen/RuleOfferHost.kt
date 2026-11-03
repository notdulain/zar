package com.example.zar.features.rules.presentation.screen

import com.example.zar.features.transactions.presentation.category.ProvideExpenseCategoryCatalog
import com.example.zar.features.transactions.presentation.category.categoryDisplayNameOrFallback
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.catchup.presentation.screen.KeyValueRow
import com.example.zar.features.rules.domain.model.RuleOffer
import com.example.zar.features.rules.domain.model.RuleScope
import com.example.zar.features.rules.presentation.viewmodel.RuleOfferUiState
import com.example.zar.features.rules.presentation.viewmodel.RuleOfferViewModel
import com.example.zar.features.transactions.presentation.activity.EntryDisplay
import com.example.zar.features.transactions.presentation.activity.screen.SheetGrabber
import com.example.zar.features.transactions.presentation.activity.screen.SheetHeader
import com.example.zar.features.transactions.presentation.activity.screen.SheetScrim
import kotlinx.coroutines.delay

private const val CARD_SECONDS = 12L

/**
 * Sits above every screen of the signed-in app. After a saved expense or a correction it shows the small
 * "Remember" card (Figma Z116); tapping it opens the confirm sheet (Z115, Z123). Nothing is saved until Remember.
 */
@Composable
fun RuleOfferHost(modifier: Modifier = Modifier, viewModel: RuleOfferViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProvideExpenseCategoryCatalog {
        RuleOfferContent(
            state = state,
            onOpen = viewModel::openSheet,
            onDismiss = viewModel::dismiss,
            onRemember = viewModel::remember,
            modifier = modifier,
        )
    }
}

@Composable
internal fun RuleOfferContent(
    state: RuleOfferUiState,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onRemember: (RuleScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val offer = state.offer ?: return
    if (state.sheetOpen) {
        RememberSheet(state, offer, onDismiss, onRemember)
    } else {
        // The card goes away by itself; nothing is lost, the entry's own page offers it again.
        LaunchedEffect(offer) {
            delay(CARD_SECONDS * 1_000)
            onDismiss()
        }
        Box(modifier.fillMaxSize().padding(bottom = 104.dp), Alignment.BottomCenter) {
            OfferCard(offer, onOpen, onDismiss)
        }
    }
}

@Composable
private fun OfferCard(offer: RuleOffer, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val colors = ZarTheme.colors
    val category = categoryName(offer.categoryId)
    Row(
        Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .clip(ZarShapes.lg)
            .background(colors.surface)
            .border(ZarDimens.Hairline, colors.borderDefault, ZarShapes.lg)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
            .testTag("rule-offer-card"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.rules_offer_title, offer.displayName), style = ZarType.labelAction, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.rules_offer_body, category), style = ZarType.bodyS, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            stringResource(R.string.rules_offer_remember),
            style = ZarType.labelAction,
            color = colors.textSelected,
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 8.dp, vertical = 12.dp).testTag("rule-offer-remember"),
        )
        Box(
            Modifier.size(40.dp).clickable(role = Role.Button, onClick = onDismiss).testTag("rule-offer-dismiss"),
            Alignment.Center,
        ) { ZarIcon(R.drawable.ic_ph_x, size = 16.dp, tint = colors.iconMuted) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RememberSheet(state: RuleOfferUiState, offer: RuleOffer, onDismiss: () -> Unit, onRemember: (RuleScope) -> Unit) {
    val colors = ZarTheme.colors
    val category = categoryName(offer.categoryId)
    val accountName = state.accountName
    var scope by remember { mutableStateOf(RuleScope.ANY_ACCOUNT) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ZarShapes.sheet,
        containerColor = colors.surface,
        scrimColor = SheetScrim,
        dragHandle = { SheetGrabber() },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZarDimens.SheetGutter)
                .padding(bottom = 14.dp)
                .testTag("rule-sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(stringResource(R.string.rules_sheet_title), onClose = onDismiss)
            Row(
                Modifier.fillMaxWidth().clip(ZarShapes.lg).background(colors.subtle).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZarIconChip(R.drawable.ic_ph_receipt, background = colors.surface)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(offer.displayName, style = ZarType.bodyM, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.rules_sheet_entry_detail, category), style = ZarType.bodyS, color = colors.textSecondary)
                }
            }
            if (accountName != null) {
                Column(Modifier.fillMaxWidth().clip(ZarShapes.lg).background(colors.subtle)) {
                    ScopeRow(
                        selected = scope == RuleScope.ANY_ACCOUNT,
                        title = stringResource(R.string.rules_scope_any),
                        detail = stringResource(R.string.rules_scope_any_detail, offer.displayName),
                        onClick = { scope = RuleScope.ANY_ACCOUNT },
                        tag = "rule-scope-any",
                    )
                    ZarDivider()
                    ScopeRow(
                        selected = scope == RuleScope.THIS_ACCOUNT,
                        title = stringResource(R.string.rules_scope_account, accountName),
                        detail = stringResource(R.string.rules_scope_account_detail),
                        onClick = { scope = RuleScope.THIS_ACCOUNT },
                        tag = "rule-scope-account",
                    )
                }
            }
            ZarEyebrow(stringResource(R.string.rules_sheet_the_rule))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyValueRow(stringResource(R.string.rules_matches), stringResource(R.string.rules_matches_value, offer.displayName))
                KeyValueRow(stringResource(R.string.rules_sets), stringResource(R.string.rules_sets_value, category))
            }
            val note = when {
                state.failed -> R.string.rules_sheet_failed
                !state.isOnline -> R.string.rules_sheet_note_offline
                else -> R.string.rules_sheet_note
            }
            Text(
                stringResource(note),
                style = ZarType.bodyS,
                color = if (state.failed) colors.danger else colors.textSecondary,
                modifier = Modifier.testTag("rule-sheet-note"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZarButton(stringResource(R.string.rules_not_now), onDismiss, Modifier.weight(1f).testTag("rule-sheet-not-now"), style = ZarButtonStyle.Soft)
                ZarButton(
                    stringResource(R.string.rules_remember),
                    { onRemember(if (accountName == null) RuleScope.ANY_ACCOUNT else scope) },
                    Modifier.weight(1f).testTag("rule-sheet-remember"),
                    enabled = !state.isSaving,
                )
            }
        }
    }
}

@Composable
private fun ScopeRow(selected: Boolean, title: String, detail: String, onClick: () -> Unit, tag: String) {
    val colors = ZarTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) colors.selectedTint else colors.subtle)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ZarType.bodyM, color = colors.textPrimary)
            Text(detail, style = ZarType.bodyCaption, color = colors.textSecondary)
        }
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) colors.accentPrimary else colors.surface)
                .then(if (selected) Modifier else Modifier.border(BorderStroke(1.dp, colors.borderDefault), CircleShape)),
            Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(colors.surface))
        }
    }
}

/** The label of a saved category, or the raw ID for one this app does not know. */
@Composable
internal fun categoryName(id: String): String = categoryDisplayNameOrFallback(id)
