package com.example.zar.features.transactions.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.zar.core.domain.calculation.ZarTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.presentation.components.AddEntryOption
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.components.ZarLabel
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarNavy
import com.example.zar.core.presentation.theme.ZarWhite
import com.example.zar.core.presentation.theme.ZarTheme

// Layout shell for Activity: dark summary block, then the transactions list. The Transactions
// feature adds the filters and rows (use ZarActivityRow for each transaction).
@Composable
fun ActivityScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    onOpenIncome: () -> Unit,
) {
    ZarScreenScaffold(
        destination = TopLevelDestination.Activity,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd),
        ) {
            ActivityHero()
            Column(
                modifier = Modifier.padding(horizontal = ZarDimens.ScreenGutter),
                verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd),
            ) {
                TextButton(onClick = onOpenIncome, modifier = Modifier.testTag("activity-open-income")) {
                    Text(stringResource(R.string.income_open))
                }
                ActivityFilters()
                ZarLabel(ZarTime.today().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.activity_pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivityHero() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(224.dp)
            .clip(RoundedCornerShape(bottomStart = ZarDimens.HeroRadius, bottomEnd = ZarDimens.HeroRadius)),
    ) {
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF05070B), Color(0xFF0C1A2B), Color(0xFF174257)))),
        ) {
            Text(
                stringResource(R.string.nav_activity),
                style = MaterialTheme.typography.headlineLarge,
                color = ZarWhite,
                modifier = Modifier.padding(start = 22.dp, top = 22.dp),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 20.dp),
            ) {
                Text(stringResource(R.string.activity_amount_pending), style = MaterialTheme.typography.headlineLarge, color = ZarWhite)
                ZarLabel(stringResource(R.string.home_received), color = ZarWhite)
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0A070C), Color(0xFF2A1828), Color(0xFF7E3D52)))),
        ) {
            ZarLabel(
                stringResource(R.string.activity_current_month),
                color = ZarWhite,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 22.dp),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 20.dp),
            ) {
                Text(stringResource(R.string.activity_amount_pending), style = MaterialTheme.typography.headlineMedium, color = ZarWhite)
                ZarLabel(stringResource(R.string.home_spent), color = ZarWhite)
            }
        }
    }
}

@Composable
private fun ActivityFilters() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ZarDimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(99.dp), color = ZarNavy) {
            Text(
                stringResource(R.string.activity_filter_all),
                style = MaterialTheme.typography.labelLarge,
                color = ZarWhite,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
        Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
            Text(
                stringResource(R.string.activity_filter_review),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActivityScreenPreview() {
    ZarTheme { ActivityScreen(onNavigate = {}, onAddOption = {}, onOpenIncome = {}) }
}
