package com.example.zar.core.presentation.theme

import androidx.compose.ui.graphics.Color
import com.example.zar.core.domain.model.TransactionType

/**
 * The colour of an amount's text, chosen from what the entry is, not from its direction alone.
 *
 * Only real income uses [ZarColors.textIncome]. A transfer in, a refund or an adjustment is also a credit and keeps its
 * "+" sign, but it is not income, so it stays in the ordinary text colour rather than borrowing income's. Freshness fills
 * ([ZarColors.fresh]) are status indicators and must never colour text: the pale green is 1.53:1 on the light canvas.
 */
fun ZarColors.amountText(type: TransactionType): Color =
    if (type == TransactionType.INCOME) textIncome else textPrimary
