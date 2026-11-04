package ir.ghadirpartner.nativeapp

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Immutable selected Persian day, stored in the API's existing date format. */
internal data class PersianDay(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    /** Serializes an unambiguous ASCII date for existing server validation. */
    fun serialize(): String = String.format(Locale.US, "%04d/%02d/%02d", year, month, day)
}

/** Uses Android's Persian calendar for leap years and business-local dates. */
internal class PersianCalendarService(
    private val clock: CheckoutClock,
    private val zone: String,
) {
    private fun calendar(): Calendar = Calendar.getInstance(TimeZone.getTimeZone(zone), ULocale("fa_IR@calendar=persian"))

    /** Returns today's Persian date in the configured business time zone. */
    fun today(): PersianDay =
        calendar().run {
            timeInMillis = clock.now()
            PersianDay(get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH))
        }

    private fun at(day: PersianDay): Calendar =
        calendar().apply {
            isLenient = false
            clear()
            set(day.year, day.month - 1, day.day, 12, 0, 0)
        }

    /** Parses only real Persian calendar dates; old or empty input opens on today. */
    fun parse(raw: String): PersianDay =
        runCatching {
            val parts = asciiDigits(raw).split('/', '-').map(String::toInt)
            require(parts.size == 3 && parts[0] > 0)
            val day = PersianDay(parts[0], parts[1], parts[2])
            at(day).timeInMillis
            day
        }.getOrElse { today() }

    /** Returns the actual length of a Persian month, including leap Esfand. */
    fun daysInMonth(day: PersianDay): Int = at(day.copy(day = 1)).getActualMaximum(Calendar.DAY_OF_MONTH)

    /** Returns the first weekday as a Saturday-first column offset. */
    fun firstWeekday(day: PersianDay): Int = at(day.copy(day = 1)).get(Calendar.DAY_OF_WEEK) % 7

    /** Moves by calendar months, preserving validity at short-month boundaries. */
    fun moveMonth(
        day: PersianDay,
        amount: Int,
    ): PersianDay =
        at(day).run {
            add(Calendar.MONTH, amount)
            PersianDay(get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH))
        }
}

/** Shows a wrapping value inside its border and a separate, unclipped field label. */
@Composable
private fun SelectionField(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = AppInk)
        Surface(
            modifier =
                Modifier.fillMaxWidth().clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = "انتخاب $label",
                    onClick = onClick,
                ),
            color = AppSurface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, AppInk.copy(alpha = 0.22f)),
        ) {
            Text(value.ifBlank { placeholder }, Modifier.padding(16.dp), color = AppInk)
        }
    }
}

/** Selects a listed bank or accepts the exact issuer printed on an older cheque. */
@Composable
internal fun BankSelectionField(
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val banks = remember { BuildConfig.CHEQUE_BANKS.split('\n') }
    SelectionField("بانک", value, "انتخاب بانک ▾", enabled) {
        query = ""
        open = true
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = AppSurface.copy(alpha = 1f),
            title = { Text("انتخاب بانک", color = AppInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(query, { query = it.take(80) }, label = { Text("جست‌وجو یا نام بانک") }, singleLine = true)
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(banks.filter { it.contains(query.trim(), ignoreCase = true) }) { bank ->
                            TextButton(onClick = {
                                onChange(bank)
                                open = false
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(bank, Modifier.fillMaxWidth(), color = AppInk)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = query.isNotBlank(), onClick = {
                    onChange(query.trim())
                    open = false
                }) { Text("ثبت نام واردشده") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("انصراف") } },
        )
    }
}

/** Opens a Saturday-first Persian month grid and commits only on confirmation. */
@Composable
internal fun PersianDateSelectionField(
    label: String,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    val service = remember { PersianCalendarService(CheckoutClock { System.currentTimeMillis() }, BuildConfig.CHECKOUT_TIME_ZONE) }
    var open by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(service.parse(value)) }
    var month by remember { mutableStateOf(selected.copy(day = 1)) }
    val months = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    SelectionField(label, value, "انتخاب تاریخ شمسی ▦", enabled) {
        selected = service.parse(value)
        month = selected.copy(day = 1)
        open = true
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = AppSurface.copy(alpha = 1f),
            title = { Text("انتخاب $label", color = AppInk) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { month = service.moveMonth(month, -1) }) { Text("قبلی") }
                        Text("${months[month.month - 1]} ${month.year}", Modifier.weight(1f), textAlign = TextAlign.Center, color = AppInk)
                        TextButton(onClick = { month = service.moveMonth(month, 1) }) { Text("بعدی") }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach {
                            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = AppInk)
                        }
                    }
                    val offset = service.firstWeekday(month)
                    val count = service.daysInMonth(month)
                    repeat((offset + count + 6) / 7) { week ->
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { weekday ->
                                val day = week * 7 + weekday - offset + 1
                                Box(Modifier.weight(1f).heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                                    if (day in 1..count) {
                                        val date = month.copy(day = day)
                                        Surface(
                                            modifier =
                                                Modifier.size(36.dp).clickable(role = Role.Button, onClickLabel = date.serialize()) {
                                                    selected =
                                                        date
                                                },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (date == selected) AppInk else AppSurface,
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(day.toString(), color = if (date == selected) AppSurface.copy(alpha = 1f) else AppInk)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(selected.serialize(), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = AppInk)
                    TextButton(onClick = {
                        selected = service.today()
                        month = selected.copy(day = 1)
                    }) { Text("امروز") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(selected.serialize())
                    open = false
                }) { Text("تأیید تاریخ") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("انصراف") } },
        )
    }
}
