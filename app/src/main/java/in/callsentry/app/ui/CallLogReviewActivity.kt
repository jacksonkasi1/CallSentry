package `in`.callsentry.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import `in`.callsentry.app.CallSentryApp
import `in`.callsentry.app.R
import `in`.callsentry.app.core.CallerIdentity
import `in`.callsentry.app.core.Contacts
import `in`.callsentry.app.core.IdentityResolver
import `in`.callsentry.app.core.Phone
import `in`.callsentry.app.data.UserRule
import `in`.callsentry.app.databinding.ActivityCallLogBinding
import `in`.callsentry.app.databinding.ItemLogEntryBinding

class CallLogReviewActivity : AppCompatActivity() {

    private lateinit var b: ActivityCallLogBinding
    private val app get() = application as CallSentryApp
    private val adapter = LogAdapter()

    private val perms = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS
    )

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) load()
            else showEmpty("CallSentry needs the call log and contacts permissions to find unsaved callers. Tap Scan again to grant them.")
        }

    private class Entry(
        val number: String,
        val raw: String,
        var count: Int = 0,
        var last: Long = 0,
        var incoming: Int = 0,
        var outgoing: Int = 0,
        var missed: Int = 0,
        var identity: CallerIdentity? = null,
        var label: String? = null,
        var rule: UserRule? = null
    ) {
        /** True when the user has never dialled this number themselves. */
        val onlyCalledYou: Boolean get() = outgoing == 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCallLogBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationIcon(R.drawable.ic_back)
        b.toolbar.setNavigationOnClickListener { finish() }

        b.rvLog.layoutManager = LinearLayoutManager(this)
        b.rvLog.adapter = adapter

        b.toolbar.inflateMenu(R.menu.menu_scan)
        b.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_refresh) {
                if (hasPerms()) load() else permLauncher.launch(perms)
                true
            } else false
        }

        if (hasPerms()) load() else permLauncher.launch(perms)
    }

    override fun onResume() {
        super.onResume()
        Nav.setup(this, b.bottomNav.root, R.id.navScan)
    }

    private fun hasPerms(): Boolean = perms.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun showEmpty(msg: String) {
        adapter.submit(emptyList())
        b.shimmerLog.visibility = android.view.View.GONE
        b.rvLog.visibility = android.view.View.VISIBLE
        b.tvEmptyLog.text = msg
        b.tvEmptyLog.visibility = android.view.View.VISIBLE
    }

    private fun load() {
        b.tvEmptyLog.visibility = android.view.View.GONE
        if (adapter.itemCount == 0) {
            b.shimmerLog.visibility = android.view.View.VISIBLE
            b.rvLog.visibility = android.view.View.INVISIBLE
        }
        Thread {
            val entries = scan()
            runOnUiThread {
                b.shimmerLog.visibility = android.view.View.GONE
                b.rvLog.visibility = android.view.View.VISIBLE
                adapter.submit(entries)
                if (entries.isEmpty()) showEmpty("No unsaved numbers found in your call log.")
            }
        }.start()
    }

    private fun scan(): List<Entry> {
        val map = LinkedHashMap<String, Entry>()
        try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
                null, null,
                CallLog.Calls.DATE + " DESC"
            )?.use { c ->
                val iNum = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val iDate = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val iType = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                while (c.moveToNext()) {
                    val raw = if (c.isNull(iNum)) null else c.getString(iNum)
                    val n = Phone.digits(raw) ?: continue
                    if (Phone.isEmergency(n)) continue
                    val date = if (c.isNull(iDate)) 0L else c.getLong(iDate)
                    val e = map.getOrPut(n) { Entry(n, raw ?: n) }
                    e.count++
                    if (date > e.last) e.last = date
                    when (if (c.isNull(iType)) -1 else c.getInt(iType)) {
                        CallLog.Calls.OUTGOING_TYPE -> e.outgoing++
                        CallLog.Calls.MISSED_TYPE -> e.missed++
                        else -> e.incoming++
                    }
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }

        val resolver = IdentityResolver(app.db)
        val contactsOn = Contacts.permitted(this)
        val result = mutableListOf<Entry>()
        for (e in map.values) {
            if (contactsOn && Contacts.lookup(this, e.raw) != null) continue
            e.identity = resolver.resolve(e.number, null)
            e.label = app.db.labelFor(e.number)
            e.rule = app.db.rules().firstOrNull {
                it.matchType == "EXACT" && Phone.compact(it.pattern) == e.number
            }
            result += e
            if (result.size >= 300) break
        }
        return result.sortedWith(
            compareByDescending<Entry> { it.onlyCalledYou }
                .thenByDescending { it.last }
        )
    }

    private fun showActions(e: Entry, position: Int) {
        val actionNames = mapOf("ALLOW" to "Allowed", "SILENCE" to "Silenced", "REJECT" to "Blocked")
        val options = mutableListOf(
            "Block this number",
            "Silence this number",
            "Always allow this number",
            "Add your label"
        )
        if (e.rule != null) {
            options += "Remove existing rule (${actionNames[e.rule!!.action]})"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(Phone.pretty(e.number))
            .setMessage(
                buildString {
                    append(
                        if (e.outgoing > 0)
                            "You have called this number ${e.outgoing} time(s), so it is likely known to you."
                        else
                            "This number has only ever called you — you have never dialled it. Be cautious of unverified financial claims."
                    )
                    if (e.last > 0) {
                        append("\nLast call: ")
                        append(DateUtils.getRelativeTimeSpanString(this@CallLogReviewActivity, e.last))
                    }
                }
            )
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> applyRule(e, position, "REJECT")
                    1 -> applyRule(e, position, "SILENCE")
                    2 -> applyRule(e, position, "ALLOW")
                    3 -> showLabelDialog(e, position)
                    4 -> {
                        val ruleId = e.rule?.id ?: return@setItems
                        Thread {
                            app.db.deleteRule(ruleId)
                            e.rule = null
                            runOnUiThread {
                                adapter.notifyItemChanged(position)
                                Toast.makeText(this, "Rule removed", Toast.LENGTH_SHORT).show()
                            }
                        }.start()
                    }
                }
            }
            .show()
    }

    private fun applyRule(e: Entry, position: Int, action: String) {
        Thread {
            app.db.replaceExactRule(e.number, action)
            e.rule = app.db.rules().firstOrNull {
                it.matchType == "EXACT" && Phone.compact(it.pattern) == e.number
            }
            runOnUiThread {
                adapter.notifyItemChanged(position)
                Toast.makeText(this, "Rule saved for ${Phone.pretty(e.number)}", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun showLabelDialog(e: Entry, position: Int) {
        val input = EditText(this)
        input.hint = "Your label, e.g. \"My HDFC agent\""
        input.setText(e.label ?: "")
        MaterialAlertDialogBuilder(this)
            .setTitle("Your label")
            .setMessage("A personal label overrides community guesses. It stays on this device.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val label = input.text.toString().trim()
                if (label.isNotEmpty()) {
                    Thread {
                        app.db.upsertLabel(e.number, label)
                        e.label = label
                        runOnUiThread {
                            adapter.notifyItemChanged(position)
                            Toast.makeText(this, "Label saved", Toast.LENGTH_SHORT).show()
                        }
                    }.start()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private inner class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

        private val items = mutableListOf<Entry>()

        fun submit(list: List<Entry>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemLogEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            val ctx = holder.b.root.context
            holder.b.tvLogNumber.text = Phone.pretty(e.number)

            val meta = StringBuilder()
            meta.append(e.count).append(if (e.count == 1) " call" else " calls")
            if (e.missed > 0) {
                meta.append(" · ").append(e.missed).append(" missed")
            }
            meta.append(
                if (e.outgoing > 0) " · you called ${e.outgoing}×"
                else " · never called by you"
            )
            e.label?.let { meta.append(" · ").append(it) }
            holder.b.tvLogMeta.text = meta.toString()

            val rule = e.rule
            if (rule != null) {
                val action = `in`.callsentry.app.core.DecisionEngine.actionOf(rule.action)
                Ui.badge(holder.b.tvLogBadge, "Rule: ${Ui.actionName(action)}", Ui.actionColor(ctx, action))
            } else {
                val level = e.identity?.level ?: `in`.callsentry.app.core.IdentityLevel.UNKNOWN
                Ui.badge(holder.b.tvLogBadge, Ui.levelName(level), Ui.levelColor(ctx, level))
            }

            holder.b.root.setOnClickListener { showActions(e, holder.bindingAdapterPosition) }
        }

        inner class VH(val b: ItemLogEntryBinding) : RecyclerView.ViewHolder(b.root)
    }
}
