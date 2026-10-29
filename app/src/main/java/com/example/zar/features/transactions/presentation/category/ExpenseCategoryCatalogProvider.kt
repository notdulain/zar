package com.example.zar.features.transactions.presentation.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.zar.R
import com.example.zar.features.transactions.domain.category.CategoryLabel
import com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog
import com.example.zar.features.transactions.domain.category.usecase.ObserveExpenseCategoryCatalogUseCase
import com.example.zar.features.transactions.domain.manual.model.CustomCategoryIds
import com.example.zar.features.transactions.presentation.activity.EntryDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The user's expense categories for reading screens. Stateless bodies read it, so a preview or UI test gets the
 * built-ins without Hilt; a screen that owns a ViewModel wraps itself in [ProvideExpenseCategoryCatalog].
 */
internal val LocalExpenseCategoryCatalog = compositionLocalOf { ExpenseCategoryCatalog.BUILT_IN_ONLY }

@HiltViewModel
class ExpenseCategoryCatalogViewModel @Inject constructor(
    observe: ObserveExpenseCategoryCatalogUseCase,
) : ViewModel() {
    val catalog: StateFlow<ExpenseCategoryCatalog> = observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseCategoryCatalog.BUILT_IN_ONLY)
}

@Composable
fun ProvideExpenseCategoryCatalog(content: @Composable () -> Unit) {
    val viewModel: ExpenseCategoryCatalogViewModel = hiltViewModel()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalExpenseCategoryCatalog provides catalog, content = content)
}

/**
 * The readable name of a saved category ID: a built-in or income label, or a custom category's own name. Null when
 * nothing readable is known (a legacy ID, or a custom category that has not arrived yet).
 */
@Composable
internal fun categoryDisplayName(id: String?): String? {
    EntryDisplay.categoryLabel(id)?.let { return stringResource(it) }
    return (LocalExpenseCategoryCatalog.current.label(id) as? CategoryLabel.Custom)?.name
}

/** Like [categoryDisplayName] but never empty: an unresolved custom ID reads as a generic label, not its opaque ID. */
@Composable
internal fun categoryDisplayNameOrFallback(id: String): String =
    categoryDisplayName(id) ?: if (CustomCategoryIds.isCustom(id)) stringResource(R.string.category_unavailable) else id
