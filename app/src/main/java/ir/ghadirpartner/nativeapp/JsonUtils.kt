package ir.ghadirpartner.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it) }
fun JSONObject.s(key: String): String = optString(key, "")
fun JSONObject.i(key: String): Int = optInt(key, 0)
fun JSONObject.l(key: String): Long = optLong(key, 0L)
fun JSONObject.b(key: String): Boolean = optBoolean(key, false)
fun JSONObject.arr(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
fun JSONObject.obj(key: String): JSONObject = optJSONObject(key) ?: JSONObject()

private val faMap = mapOf(
    '0' to '۰', '1' to '۱', '2' to '۲', '3' to '۳', '4' to '۴',
    '5' to '۵', '6' to '۶', '7' to '۷', '8' to '۸', '9' to '۹'
)

fun faDigits(value: String): String = buildString(value.length) {
    value.forEach { append(faMap[it] ?: it) }
}

fun faNumber(value: Int): String = faDigits(value.toString())
fun faNumber(value: Long): String = faDigits(value.toString())
fun formatMoney(value: Long): String = faDigits(DecimalFormat("#,###").format(value))

private fun gregorianToJalali(gyInput: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
    val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    var gy = gyInput
    var jy: Int
    if (gy > 1600) {
        jy = 979
        gy -= 1600
    } else {
        jy = 0
        gy -= 621
    }
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 - 80 + gd + gdm[gm - 1]
    jy += 33 * (days / 12053)
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm: Int
    val jd: Int
    if (days < 186) {
        jm = 1 + days / 31
        jd = 1 + days % 31
    } else {
        jm = 7 + (days - 186) / 30
        jd = 1 + (days - 186) % 30
    }
    return Triple(jy, jm, jd)
}

fun formatDateFa(raw: String, includeTime: Boolean = true): String {
    val value = raw.trim()
    if (value.length < 10) return faDigits(value.ifBlank { "-" })
    return try {
        val y = value.substring(0, 4).toInt()
        val m = value.substring(5, 7).toInt()
        val d = value.substring(8, 10).toInt()
        val (jy, jm, jd) = gregorianToJalali(y, m, d)
        val date = "%04d/%02d/%02d".format(jy, jm, jd)
        val time = if (includeTime && value.length >= 16) value.substring(11, 16) else ""
        faDigits(if (time.isBlank()) date else "$date • $time")
    } catch (_: Exception) {
        faDigits(value)
    }
}

fun shortProductName(raw: String): String {
    return raw
        .replace("دستگاه كارتخوان ", "")
        .replace("دستگاه کارتخوان ", "")
        .replace("دستگاه كش لس ", "")
        .replace("دستگاه کش لس ", "")
        .trim()
}
