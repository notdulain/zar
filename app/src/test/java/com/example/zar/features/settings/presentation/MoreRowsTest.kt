package com.example.zar.features.settings.presentation

import com.example.zar.R
import com.example.zar.features.settings.presentation.screen.moreRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MoreRowsTest {

    private val opened = mutableListOf<String>()

    private val rows = moreRows(
        onOpenAccounts = { opened += "accounts" },
        onOpenIncome = { opened += "income" },
        onOpenFreelance = { opened += "freelance" },
        onOpenReminder = { opened += "reminder" },
        onOpenSettings = { opened += "settings" },
    )

    @Test
    fun rowsFollowFigmaOrder() {
        assertEquals(
            listOf(
                R.string.more_accounts,
                R.string.income_open,
                R.string.freelance_open,
                R.string.more_reminder,
                R.string.settings_title,
            ),
            rows.map { it.title },
        )
        assertEquals(
            listOf("open-accounts", "open-income", "open-freelance", "open-reminder", "open-settings"),
            rows.map { it.tag },
        )
    }

    @Test
    fun rowsShowOnlyFeaturesThatExist() {
        val titles = rows.map { it.title }
        assertFalse(R.string.more_privacy in titles)
        assertFalse(R.string.more_export in titles)
        assertFalse(R.string.more_capture in titles)
    }

    @Test
    fun eachRowOpensItsDestination() {
        rows.forEach { it.onClick() }

        assertEquals(listOf("accounts", "income", "freelance", "reminder", "settings"), opened)
    }
}
