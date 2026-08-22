package `in`.callsentry.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import `in`.callsentry.app.R
import `in`.callsentry.app.core.Decision
import `in`.callsentry.app.core.IdentityLevel

object Ui {

    fun levelName(l: IdentityLevel): String = when (l) {
        IdentityLevel.OFFICIAL -> "Officially verified"
        IdentityLevel.PARTNER -> "Partner-verified"
        IdentityLevel.LIKELY -> "Likely identity"
        IdentityLevel.PERSONAL -> "Your label"
        IdentityLevel.POSSIBLE_FINANCIAL -> "Possible financial caller"
        IdentityLevel.UNKNOWN -> "Unknown caller"
        IdentityLevel.DISPUTED -> "Disputed identity"
    }

    fun levelColor(c: Context, l: IdentityLevel): Int = ContextCompat.getColor(
        c,
        when (l) {
            IdentityLevel.OFFICIAL -> R.color.levelOfficial
            IdentityLevel.PARTNER -> R.color.levelPartner
            IdentityLevel.LIKELY -> R.color.levelLikely
            IdentityLevel.PERSONAL -> R.color.levelPersonal
            IdentityLevel.POSSIBLE_FINANCIAL -> R.color.levelPossible
            IdentityLevel.UNKNOWN -> R.color.levelUnknown
            IdentityLevel.DISPUTED -> R.color.levelDisputed
        }
    )

    fun badge(tv: TextView, text: String, color: Int) {
        tv.text = text
        tv.backgroundTintList = ColorStateList.valueOf(color)
        tv.setTextColor(0xFFFFFFFF.toInt())
    }

    fun actionName(d: Decision): String = when (d) {
        Decision.ALLOW -> "Allowed"
        Decision.SILENCE -> "Silenced"
        Decision.REJECT -> "Blocked"
    }

    fun actionColor(c: Context, d: Decision): Int = ContextCompat.getColor(
        c,
        when (d) {
            Decision.ALLOW -> R.color.actionAllow
            Decision.SILENCE -> R.color.actionSilence
            Decision.REJECT -> R.color.actionReject
        }
    )

    fun parseLevel(s: String): IdentityLevel =
        runCatching { IdentityLevel.valueOf(s) }.getOrDefault(IdentityLevel.UNKNOWN)

    fun parseAction(s: String): Decision =
        runCatching { Decision.valueOf(s) }.getOrDefault(Decision.ALLOW)
}
