package com.example.zar.features.catchup.data

import com.example.zar.features.catchup.FakeCatchUpPreferences
import com.example.zar.features.catchup.FakeReminderScheduler
import com.example.zar.features.catchup.domain.model.CatchUpPreferences
import com.example.zar.features.catchup.domain.model.ReminderSettings
import com.example.zar.features.catchup.domain.usecase.ScheduleReminderUseCase
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ResetCatchUpOnSignOutTest {
    @Test
    fun signingOutForgetsTheLastCatchUpAndTheMovedReminderAndArrangesTheDefault() = runBlocking {
        val prefs = FakeCatchUpPreferences(
            CatchUpPreferences(
                reminder = ReminderSettings(day = DayOfWeek.FRIDAY, time = LocalTime.of(8, 0)),
                lastCatchUp = LocalDate.of(2026, 10, 18),
            ),
        )
        val scheduler = FakeReminderScheduler()

        ResetCatchUpOnSignOut(prefs, ScheduleReminderUseCase(prefs, scheduler)).onSignOut()

        assertNull(prefs.current.lastCatchUp)
        assertEquals(ReminderSettings(), prefs.current.reminder)
        assertNotNull(scheduler.last)
        assertEquals(DayOfWeek.SUNDAY, scheduler.last!!.dayOfWeek)
        assertEquals(LocalTime.of(19, 0), scheduler.last!!.toLocalTime())
    }
}
