package `in`.callsentry.app.core

object Phone {

    fun compact(raw: String): String = raw.filter { it in '0'..'9' }

    /**
     * Normalizes a raw caller number to its Indian national form
     * (10 significant digits, or a service number such as 1800112211).
     */
    fun digits(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var n = compact(raw.trim())
        if (n.isEmpty()) return null
        if (n.startsWith("00")) n = n.substring(2)
        if (n.length == 12 && n.startsWith("91")) n = n.substring(2)
        if (n.length == 11 && n.startsWith("0")) n = n.substring(1)
        return n
    }

    enum class Series { TOLL_FREE, TELEMARKETER, REGISTERED_BUSINESS }

    data class SeriesInfo(val series: Series, val label: String)

    fun series(n: String): SeriesInfo? = when {
        n.length == 10 && n.startsWith("140") ->
            SeriesInfo(Series.TELEMARKETER, "TRAI-registered telemarketer series (140)")
        n.length == 10 && n.startsWith("160") ->
            SeriesInfo(Series.REGISTERED_BUSINESS, "TRAI registered business series (160) — identity unconfirmed")
        n.startsWith("1800") || n.startsWith("1860") ->
            SeriesInfo(Series.TOLL_FREE, "Toll-free or service number")
        else -> null
    }

    private val emergency = setOf("112", "100", "101", "102", "108", "1091", "1098", "1073", "1911", "104")

    fun isEmergency(n: String): Boolean = n in emergency

    /**
     * Heuristic: does this normalized number look non-Indian?
     * Indian national numbers are 10 digits; service numbers start with 1
     * (1800/1860 toll-free are 11 digits, 140/160 are 10 digits).
     */
    fun isInternational(n: String): Boolean = when {
        n.length > 11 -> true
        n.length == 11 -> !(n.startsWith("1800") || n.startsWith("1860"))
        else -> false
    }

    fun pretty(n: String): String = when {
        n.length == 10 && !n.startsWith("1") -> "+91 ${n.substring(0, 5)} ${n.substring(5)}"
        n.length == 11 && (n.startsWith("1800") || n.startsWith("1860")) ->
            "${n.substring(0, 4)} ${n.substring(4, 7)} ${n.substring(7)}"
        n.length == 10 && n.startsWith("140") -> "${n.substring(0, 3)} ${n.substring(3, 6)} ${n.substring(6)}"
        else -> n
    }

    fun dialUri(n: String): String = if (n.length == 10 && !n.startsWith("1")) "+91$n" else n
}
