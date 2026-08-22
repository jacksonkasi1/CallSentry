package `in`.callsentry.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import `in`.callsentry.app.CallSentryApp
import `in`.callsentry.app.R
import `in`.callsentry.app.core.Contacts
import `in`.callsentry.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private val app get() = application as CallSentryApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationIcon(R.drawable.ic_back)
        b.toolbar.setNavigationOnClickListener { finish() }

        b.rgUnknown.setOnCheckedChangeListener { _, id ->
            val value = when (id) {
                R.id.rbUnkRing -> "ring"
                R.id.rbUnkReject -> "reject"
                else -> "silence"
            }
            app.prefs.edit().putString("unknown_policy", value).apply()
        }

        b.rgSpam.setOnCheckedChangeListener { _, id ->
            val value = when (id) {
                R.id.rbSpamAllow -> "allow"
                R.id.rbSpamReject -> "reject"
                else -> "silence"
            }
            app.prefs.edit().putString("spam_action", value).apply()
        }

        b.swContacts.setOnCheckedChangeListener { _, checked ->
            app.prefs.edit().putBoolean("contacts_allow", checked).apply()
            if (checked && !Contacts.permitted(this)) {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 100)
            }
        }

        sync()
    }

    override fun onResume() {
        super.onResume()
        sync()
    }

    private fun sync() {
        when (app.prefs.getString("unknown_policy", "silence")) {
            "ring" -> b.rbUnkRing.isChecked = true
            "reject" -> b.rbUnkReject.isChecked = true
            else -> b.rbUnkSilence.isChecked = true
        }
        when (app.prefs.getString("spam_action", "silence")) {
            "allow" -> b.rbSpamAllow.isChecked = true
            "reject" -> b.rbSpamReject.isChecked = true
            else -> b.rbSpamSilence.isChecked = true
        }
        b.swContacts.isChecked =
            app.prefs.getBoolean("contacts_allow", true) ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            app.prefs.edit().putBoolean("contacts_allow", granted).apply()
            b.swContacts.isChecked = granted
        }
    }
}
