package com.microsip.android.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microsip.android.sip.DialogInfo
import com.microsip.android.ui.theme.Away
import com.microsip.android.ui.theme.CallGreen
import com.microsip.android.ui.theme.HangupRed
import com.microsip.android.ui.theme.Offline
import org.linphone.core.ConsolidatedPresence
import org.linphone.core.RegistrationState

@Composable
fun Avatar(name: String, size: Dp = 40.dp) {
    val initials = name.split(' ', '@', '.').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

fun registrationColor(state: RegistrationState?): Color = when (state) {
    RegistrationState.Ok -> CallGreen
    RegistrationState.Progress, RegistrationState.Refreshing -> Away
    RegistrationState.Failed -> HangupRed
    else -> Offline
}

fun presenceColor(p: ConsolidatedPresence?): Color = when (p) {
    ConsolidatedPresence.Online -> CallGreen
    ConsolidatedPresence.Busy -> HangupRed
    ConsolidatedPresence.DoNotDisturb -> HangupRed
    else -> Offline
}

fun blfColor(s: DialogInfo.BlfState?): Color = when (s) {
    DialogInfo.BlfState.IDLE -> CallGreen
    DialogInfo.BlfState.RINGING -> Away
    DialogInfo.BlfState.BUSY -> HangupRed
    else -> Offline
}

fun formatDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

fun formatWhen(epochSeconds: Long): String =
    DateUtils.getRelativeTimeSpanString(epochSeconds * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
