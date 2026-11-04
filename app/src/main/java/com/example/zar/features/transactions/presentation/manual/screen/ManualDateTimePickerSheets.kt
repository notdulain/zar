package com.example.zar.features.transactions.presentation.manual.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.haptics.rememberSelectionTicker
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.ZarSprings
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Figma Z08 picker. The optional set draws the Figma entry dots only for real entries. */
@Composable
fun ManualDatePickerSheet(
    date: LocalDate,
    onCancel: () -> Unit,
    onSetDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    datesWithEntries: Set<LocalDate> = emptySet(),
) {
    val locale = LocalConfiguration.current.locales[0]
    val today = remember { ZarTime.today() }
    var selected by remember(date) { mutableStateOf(date.coerceAtMost(today)) }
    var month by remember(date) { mutableStateOf(YearMonth.from(selected)) }
    var choosingMonth by remember { mutableStateOf(false) }
    val colors = ZarTheme.colors
    val currentMonth = YearMonth.from(today)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Swiping the calendar changes the month; the next month stays off past today, as the arrow does.
    fun step(delta: Long) {
        val target = month.plusMonths(delta)
        if (target <= currentMonth) month = target
    }
    PickerShell(title = "Date", onCancel = onCancel, action = "Set date", onAction = { onSetDate(selected) }, modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today" to today, "Yesterday" to today.minusDays(1), "2 days ago" to today.minusDays(2)).forEach { (label, value) ->
                ZarChip(label = label, selected = selected == value, onClick = {
                    selected = value
                    month = YearMonth.from(value)
                })
            }
        }

        Column(
            Modifier.testTag("date-calendar").pointerInput(rtl, currentMonth) { detectMonthSwipes(rtl) { delta -> step(delta) } },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(ZarShapes.sm)
                        .clickable(role = Role.Button, onClickLabel = "Choose month") { choosingMonth = !choosingMonth }
                        .semantics { stateDescription = if (choosingMonth) "Choosing month" else "Showing days" }
                        .testTag("date-month-title"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)), style = ZarType.bodyL.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
                    ZarIcon(R.drawable.ic_ph_caret_down, modifier = Modifier.rotate(if (choosingMonth) 180f else 0f), size = 14.dp, tint = colors.iconMuted)
                }
                if (!choosingMonth) {
                    MonthArrow("Previous month", enabled = true, rotated = true) { month = month.minusMonths(1) }
                    Spacer(Modifier.width(8.dp))
                    MonthArrow("Next month", enabled = month < currentMonth) { month = month.plusMonths(1) }
                }
            }
            if (choosingMonth) {
                MonthChooser(
                    selected = month,
                    latest = currentMonth,
                    locale = locale,
                    onChoose = { month = it; choosingMonth = false },
                )
                return@Column
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Box(Modifier.weight(1f).height(16.dp), Alignment.Center) {
                        Text(day, style = ZarType.bodyCaption, color = colors.textTertiary)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val offset = month.atDay(1).dayOfWeek.value % 7
                val count = month.lengthOfMonth()
                val weekCount = (offset + count + 6) / 7
                repeat(weekCount) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { weekday ->
                            val number = week * 7 + weekday - offset + 1
                            val candidate = if (number in 1..count) month.atDay(number) else null
                            val enabled = candidate != null && candidate <= today
                            val active = candidate == selected
                            Box(Modifier.weight(1f).height(46.dp), Alignment.Center) {
                                if (candidate != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Box(
                                            Modifier.size(38.dp).clip(CircleShape)
                                                .background(if (active) colors.accentPrimary else Color.Transparent)
                                                .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Select $candidate") { selected = candidate },
                                            Alignment.Center,
                                        ) {
                                            Text(
                                                number.toString(),
                                                style = ZarType.bodyM.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
                                                color = when { active -> colors.textOnInverse; !enabled -> colors.textDisabled; else -> colors.textPrimary },
                                            )
                                        }
                                        Box(Modifier.size(4.dp).background(if (candidate in datesWithEntries && enabled) colors.textPlaceholder else Color.Transparent, CircleShape))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(4.dp).background(colors.textPlaceholder, CircleShape))
                Text("Days with entries · future dates off", style = ZarType.bodyCaption, color = colors.textSecondary)
            }
        }
    }
}

