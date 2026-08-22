package `in`.callsentry.app.core

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object Contacts {

    fun permitted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun lookup(context: Context, rawNumber: String): String? {
        if (!permitted(context)) return null
        val target = Phone.digits(rawNumber) ?: return null
        val uri: Uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(rawNumber)
        )
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.NORMALIZED_NUMBER
            ),
            null, null, null
        )?.use { c: Cursor ->
            while (c.moveToNext()) {
                val norm = if (c.isNull(1)) null else c.getString(1)
                if (norm == null || Phone.digits(norm) == target) {
                    val name = if (c.isNull(0)) null else c.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return null
    }
}
