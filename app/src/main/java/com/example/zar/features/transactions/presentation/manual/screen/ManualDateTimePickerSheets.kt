package com.example.zar.features.transactions.presentation.manual.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarIcon
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
    val today = remember { ZarTime.today() }
    var selected by remember(date) { mutableStateOf(date.coerceAtMost(today)) }
    var month by remember(date) { mutableStateOf(YearMonth.from(selected)) }
    val colors = ZarTheme.colors
    PickerShell(title = "Date", onCancel = onCancel, action = "Set date", onAction = { onSetDate(selected) }, modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today" to today, "Yesterday" to today.minusDays(1), "2 days ago" to today.minusDays(2)).forEach { (label, value) ->
                ZarChip(label = label, selected = selected == value, onClick = {
                    selected = value
                    month = YearMonth.from(value)
                })
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), style = ZarType.bodyL.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
                    ZarIcon(R.drawable.ic_ph_caret_down, size = 14.dp, tint = colors.iconMuted)
                }
                MonthArrow("Previous month", enabled = true, rotated = true) { month = month.minusMonths(1) }
                Spacer(Modifier.width(8.dp))
                MonthArrow("Next month", enabled = month < YearMonth.from(today)) { month = month.plusMonths(1) }
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
fun ManualTimePickerSheet(time: LocalTime, onCancel: () -> Unit, onSetTime: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
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
            val now = remember { LocalTime.now(ZarTime.ZONE) }
            listOf("Now" to now, "15 min ago" to now.minusMinutes(15), "1 hour ago" to now.minusHours(1)).forEach { (label, value) ->
                ZarChip(label = label, selected = false, onClick = {
                    selected = value.withSecond(0).withNano(0)
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
            ClockDial(selected = selected, hourMode = activeHour, onSelect = { value ->
                selected = if (activeHour) {
                    selected.withHour((value % 12) + if (selected.hour >= 12) 12 else 0)
                } else {
                    selected.withMinute((value % 12) * 5)
                }
                if (activeHour) activeHour = false
            })
        } else {
            Box(Modifier.fillMaxWidth().height(256.dp), Alignment.Center) {
                Text("Enter hour and minute above", style = ZarType.bodyS, color = colors.textSecondary)
            }
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
                Box(Modifier.size(36.dp, 4.dp).background(colors.trackOff, RoundedCornerShape(2.dp)))
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

@Composable
private fun MonthArrow(label: String, enabled: Boolean, rotated: Boolean = false, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    Box(Modifier.size(44.dp).clip(CircleShape).background(colors.subtle).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick), Alignment.Center) {
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
private fun ClockDial(selected: LocalTime, hourMode: Boolean, onSelect: (Int) -> Unit) {
    val colors = ZarTheme.colors
    val selectedIndex = if (hourMode) selected.hour % 12 else selected.minute / 5
    val theta = 2 * PI * selectedIndex / 12 - PI / 2
    Box(Modifier.fillMaxWidth(), Alignment.Center) {
        Box(Modifier.size(256.dp).clip(CircleShape).background(colors.subtle), Alignment.Center) {
            Canvas(Modifier.size(256.dp)) {
                val centre = Offset(size.width / 2, size.height / 2)
                val end = Offset(
                    centre.x + 100.dp.toPx() * cos(theta).toFloat(),
                    centre.y + 100.dp.toPx() * sin(theta).toFloat(),
                )
                drawLine(colors.accentPrimary, centre, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(colors.accentPrimary, radius = 4.dp.toPx(), center = centre)
            }
            repeat(12) { index ->
                val number = if (index == 0) 12 else index
                val label = if (hourMode) number.toString() else "%02d".format(index * 5)
                val angle = 2 * PI * index / 12 - PI / 2
                val x = (100 * cos(angle)).dp
                val y = (100 * sin(angle)).dp
                Box(Modifier.align(Alignment.Center).padding(0.dp)
                    .then(Modifier.offset(x = x, y = y))
                    .size(44.dp).clip(CircleShape)
                    .background(if (selectedIndex == index) colors.accentPrimary else Color.Transparent)
                    .clickable(role = Role.Button, onClickLabel = "Select $label") { onSelect(number) }, Alignment.Center) {
                    Text(label, style = ZarType.bodyL.copy(fontWeight = if (selectedIndex == index) FontWeight.Medium else FontWeight.Normal),
                        color = if (selectedIndex == index) colors.textOnInverse else colors.textPrimary)
                }
            }
        }
    }
}