/** Figma Z09 picker: 12-hour hour/minute dial, AM/PM switch and keyboard mode. */
@Composable
fun ManualTimePickerSheet(
    time: LocalTime,
    onCancel: () -> Unit,
    onSetTime: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    /** Optional times (Income) offer "Remove time"; Expense and Transfer always have a time and pass null. */
    onClear: (() -> Unit)? = null,
) {
    var selected by remember(time) { mutableStateOf(time.withSecond(0).withNano(0)) }
    var activeHour by remember { mutableStateOf(true) }
    var typing by remember { mutableStateOf(false) }
    var typedHour by remember(time, typing) { mutableStateOf(((selected.hour + 11) % 12 + 1).toString()) }
    var typedMinute by remember(time, typing) { mutableStateOf("%02d".format(selected.minute)) }
    val colors = ZarTheme.colors
    PickerShell(title = "Time", onCancel = onCancel, action = "Set time", modifier = modifier, onAction = {
        val chosen = if (typing) {
            val hour = typedHour.toIntOrNull()?.takeIf { it in 1..12 }
            val minute = typedMinute.toIntOrNull()?.takeIf { it in 0..59 }
            if (hour == null || minute == null) null
            else LocalTime.of((hour % 12) + if (selected.hour >= 12) 12 else 0, minute)
        } else selected
        if (chosen != null) onSetTime(chosen)
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // "Now" is read when a chip is tapped, not when the sheet composed: the picker can stay composed for
            // minutes, and "15 min ago" must be relative to the clock at the tap.
            listOf("Now" to 0L, "15 min ago" to 15L, "1 hour ago" to 60L).forEach { (label, minutesAgo) ->
                ZarChip(label = label, selected = false, onClick = {
                    selected = LocalTime.now(ZarTime.ZONE).minusMinutes(minutesAgo).withSecond(0).withNano(0)
                    typedHour = ((selected.hour + 11) % 12 + 1).toString()
                    typedMinute = "%02d".format(selected.minute)
                })
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TimeNumber(
                value = if (typing) typedHour else ((selected.hour + 11) % 12 + 1).toString(),
                selected = activeHour,
                onClick = { activeHour = true },
                onValueChange = { typedHour = it.filter(Char::isDigit).take(2) },
                typing = typing,
            )
            Text(":", modifier = Modifier.padding(horizontal = 8.dp), style = ZarType.amountXL.copy(fontSize = 44.sp), color = colors.textTertiary)
            TimeNumber(
                value = if (typing) typedMinute else "%02d".format(selected.minute),
                selected = !activeHour,
                onClick = { activeHour = false },
                onValueChange = { typedMinute = it.filter(Char::isDigit).take(2) },
                typing = typing,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.width(58.dp).height(82.dp).border(1.dp, Color(0xFFE3E4E8), RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))) {
                listOf("AM", "PM").forEachIndexed { index, label ->
                    val active = (selected.hour >= 12) == (index == 1)
                    Box(Modifier.fillMaxWidth().weight(1f).background(if (active) colors.selectedTint else colors.surface)
                        .clickable(role = Role.Button, onClickLabel = "Select $label") {
                            selected = selected.withHour((selected.hour % 12) + if (index == 1) 12 else 0)
                        }, Alignment.Center) {
                        Text(label, style = ZarType.bodyS.copy(fontWeight = FontWeight.Medium), color = if (active) colors.textSelected else colors.textTertiary)
                    }
                }
            }
        }

        if (!typing) {
            ClockDial(time = selected, hourMode = activeHour, onSelect = { detent ->
                selected = if (activeHour) {
                    selected.withHour(detent + if (selected.hour >= 12) 12 else 0)
                } else {
                    selected.withMinute(detent)
                }
            }, onSelectionFinished = { if (activeHour) activeHour = false })
        } else {
            // Figma Z191: the 256dp dial box collapses to one hint line, so the sheet shrinks and the actions stay above
            // the number pad instead of being pushed off the screen.
            Text(
                "Enter hour and minute above. AM / PM stays as selected.",
                style = ZarType.bodyS,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth().testTag("time-typing-hint"),
            )
        }

        Box(Modifier.fillMaxWidth(), Alignment.Center) {
            Box(Modifier.width(160.dp).height(44.dp).clip(RoundedCornerShape(16.dp)).background(colors.subtle)
                .clickable(role = Role.Button, onClickLabel = if (typing) "Use clock dial" else "Type the time") {
                    if (typing) {
                        val hour = typedHour.toIntOrNull()?.takeIf { it in 1..12 }
                        val minute = typedMinute.toIntOrNull()?.takeIf { it in 0..59 }
                        if (hour != null && minute != null) selected = LocalTime.of((hour % 12) + if (selected.hour >= 12) 12 else 0, minute)
                    }
                    typing = !typing
                }, Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!typing) {
                        Canvas(Modifier.size(18.dp)) {
                            val ink = colors.iconMuted
                            drawRoundRect(ink, topLeft = Offset(1.dp.toPx(), 3.dp.toPx()), size = Size(16.dp.toPx(), 12.dp.toPx()),
                                cornerRadius = CornerRadius(1.dp.toPx()), style = Stroke(1.dp.toPx()))
                            for (row in 0..1) for (col in 0..4) {
                                drawCircle(ink, radius = 0.65f.dp.toPx(), center = Offset((4 + col * 2.5f).dp.toPx(), (6 + row * 2.5f).dp.toPx()))
                            }
                            drawLine(ink, Offset(5.dp.toPx(), 12.dp.toPx()), Offset(13.dp.toPx(), 12.dp.toPx()), strokeWidth = 1.dp.toPx())
                        }
                    }
                    Text(if (typing) "Use the dial" else "Type the time", style = ZarType.labelAction, color = colors.textPrimary)
                }
            }
        }
        if (onClear != null) {
            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button, onClick = onClear).padding(horizontal = 16.dp).testTag("time-clear"), Alignment.Center) {
                    Text("Remove time", style = ZarType.labelAction, color = colors.textSelected)
                }
            }
        }
    }
}

