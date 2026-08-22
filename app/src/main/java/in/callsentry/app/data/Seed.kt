package `in`.callsentry.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf

object Seed {

    fun run(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val day = 24L * 3600_000L

        fun inst(name: String, category: String, website: String): Long =
            db.insert(
                "institutions", null,
                contentValuesOf("name" to name, "category" to category, "website" to website)
            )

        fun num(instId: Long, phone: String, label: String): Long =
            db.insert(
                "official_numbers", null,
                contentValuesOf(
                    "institution_id" to instId,
                    "phone" to phone,
                    "label" to label,
                    "verification" to "OFFICIAL",
                    "note" to "Seed sample — verify on the institution's official website"
                )
            )

        fun rep(phone: String, verdict: String, guess: String?, daysAgo: Int): Long =
            db.insert(
                "community_reports", null,
                contentValuesOf(
                    "phone" to phone,
                    "verdict" to verdict,
                    "institution_guess" to guess,
                    "reported_at" to now - daysAgo * day,
                    "seed" to 1
                )
            )

        val sbi = inst("State Bank of India", "BANK", "sbi.co.in")
        val hdfc = inst("HDFC Bank", "BANK", "hdfcbank.com")
        val icici = inst("ICICI Bank", "BANK", "icicibank.com")
        val axis = inst("Axis Bank", "BANK", "axisbank.com")
        val kotak = inst("Kotak Mahindra Bank", "BANK", "kotak.com")
        val uidai = inst("UIDAI (Aadhaar)", "GOVERNMENT", "uidai.gov.in")

        num(sbi, "1800112211", "Customer care")
        num(hdfc, "1800221006", "Customer care")
        num(icici, "18601207777", "Customer care")
        num(axis, "18604195555", "Customer care")
        num(kotak, "18602662666", "Customer care")
        num(uidai, "1947", "Aadhaar helpline")

        // Sample community intelligence on clearly non-real numbers so the
        // different identity levels can be exercised in development.
        repeat(12) { rep("9876543210", "SPAM", null, 1 + it) }
        repeat(4) { rep("9876543210", "FRAUD", null, 2 + it) }
        repeat(8) { rep("9000000010", "LEGITIMATE", "ICICI Bank", 3 + it) }
        rep("9000000010", "SPAM", null, 5)
        repeat(4) { rep("9000000020", "SPAM", null, 4 + it) }
        repeat(4) { rep("9000000020", "LEGITIMATE", "Axis Bank", 6 + it) }
        repeat(2) { rep("9000000030", "FINANCIAL", "HDFC Bank", 7 + it) }
    }
}
