package `in`.callsentry.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import `in`.callsentry.app.core.Phone

class Db(context: Context) : SQLiteOpenHelper(context, "callsentry.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE institutions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'OTHER',
                website TEXT)"""
        )
        db.execSQL(
            """CREATE TABLE official_numbers(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                institution_id INTEGER NOT NULL REFERENCES institutions(id),
                phone TEXT NOT NULL,
                label TEXT NOT NULL DEFAULT '',
                verification TEXT NOT NULL DEFAULT 'OFFICIAL',
                note TEXT)"""
        )
        db.execSQL(
            """CREATE TABLE user_rules(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pattern TEXT NOT NULL,
                match_type TEXT NOT NULL DEFAULT 'EXACT',
                action TEXT NOT NULL DEFAULT 'SILENCE',
                label TEXT,
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE relationships(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                institution_id INTEGER NOT NULL REFERENCES institutions(id),
                declared_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE user_labels(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                phone TEXT NOT NULL UNIQUE,
                label TEXT NOT NULL,
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE community_reports(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                phone TEXT NOT NULL,
                verdict TEXT NOT NULL,
                institution_guess TEXT,
                reported_at INTEGER NOT NULL,
                seed INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE calls(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                phone TEXT NOT NULL,
                identity_level TEXT NOT NULL,
                identity_name TEXT,
                action TEXT NOT NULL,
                reason TEXT NOT NULL,
                evidence TEXT NOT NULL DEFAULT '')"""
        )
        db.execSQL("CREATE INDEX idx_numbers_phone ON official_numbers(phone)")
        db.execSQL("CREATE INDEX idx_reports_phone ON community_reports(phone)")
        db.execSQL("CREATE INDEX idx_calls_ts ON calls(ts)")
        Seed.run(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 — nothing to migrate yet.
    }

    private fun <T> SQLiteDatabase.map(sql: String, args: Array<String> = arrayOf(), f: (Cursor) -> T): List<T> {
        rawQuery(sql, args).use { c ->
            val out = ArrayList<T>()
            while (c.moveToNext()) out.add(f(c))
            return out
        }
    }

    // ---------- Institutions & official numbers ----------

    fun institutions(): List<Institution> = readableDatabase.map(
        "SELECT id,name,category,website FROM institutions ORDER BY name"
    ) { c ->
        Institution(c.getLong(0), c.getString(1), c.getString(2), if (c.isNull(3)) null else c.getString(3))
    }

    fun institution(id: Long): Institution? = readableDatabase.map(
        "SELECT id,name,category,website FROM institutions WHERE id=?", arrayOf(id.toString())
    ) { c ->
        Institution(c.getLong(0), c.getString(1), c.getString(2), if (c.isNull(3)) null else c.getString(3))
    }.firstOrNull()

    fun addInstitution(name: String, category: String, website: String?): Long {
        val cv = ContentValues()
        cv.put("name", name)
        cv.put("category", category)
        website?.let { cv.put("website", it) }
        return writableDatabase.insert("institutions", null, cv)
    }

    fun numbersFor(institutionId: Long): List<OfficialNumber> = readableDatabase.map(
        "SELECT id,institution_id,phone,label,verification,note FROM official_numbers WHERE institution_id=? ORDER BY phone",
        arrayOf(institutionId.toString())
    ) { c ->
        OfficialNumber(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), if (c.isNull(5)) null else c.getString(5))
    }

    fun numberCount(institutionId: Long): Int = readableDatabase.map(
        "SELECT COUNT(*) FROM official_numbers WHERE institution_id=?", arrayOf(institutionId.toString())
    ) { it.getInt(0) }.firstOrNull() ?: 0

    fun addNumber(institutionId: Long, phone: String, label: String, verification: String = "SELF"): Long {
        val cv = ContentValues()
        cv.put("institution_id", institutionId)
        cv.put("phone", phone)
        cv.put("label", label)
        cv.put("verification", verification)
        return writableDatabase.insert("official_numbers", null, cv)
    }

    fun findOfficial(phone: String): Pair<OfficialNumber, Institution>? {
        readableDatabase.rawQuery(
            """SELECT o.id,o.institution_id,o.phone,o.label,o.verification,o.note,
                      i.id,i.name,i.category,i.website
               FROM official_numbers o JOIN institutions i ON i.id = o.institution_id
               WHERE o.phone = ? LIMIT 1""",
            arrayOf(phone)
        ).use { c ->
            if (!c.moveToFirst()) return null
            return Pair(
                OfficialNumber(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), if (c.isNull(5)) null else c.getString(5)),
                Institution(c.getLong(6), c.getString(7), c.getString(8), if (c.isNull(9)) null else c.getString(9))
            )
        }
    }

    // ---------- User labels ----------

    fun labelFor(phone: String): String? = readableDatabase.map(
        "SELECT label FROM user_labels WHERE phone=?", arrayOf(phone)
    ) { it.getString(0) }.firstOrNull()

    fun upsertLabel(phone: String, label: String) {
        val cv = ContentValues()
        cv.put("phone", phone)
        cv.put("label", label)
        cv.put("created_at", System.currentTimeMillis())
        writableDatabase.insertWithOnConflict("user_labels", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------- Rules ----------

    fun rules(): List<UserRule> = readableDatabase.map(
        "SELECT id,pattern,match_type,action,label FROM user_rules ORDER BY id DESC"
    ) { c ->
        UserRule(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), if (c.isNull(4)) null else c.getString(4))
    }

    fun addRule(pattern: String, matchType: String, action: String, label: String?): Long {
        val cv = ContentValues()
        cv.put("pattern", Phone.compact(pattern))
        cv.put("match_type", matchType)
        cv.put("action", action)
        label?.let { cv.put("label", it) }
        cv.put("created_at", System.currentTimeMillis())
        return writableDatabase.insert("user_rules", null, cv)
    }

    fun deleteRule(id: Long) {
        writableDatabase.delete("user_rules", "id=?", arrayOf(id.toString()))
    }

    fun replaceExactRule(phone: String, action: String) {
        writableDatabase.delete("user_rules", "pattern=? AND match_type='EXACT'", arrayOf(phone))
        addRule(phone, "EXACT", action, null)
    }

    // ---------- Relationships ----------

    fun hasRelationship(institutionId: Long): Boolean = readableDatabase.map(
        "SELECT 1 FROM relationships WHERE institution_id=?", arrayOf(institutionId.toString())
    ) { 1 }.isNotEmpty()

    fun toggleRelationship(institutionId: Long) {
        if (hasRelationship(institutionId)) {
            writableDatabase.delete("relationships", "institution_id=?", arrayOf(institutionId.toString()))
        } else {
            val cv = ContentValues()
            cv.put("institution_id", institutionId)
            cv.put("declared_at", System.currentTimeMillis())
            writableDatabase.insert("relationships", null, cv)
        }
    }

    // ---------- Community ----------

    fun communityStats(phone: String): CommunityStats {
        var spam = 0; var fraud = 0; var legit = 0; var fin = 0
        readableDatabase.map(
            "SELECT verdict,COUNT(*) FROM community_reports WHERE phone=? GROUP BY verdict",
            arrayOf(phone)
        ) { c ->
            when (c.getString(0)) {
                "SPAM" -> spam = c.getInt(1)
                "FRAUD" -> fraud = c.getInt(1)
                "LEGITIMATE" -> legit = c.getInt(1)
                "FINANCIAL" -> fin = c.getInt(1)
            }
        }
        var top: String? = null
        var topCount = 0
        readableDatabase.rawQuery(
            """SELECT institution_guess, COUNT(*) c FROM community_reports
               WHERE phone=? AND institution_guess IS NOT NULL
               GROUP BY institution_guess ORDER BY c DESC LIMIT 1""",
            arrayOf(phone)
        ).use { c ->
            if (c.moveToFirst()) {
                top = c.getString(0)
                topCount = c.getInt(1)
            }
        }
        return CommunityStats(spam + fraud + legit + fin, spam, fraud, legit, fin, top, topCount)
    }

    // ---------- Calls ----------

    fun insertCall(
        ts: Long, phone: String, level: String, name: String?,
        action: String, reason: String, evidence: String
    ): Long {
        val cv = ContentValues()
        cv.put("ts", ts)
        cv.put("phone", phone)
        cv.put("identity_level", level)
        name?.let { cv.put("identity_name", it) }
        cv.put("action", action)
        cv.put("reason", reason)
        cv.put("evidence", evidence)
        return writableDatabase.insert("calls", null, cv)
    }

    fun recentCalls(limit: Int = 100): List<CallRecord> = readableDatabase.map(
        "SELECT id,ts,phone,identity_level,identity_name,action,reason,evidence FROM calls ORDER BY ts DESC LIMIT $limit"
    ) { c ->
        CallRecord(
            c.getLong(0), c.getLong(1), c.getString(2), c.getString(3),
            if (c.isNull(4)) null else c.getString(4), c.getString(5), c.getString(6), c.getString(7)
        )
    }

    fun callById(id: Long): CallRecord? = readableDatabase.map(
        "SELECT id,ts,phone,identity_level,identity_name,action,reason,evidence FROM calls WHERE id=?",
        arrayOf(id.toString())
    ) { c ->
        CallRecord(
            c.getLong(0), c.getLong(1), c.getString(2), c.getString(3),
            if (c.isNull(4)) null else c.getString(4), c.getString(5), c.getString(6), c.getString(7)
        )
    }.firstOrNull()

    /** Returns (allowed, silenced, rejected). */
    fun stats(): Triple<Int, Int, Int> {
        var a = 0; var s = 0; var r = 0
        readableDatabase.map("SELECT action,COUNT(*) FROM calls GROUP BY action") { c ->
            when (c.getString(0)) {
                "ALLOW" -> a = c.getInt(1)
                "SILENCE" -> s = c.getInt(1)
                "REJECT" -> r = c.getInt(1)
            }
        }
        return Triple(a, s, r)
    }
}
