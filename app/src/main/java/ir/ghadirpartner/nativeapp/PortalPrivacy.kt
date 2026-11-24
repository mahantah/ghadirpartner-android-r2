package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** Makes the customer data notice available before signing in and from the profile. */
@Composable
internal fun PortalPrivacyLink() {
    var visible by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.portal_privacy_title)
    TextButton(onClick = { visible = true }) { Text(title) }
    if (visible) {
        AlertDialog(
            onDismissRequest = { visible = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.portal_privacy_body))
                }
            },
            confirmButton = {
                TextButton(onClick = { visible = false }) {
                    Text(stringResource(R.string.portal_privacy_close))
                }
            },
        )
    }
}
