package com.example.zar.features.transactions.presentation.income.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.presentation.theme.ZarWhite
import com.example.zar.features.transactions.domain.income.model.IncomeCategory

/** Wireframe style: monospace labels and amounts. */
val ZarMono = FontFamily.Monospace

@StringRes
fun IncomeCategory.labelRes(): Int = when (this) {
    IncomeCategory.SALARY -> R.string.income_category_salary
    IncomeCategory.FREELANCE -> R.string.income_category_freelance
    IncomeCategory.ALLOWANCE -> R.string.income_category_allowance
    IncomeCategory.GIFT -> R.string.income_category_gift
    IncomeCategory.OTHER -> R.string.income_category_other
}

/** The dark summary card at the top of Activity-style screens. */
@Composable
fun SummaryHeaderCard(
    label: String,
    amount: String,
    caption: String,
    modifier: Modifier = Modifier,
    /** Paints the card with a gradient instead of the flat inverse surface (the Crypto hero). */
    brush: Brush? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = if (brush == null) MaterialTheme.colorScheme.inverseSurface else Color.Transparent,
        contentColor = if (brush == null) MaterialTheme.colorScheme.inverseOnSurface else ZarWhite,
    ) {
        Column(modifier = (if (brush != null) Modifier.background(brush) else Modifier).padding(horizontal = 24.dp, vertical = 28.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontFamily = ZarMono)
            Spacer(Modifier.height(12.dp))
            Text(amount, style = MaterialTheme.typography.headlineLarge, fontFamily = ZarMono)
            Spacer(Modifier.height(8.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, fontFamily = ZarMono)
        }
    }
}
