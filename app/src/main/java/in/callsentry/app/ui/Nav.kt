package `in`.callsentry.app.ui

import android.content.Context
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView
import `in`.callsentry.app.R

object Nav {
    fun setup(context: Context, nav: BottomNavigationView, current: Int) {
        nav.selectedItemId = current
        nav.setOnItemSelectedListener { item ->
            if (item.itemId == current) return@setOnItemSelectedListener true
            val target = when (item.itemId) {
                R.id.navScan -> CallLogReviewActivity::class.java
                R.id.navRules -> RulesActivity::class.java
                R.id.navBanks -> InstitutionsActivity::class.java
                R.id.navSettings -> SettingsActivity::class.java
                else -> MainActivity::class.java
            }
            context.startActivity(
                Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
            true
        }
    }
}
