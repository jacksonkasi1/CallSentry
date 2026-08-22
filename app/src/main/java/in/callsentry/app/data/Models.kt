package `in`.callsentry.app.data

import `in`.callsentry.app.core.Phone

data class Institution(
    val id: Long,
    val name: String,
    val category: String,
    val website: String?
)

data class OfficialNumber(
    val id: Long,
    val institutionId: Long,
    val phone: String,
    val label: String,
    val verification: String, // OFFICIAL | PARTNER | SELF
    val note: String?
)

data class UserRule(
    val id: Long,
    val pattern: String,
    val matchType: String, // EXACT | PREFIX
    val action: String,    // ALLOW | SILENCE | REJECT
    val label: String?
) {
    fun matches(number: String): Boolean {
        val p = Phone.compact(pattern)
        return if (matchType == "PREFIX") number.startsWith(p) else number == p
    }
}

data class UserLabel(val id: Long, val phone: String, val label: String)

data class CommunityStats(
    val total: Int,
    val spam: Int,
    val fraud: Int,
    val legitimate: Int,
    val financial: Int,
    val topGuess: String?,
    val topGuessCount: Int
)

data class CallRecord(
    val id: Long,
    val ts: Long,
    val phone: String,
    val identityLevel: String,
    val identityName: String?,
    val action: String,
    val reason: String,
    val evidence: String
)
