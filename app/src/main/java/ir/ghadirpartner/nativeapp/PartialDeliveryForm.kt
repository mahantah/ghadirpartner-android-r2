package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/** Let management select an exact physical subset without changing payment or the remaining order. */
@Composable
internal fun PartialDeliveryForm(
    service: PartialDeliveryService,
    order: JSONObject,
    onChanged: () -> Unit,
) {
    val lines = remember(order) { service.lines(order) }
    val quantities = remember(order) { mutableStateMapOf<String, String>() }
    val requestId = remember(order) { UUID.randomUUID().toString() }
    val scope = rememberCoroutineScope()
    var expanded by remember(order) { mutableStateOf(false) }
    var reason by remember(order) { mutableStateOf("") }
    var carrier by remember(order) { mutableStateOf("") }
    var tracking by remember(order) { mutableStateOf("") }
    var courierName by remember(order) { mutableStateOf("") }
    var courierPhone by remember(order) { mutableStateOf("") }
    var error by remember(order) { mutableStateOf("") }
    var saving by remember(order) { mutableStateOf(false) }
    var confirming by remember(order) { mutableStateOf(false) }
    val selected =
        lines.mapNotNull { line ->
            val quantity = quantities[line.product]?.toIntOrNull() ?: 0
            if (quantity > 0) PartialDeliveryQuantity(line.product, quantity) else null
        }
    val total = selected.sumOf { it.quantity }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GhadirButton("تحویل بخشی با مجوز مدیر", { expanded = !expanded }, enabled = !saving, secondary = true)
        if (expanded) {
            DesignNotice("فقط تعداد انتخاب‌شده از بار آماده خارج می‌شود؛ وضعیت تسویه و ماندهٔ بدهی تغییر نمی‌کند.")
            ErrorBanner(error) { error = "" }
            TextButton(enabled = !saving, onClick = {
                lines.forEach { quantities[it.product] = minOf(it.remaining / 3, it.ready).toString() }
            }) { Text("انتخاب یک‌سوم ماندهٔ هر مدل") }
            lines.forEach { line ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    DetailLine(
                        shortProductName(line.product),
                        "ارسال‌شده: ${faNumber(line.shipped)} • مانده: ${faNumber(line.remaining)} • آماده: ${faNumber(line.ready)}",
                    )
                    DesignField(
                        "تعداد تحویل ${line.product}",
                        quantities[line.product].orEmpty(),
                        { value ->
                            val normalized =
                                value
                                    .map {
                                        if (it.isDigit()) Character.getNumericValue(it).digitToChar() else it
                                    }.joinToString("")
                            if (normalized.all { it in '0'..'9' } &&
                                normalized.length <= BuildConfig.ORDER_MAXIMUM_QUANTITY.toString().length
                            ) {
                                quantities[line.product] = normalized
                            }
                        },
                        enabled = !saving && line.ready > 0,
                        keyboardType = KeyboardType.Number,
                    )
                }
            }
            DesignField("علت مجوز تحویل بخشی", reason, {
                reason = it.take(BuildConfig.PARTIAL_DELIVERY_MAX_REASON_LENGTH)
            }, enabled = !saving, singleLine = false)
            SimpleDropdown("نوع تحویل بخشی", carrier.ifBlank { "انتخاب کنید" }, service.carriers) { if (!saving) carrier = it }
            if (service.needsTracking(carrier)) {
                DesignField("کد پیگیری تحویل بخشی", tracking, { tracking = it }, enabled = !saving)
            }
            if (carrier == "پیک") {
                DesignField("نام پیک تحویل بخشی", courierName, { courierName = it }, enabled = !saving)
                DesignField(
                    "شماره تماس پیک تحویل بخشی",
                    courierPhone,
                    { courierPhone = it },
                    enabled = !saving,
                    keyboardType = KeyboardType.Phone,
                )
            }
            DetailLine("خروج انتخاب‌شده", "${faNumber(total)} دستگاه")
            DetailLine("مانده پس از خروج", "${faNumber((lines.sumOf { it.remaining } - total).coerceAtLeast(0))} دستگاه")
            GhadirButton(
                "بررسی و ثبت خروج بخشی",
                { confirming = true },
                enabled =
                    !saving && total > 0 && reason.isNotBlank() && carrier.isNotBlank(),
            )
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { if (!saving) confirming = false },
            title = { Text("ثبت خروج ${faNumber(total)} دستگاه؟") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("فقط سریال‌های زیر خارج می‌شوند و باقی بار باز می‌ماند. تسویهٔ مشتری تغییری نمی‌کند.") }
                    items(selected, key = { it.product }) { selectedLine ->
                        val serials = lines.first { it.product == selectedLine.product }.readySerials.take(selectedLine.quantity)
                        Text(selectedLine.product + " • " + faNumber(selectedLine.quantity) + " دستگاه")
                        Text(serials.joinToString("، "))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    scope.launch {
                        saving = true
                        error = ""
                        try {
                            service.dispatch(
                                lines,
                                PartialDeliveryRequest(
                                    order.i("id"),
                                    requestId,
                                    reason,
                                    selected,
                                    carrier,
                                    tracking,
                                    courierName,
                                    courierPhone,
                                ),
                            )
                            confirming = false
                            onChanged()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            confirming = false
                            error = failure.message ?: "ثبت تحویل بخشی ناموفق بود؛ پیش از ثبت دوباره سفارش را تازه‌سازی کنید"
                        } finally {
                            saving = false
                        }
                    }
                }) { Text(if (saving) "در حال ثبت..." else "تأیید و ثبت خروج") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { confirming = false }) { Text("بازگشت") } },
        )
    }
}
