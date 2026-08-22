package `in`.callsentry.app.core

import `in`.callsentry.app.data.Db

/**
 * The seven product identity levels. CallSentry never presents a caller as
 * something the evidence does not support: unknown stays unknown.
 */
enum class IdentityLevel {
    OFFICIAL,           // exact match, officially verified registry
    PARTNER,            // exact match, partner-verified registry
    LIKELY,             // community consensus with transparent confidence
    PERSONAL,           // the user's own label or device contact
    POSSIBLE_FINANCIAL, // signals hint financial, evidence insufficient
    UNKNOWN,
    DISPUTED            // strong conflicting evidence
}

data class Evidence(val source: String, val statement: String)

data class CallerIdentity(
    val level: IdentityLevel,
    val name: String,
    val institutionId: Long? = null,
    val confidence: String? = null,
    val evidence: List<Evidence> = emptyList(),
    val isCommunitySpam: Boolean = false
)

class IdentityResolver(private val db: Db) {

    fun resolve(number: String, contactName: String? = null): CallerIdentity {
        val ev = mutableListOf<Evidence>()

        // 1. The user's own truth wins.
        if (!contactName.isNullOrBlank()) {
            ev += Evidence("Your contacts", "Saved in your contacts as \"$contactName\"")
            return CallerIdentity(IdentityLevel.PERSONAL, contactName, evidence = ev)
        }
        db.labelFor(number)?.let {
            ev += Evidence("Your labels", "You labelled this number \"$it\"")
            return CallerIdentity(IdentityLevel.PERSONAL, it, evidence = ev)
        }

        // 2. Exact registry match — the only path to a verified identity.
        db.findOfficial(number)?.let { (num, inst) ->
            val official = num.verification == "OFFICIAL"
            ev += Evidence(
                if (official) "Official registry" else if (num.verification == "PARTNER") "Partner registry" else "Your registry",
                "Exact number match: ${inst.name} — ${num.label}" +
                    (num.note?.let { n -> " ($n)" } ?: "")
            )
            return CallerIdentity(
                if (official) IdentityLevel.OFFICIAL else IdentityLevel.PARTNER,
                inst.name,
                institutionId = inst.id,
                evidence = ev
            )
        }

        // 3. Community intelligence — consensus only, with visible numbers.
        val stats = db.communityStats(number)
        val total = stats.total
        if (total >= 5) {
            val spamSide = stats.spam + stats.fraud
            val legitSide = stats.legitimate
            val confidence = if (total >= 10) "High" else "Moderate"
            when {
                legitSide.toDouble() / total >= 0.8 && stats.topGuess != null -> {
                    ev += Evidence(
                        "Community intelligence",
                        "$total reports: $legitSide identify ${stats.topGuess}, $spamSide report spam or fraud"
                    )
                    return CallerIdentity(
                        IdentityLevel.LIKELY,
                        "Likely ${stats.topGuess}",
                        confidence = "Confidence: $confidence — $legitSide of $total reports",
                        evidence = ev
                    )
                }
                spamSide.toDouble() / total >= 0.8 -> {
                    ev += Evidence(
                        "Community intelligence",
                        "$total reports: $spamSide report spam or fraud, $legitSide legitimate"
                    )
                    return CallerIdentity(
                        IdentityLevel.LIKELY,
                        "Likely spam or fraud",
                        confidence = "Confidence: $confidence — $spamSide of $total reports",
                        evidence = ev,
                        isCommunitySpam = true
                    )
                }
                spamSide >= total * 0.25 && legitSide >= total * 0.25 -> {
                    ev += Evidence(
                        "Community intelligence",
                        "Reports conflict: $spamSide spam/fraud vs $legitSide legitimate — no side reaches consensus"
                    )
                    return CallerIdentity(IdentityLevel.DISPUTED, "Conflicting community reports", evidence = ev)
                }
            }
        } else if (total in 1..4 && stats.financial > 0 && stats.topGuess != null) {
            ev += Evidence(
                "Community intelligence",
                "$total report(s) mention ${stats.topGuess} — below the consensus threshold"
            )
            return CallerIdentity(
                IdentityLevel.POSSIBLE_FINANCIAL,
                "Possible: ${stats.topGuess}",
                confidence = "Low confidence — $total report(s)",
                evidence = ev
            )
        } else if (total > 0) {
            ev += Evidence("Community intelligence", "$total report(s) — not enough evidence to identify")
        }

        // 4. Regulatory series hints — category knowledge, never identity.
        Phone.series(number)?.let {
            ev += Evidence("Number-series policy (TRAI)", it.label)
            if (it.series == Phone.Series.REGISTERED_BUSINESS) {
                return CallerIdentity(
                    IdentityLevel.POSSIBLE_FINANCIAL,
                    "Registered business (identity unconfirmed)",
                    evidence = ev
                )
            }
        }

        // 5. Unknown stays unknown.
        return CallerIdentity(IdentityLevel.UNKNOWN, "Unknown caller", evidence = ev)
    }
}
