package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition

/** Display a server-confirmed payment, with an optional action appropriate to the current screen. */
@Composable
internal fun PaymentSuccessCard(
    amount: Long,
    message: String = "پرداخت شما تأیید شد",
    action: @Composable () -> Unit = {},
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.payment_success))
    GlassSurface(modifier = Modifier.fillMaxWidth(), color = AppSurface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (composition == null) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(108.dp), tint = Success)
            } else {
                LottieAnimation(composition, modifier = Modifier.size(108.dp))
            }
            Text("پرداخت موفق", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(message, color = Muted, textAlign = TextAlign.Center)
            if (amount > 0L) {
                Text("${formatMoney(amount)} تومان", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            action()
        }
    }
}
