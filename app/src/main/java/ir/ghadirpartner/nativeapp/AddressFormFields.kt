package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AddressFormFields(
    title: String,
    onTitleChange: (String) -> Unit,
    province: String,
    onProvinceChange: (String) -> Unit,
    city: String,
    onCityChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    postal: String,
    onPostalChange: (String) -> Unit,
) {
    OutlinedTextField(title, { onTitleChange(it.take(60)) }, label = { Text("عنوان، مثلاً دفتر") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(province, { onProvinceChange(it.take(80)) }, label = { Text("استان") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(city, { onCityChange(it.take(80)) }, label = { Text("شهر") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        address,
        { onAddressChange(it.take(1000)) },
        label = { Text("نشانی کامل و پلاک") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
    OutlinedTextField(
        postal,
        { onPostalChange(it.filter(Char::isDigit).take(10)) },
        label = { Text("کدپستی اختیاری") },
        modifier = Modifier.fillMaxWidth(),
    )
}
