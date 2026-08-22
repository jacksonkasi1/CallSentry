package `in`.callsentry.app.core

import android.content.SharedPreferences
import `in`.callsentry.app.data.Db

enum class Decision { ALLOW, SILENCE, REJECT }

class DecisionEngine(private val db: Db, private val prefs: SharedPreferences) {

    fun decide(number: String, identity: CallerIdentity): Pair<Decision, String> {
        // 1. The user's explicit rules always win.
        for (rule in db.rules()) {
            if (rule.matches(number)) {
                return DecisionEngine.actionOf(rule.action) to
                    ("Your rule: " + (rule.label ?: rule.pattern))
            }
        }

        // 2. Personal trust: contacts and own labels.
        if (identity.level == IdentityLevel.PERSONAL) {
            return Decision.ALLOW to "Matched your contacts or your own label"
        }

        // 2.5 Default rule presets the user toggled on.
        if (prefs.getBoolean("preset_intl", false) && Phone.isInternational(number)) {
            return Decision.SILENCE to "Default rule: non-Indian number — silenced"
        }

        // 3. Declared relationships let verified institutions through.
        val instId = identity.institutionId
        if (instId != null && !identity.isCommunitySpam &&
            (identity.level == IdentityLevel.OFFICIAL || identity.level == IdentityLevel.PARTNER) &&
            db.hasRelationship(instId)
        ) {
            return Decision.ALLOW to "Declared relationship: ${identity.name}"
        }

        // 4. Evidence-based defaults, per user settings.
        return when (identity.level) {
            IdentityLevel.OFFICIAL, IdentityLevel.PARTNER ->
                Decision.ALLOW to "Verified institution number"

            IdentityLevel.LIKELY ->
                if (identity.isCommunitySpam) {
                    when (prefs.getString("spam_action", "silence")) {
                        "reject" -> Decision.REJECT to "Likely spam — rejected (your setting)"
                        "allow" -> Decision.ALLOW to "Likely spam — ringing (your setting)"
                        else -> Decision.SILENCE to "Likely spam — silenced"
                    }
                } else {
                    Decision.ALLOW to "Likely a known institution — community consensus"
                }

            IdentityLevel.DISPUTED ->
                Decision.SILENCE to "Disputed identity — silenced while evidence conflicts"

            IdentityLevel.POSSIBLE_FINANCIAL ->
                Decision.SILENCE to "Possible financial caller — insufficient evidence to ring through"

            IdentityLevel.PERSONAL ->
                Decision.ALLOW to "Personal"

            IdentityLevel.UNKNOWN -> {
                val series = Phone.series(number)
                if (prefs.getBoolean("preset_140", true) && series?.series == Phone.Series.TELEMARKETER) {
                    Decision.SILENCE to "Default rule: telemarketer series (140) — silenced"
                } else if (prefs.getBoolean("preset_tollfree", false) && series?.series == Phone.Series.TOLL_FREE) {
                    Decision.SILENCE to "Default rule: unregistered toll-free number — silenced"
                } else {
                    when (prefs.getString("unknown_policy", "silence")) {
                        "ring" -> Decision.ALLOW to "Unknown caller — ringing (your setting)"
                        "reject" -> Decision.REJECT to "Unknown caller — rejected (your setting)"
                        else -> Decision.SILENCE to "Unknown caller — silenced (your setting)"
                    }
                }
            }
        }
    }

    companion object {
        fun actionOf(s: String): Decision = when (s) {
            "ALLOW" -> Decision.ALLOW
            "REJECT" -> Decision.REJECT
            else -> Decision.SILENCE
        }
    }
}