@Composable
private fun PickerShell(title: String, onCancel: () -> Unit, action: String, onAction: () -> Unit, modifier: Modifier = Modifier, body: @Composable () -> Unit) {
    val colors = ZarTheme.colors
    Column(
        modifier.fillMaxWidth().heightIn(max = 760.dp)
            .shadow(14.dp, ZarShapes.sheet, ambientColor = Color(0x1F000000), spotColor = Color(0x1F000000))
            .clip(ZarShapes.sheet).background(colors.surface).pointerInput(Unit) { detectTapGestures {} }
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                Box(Modifier.size(36.dp, 4.dp).background(colors.handle, RoundedCornerShape(2.dp)))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(20.dp).clickable(role = Role.Button, onClickLabel = "Back") { onCancel() }, Alignment.Center) {
                    ZarIcon(R.drawable.ic_ph_arrow_left, size = 20.dp, tint = colors.iconStrong)
                }
                Text(title, style = ZarType.headingSheet, color = colors.textPrimary)
            }
        }
        body()
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ZarButton("Cancel", onCancel, Modifier.weight(1f), style = ZarButtonStyle.Soft)
            ZarButton(action, onAction, Modifier.weight(1f))
        }
    }
}

/** A year stepper over a 3 x 4 grid of months. Months after the current one are off, like future days. */
@Composable
private fun MonthChooser(selected: YearMonth, latest: YearMonth, locale: java.util.Locale, onChoose: (YearMonth) -> Unit) {
    val colors = ZarTheme.colors
    var year by remember(selected) { mutableStateOf(selected.year) }
    Column(Modifier.testTag("date-month-grid"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthArrow("Previous year", enabled = true, rotated = true) { year-- }
            Text(
                year.toString(),
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = ZarType.bodyL.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
            )
            MonthArrow("Next year", enabled = year < latest.year) { year++ }
        }
        repeat(4) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { column ->
                    val candidate = YearMonth.of(year, row * 3 + column + 1)
                    val enabled = candidate <= latest
                    val active = candidate == selected
                    Box(
                        Modifier.weight(1f).height(46.dp).clip(ZarShapes.sm)
                            .background(if (active) colors.accentPrimary else colors.subtle)
                            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Show ${candidate.month.getDisplayName(java.time.format.TextStyle.FULL, locale)} $year") { onChoose(candidate) }
                            .testTag("date-month-${candidate.monthValue}"),
                        Alignment.Center,
                    ) {
                        Text(
                            candidate.month.getDisplayName(java.time.format.TextStyle.SHORT, locale),
                            style = ZarType.bodyM.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
                            color = when { active -> colors.textOnInverse; !enabled -> colors.textDisabled; else -> colors.textPrimary },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A horizontal swipe of at least 48dp over the calendar moves one month: toward the start of the reading direction
 * goes back, toward its end goes forward. Vertical drags are left to the sheet's own scrolling.
 */
private suspend fun PointerInputScope.detectMonthSwipes(rtl: Boolean, onStep: (Long) -> Unit) {
    val threshold = 48.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var total = 0f
        var started = false
        val first = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            total += over
            started = true
        } ?: return@awaitEachGesture
        if (started) {
            horizontalDrag(first.id) { change ->
                total += change.positionChange().x
                change.consume()
            }
            if (kotlin.math.abs(total) >= threshold) {
                // Dragging right reveals the previous month in LTR; mirrored in RTL.
                val back = if (rtl) total < 0 else total > 0
                onStep(if (back) -1L else 1L)
            }
        }
    }
}

@Composable
private fun MonthArrow(label: String, enabled: Boolean, rotated: Boolean = false, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    Box(Modifier.size(44.dp).clip(CircleShape).background(colors.subtle).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick).semantics { contentDescription = label }, Alignment.Center) {
        ZarIcon(R.drawable.ic_ph_caret_right, modifier = if (rotated) Modifier.rotate(180f) else Modifier, size = 18.dp, tint = if (enabled) colors.iconStrong else colors.textDisabled)
    }
}

@Composable
private fun TimeNumber(value: String, selected: Boolean, onClick: () -> Unit, onValueChange: (String) -> Unit, typing: Boolean) {
    val colors = ZarTheme.colors
    Box(Modifier.size(104.dp, 80.dp).clip(RoundedCornerShape(18.dp))
        .background(if (selected) colors.selectedTint else colors.subtle)
        .clickable(role = Role.Button, onClick = onClick), Alignment.Center) {
        if (typing) {
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = ZarType.amountXL.copy(color = if (selected) colors.textSelected else colors.textPrimary, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(value, style = ZarType.amountXL, color = if (selected) colors.textSelected else colors.textPrimary)
        }
    }
}

@Composable
private fun ClockDial(time: LocalTime, hourMode: Boolean, onSelect: (Int) -> Unit, onSelectionFinished: () -> Unit) {
    val colors = ZarTheme.colors
    val density = LocalDensity.current
    // Hours step through 12 positions, minutes through all 60, so every minute is reachable and ticks.
    val detents = if (hourMode) 12 else 60
    val labelStep = detents / 12
    val restingAngle = ClockGeometry.angleOfDetent(if (hourMode) time.hour % 12 else time.minute, detents)
    val selectedIndex = if (hourMode) time.hour % 12 else time.minute
    val policy = LocalZarMotion.current
    var dragging by remember { mutableStateOf(false) }
    // The arm and its end knob are one shape drawn at one angle. It sits on the selected position while a finger drags,
    // so it never leads or trails the selection; only a tap or a typed change settles to it, along the shortest path
    // with the shared settle spring. Reduced motion and disabled system animations resolve immediately.
    val settle = remember { Animatable(restingAngle) }
    LaunchedEffect(dragging, restingAngle, policy) {
        val delta = ClockGeometry.signedDelta(settle.value, restingAngle)
        if (!dragging && delta != 0f && policy.allowsSpatialMotion) {
            settle.animateTo(settle.value + delta, policy.spring(ZarSprings.Settle))
        } else {
            settle.snapTo(restingAngle)
        }
    }
    val armAngle = settle.value
    val theta = armAngle * PI / 180 - PI / 2
    // The arm always ends in the same big knob. Between numbers (a single minute) a small dot marks its centre.
    val onNumber = selectedIndex % labelStep == 0
    val latestAngle by rememberUpdatedState(restingAngle)
    val latestIndex by rememberUpdatedState(selectedIndex)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestFinished by rememberUpdatedState(onSelectionFinished)
    val ticks = rememberSelectionTicker()
    val input = remember(density, ticks, detents) {
        with(density) {
            ClockDialInput(
                config = ClockDialConfig(
                    armLength = 100.dp.toPx(),
                    centerDeadzone = 24.dp.toPx(),
                    armGrabRadius = 24.dp.toPx(),
                    // A minute is only 6 degrees wide, so the dead band past its edge must be small.
                    hysteresisDegrees = if (detents == 12) 3f else 1f,
                    detents = detents,
                ),
                restingArmAngle = { latestAngle },
                currentDetent = { latestIndex },
                onSelectDetent = { latestSelect(it) },
                onFinished = { latestFinished() },
                ticks = ticks,
                onArmAngle = { dragging = it != null },
            )
        }
    }
    Box(Modifier.fillMaxWidth(), Alignment.Center) {
        Box(
            Modifier.size(256.dp).clip(CircleShape).background(colors.subtle).testTag("time-dial")
                .pointerInput(input) { detectClockGestures(input) },
            Alignment.Center,
        ) {
            Canvas(Modifier.size(256.dp)) {
                val centre = Offset(size.width / 2, size.height / 2)
                val end = Offset(
                    centre.x + 100.dp.toPx() * cos(theta).toFloat(),
                    centre.y + 100.dp.toPx() * sin(theta).toFloat(),
                )
                drawLine(colors.accentPrimary, centre, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(colors.accentPrimary, radius = 4.dp.toPx(), center = centre)
                drawCircle(colors.accentPrimary, radius = 22.dp.toPx(), center = end)
                if (!onNumber) drawCircle(colors.textOnInverse, radius = 2.5.dp.toPx(), center = end)
            }
            repeat(12) { index ->
                val label = if (hourMode) (if (index == 0) 12 else index).toString() else "%02d".format(index * 5)
                val angle = 2 * PI * index / 12 - PI / 2
                val x = (100 * cos(angle)).dp
                val y = (100 * sin(angle)).dp
                val detent = index * labelStep
                val chosen = selectedIndex == detent
                // Touches are resolved by position on the dial itself; this node is the TalkBack/keyboard alternative.
                var focused by remember { mutableStateOf(false) }
                Box(Modifier.align(Alignment.Center)
                    .offset(x = x, y = y)
                    .size(48.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .onKeyEvent { event ->
                        // Real keyboard activation: Enter/Space/centre-Dpad select this position exactly like a tap.
                        if (event.type == KeyEventType.KeyUp &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter ||
                                event.key == Key.Spacebar || event.key == Key.DirectionCenter)
                        ) {
                            input.clickDetent(detent)
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        selected = chosen
                        onClick(label = "Select $label") { input.clickDetent(detent); true }
                    }, Alignment.Center) {
                    Box(Modifier.size(44.dp).clip(CircleShape)
                        .then(if (focused) Modifier.border(2.dp, colors.accentPrimary, CircleShape) else Modifier), Alignment.Center) {
                        Text(label, style = ZarType.bodyL.copy(fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal),
                            color = colors.textPrimary)
                    }
                }
            }
            // The same numbers again in the knob's text colour, visible only inside the knob, so a number it covers
            // (fully or partly) reads correctly. Decorative: the touch and accessibility nodes are the ones above.
            Box(
                Modifier.size(256.dp).clearAndSetSemantics {}.drawWithContent {
                    val end = Offset(
                        size.width / 2 + 100.dp.toPx() * cos(theta).toFloat(),
                        size.height / 2 + 100.dp.toPx() * sin(theta).toFloat(),
                    )
                    clipPath(Path().apply { addOval(Rect(center = end, radius = 22.dp.toPx())) }) { this@drawWithContent.drawContent() }
                },
                Alignment.Center,
            ) {
                repeat(12) { index ->
                    val label = if (hourMode) (if (index == 0) 12 else index).toString() else "%02d".format(index * 5)
                    val angle = 2 * PI * index / 12 - PI / 2
                    Box(Modifier.align(Alignment.Center).offset(x = (100 * cos(angle)).dp, y = (100 * sin(angle)).dp).size(48.dp), Alignment.Center) {
                        Text(label, style = ZarType.bodyL.copy(fontWeight = FontWeight.Medium), color = colors.textOnInverse)
                    }
                }
            }
        }
    }
}

/** Tap selects the sector under the finger; a drag past touch slop moves the arm, then ends once on release. */
internal suspend fun PointerInputScope.detectClockGestures(input: ClockDialInput) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val centre = Offset(size.width / 2f, size.height / 2f)
        fun rel(position: Offset) = position - centre
        input.press(rel(down.position).x, rel(down.position).y)
        val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
        if (slop == null) {
            val up = currentEvent.changes.firstOrNull { it.id == down.id }
            if (up != null && !up.pressed && !up.isConsumed) {
                up.consume()
                input.tap(rel(up.position).x, rel(up.position).y, up.uptimeMillis)
            }
            return@awaitEachGesture
        }
        input.dragStart(rel(slop.position).x, rel(slop.position).y, slop.uptimeMillis)
        var completed = false
        try {
            completed = drag(slop.id) { change ->
                input.dragMove(rel(change.position).x, rel(change.position).y, change.uptimeMillis)
                change.consume()
            }
        } finally {
            // A cancelled coroutine (re-created pointerInput, taken-over gesture) must still end dragging and clear the arm.
            if (completed) input.dragEnd() else input.dragCancel()
        }
    }
}
