package `in`.callsentry.app

import android.app.Application
import android.content.SharedPreferences
import `in`.callsentry.app.data.Db

class CallSentryApp : Application() {

    lateinit var db: Db
        private set

    lateinit var prefs: SharedPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("callsentry", MODE_PRIVATE)
        db = Db(this)
    }
}
