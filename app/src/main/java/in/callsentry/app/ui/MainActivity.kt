package `in`.callsentry.app.ui

import android.app.role.RoleManager
import android.content.Intent
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
import `in`.callsentry.app.core.DecisionEngine
import `in`.callsentry.app.core.IdentityLevel
import `in`.callsentry.app.core.IdentityResolver
import `in`.callsentry.app.core.Phone
import `in`.callsentry.app.data.CallRecord
import `in`.callsentry.app.data.UserRule
import `in`.callsentry.app.databinding.ActivityMainBinding
import `in`.callsentry.app.databinding.ItemCallBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val app get() = application as CallSentryApp
    private val adapter = HistoryAdapter()

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    private data class HistRow(
        val number: String,
        val title: String,
        val type: Int,
        val date: Long,
        val rule: UserRule?,
        val screened: CallRecord?,
        val identity: CallerIdentity?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.cardRole.setOnClickListener { requestRole() }
        b.btnRole.setOnClickListener { requestRole() }

        b.rvCalls.layoutManager = LinearLayoutManager(this)
        b.rvCalls.adapter = adapter

        Nav.setup(this, b.bottomNav.root, R.id.navHome)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        ) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    private fun refresh() {
        val rm = getSystemService(RoleManager::class.java)
        val held = rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        b.toolbar.subtitle = if (held) "Protection is on" else null
        b.cardRole.visibility = if (held) View.GONE else View.VISIBLE

        Thread {
            val rows = loadHistory()
            runOnUiThread {
                b.shimmerCalls.visibility = View.GONE
                b.rvCalls.visibility = View.VISIBLE
                adapter.submit(rows)
                b.llEmptyCalls.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()

        if (adapter.itemCount == 0) {
            b.shimmerCalls.visibility = View.VISIBLE
            b.rvCalls.visibility = View.INVISIBLE
            b.llEmptyCalls.visibility = View.GONE
        }
    }

    private fun loadHistory(): List<HistRow> {
        val rules = app.db.rules()
            .filter { it.matchType == "EXACT" }
            .associateBy { Phone.compact(it.pattern) }
        val screened = HashMap<String, CallRecord>()
        for (r in app.db.recentCalls(500)) if (!screened.containsKey(r.phone)) screened[r.phone] = r

        val resolver = IdentityResolver(app.db)
        val contactsOk = Contacts.permitted(this)
        val identityCache = HashMap<String, CallerIdentity>()
        val contactCache = HashMap<String, String?>()
        val labelCache = HashMap<String, String?>()

        val rows = mutableListOf<HistRow>()
        try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE
                ),
                null, null,
                CallLog.Calls.DATE + " DESC"
            )?.use { c ->
                val iName = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val iNum = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val iDate = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val iType = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                while (c.moveToNext() && rows.size < 500) {
                    val raw = if (c.isNull(iNum)) null else c.getString(iNum)
                    val n = Phone.digits(raw) ?: continue
                    val date = if (c.isNull(iDate)) 0L else c.getLong(iDate)
                    val type = if (c.isNull(iType)) CallLog.Calls.INCOMING_TYPE else c.getInt(iType)
                    val cached = if (c.isNull(iName)) null else c.getString(iName)

                    val contact = contactCache.getOrPut(n) {
                        cached ?: if (contactsOk && raw != null) Contacts.lookup(this, raw) else null
                    }
                    val identity = identityCache.getOrPut(n) { resolver.resolve(n, contact) }
                    val label = labelCache.getOrPut(n) { app.db.labelFor(n) }

                    val title = contact ?: label ?: when (identity.level) {
                        IdentityLevel.UNKNOWN -> Phone.pretty(n)
                        else -> identity.name
                    }
                    rows += HistRow(n, title, type, date, rules[n], screened[n], identity)
                }
            }
        } catch (_: SecurityException) {
            // Call log permission not granted yet — scanner tab requests it.
        }
        return rows
    }

    private fun showActions(row: HistRow, position: Int) {
        val actions = mutableListOf(
            "Block this number" to { applyRule(row, position, "REJECT") },
            "Silence this number" to { applyRule(row, position, "SILENCE") },
            "Always allow" to { applyRule(row, position, "ALLOW") }
        )
        row.rule?.let {
            actions += "Remove rule (${Ui.actionName(DecisionEngine.actionOf(it.action))})" to
                { removeRule(row, position) }
        }
        actions += "Add your label" to { showLabelDialog(row) }
        row.screened?.let {
            actions += "Evidence & details" to {
                startActivity(
                    Intent(this, CallDetailActivity::class.java).putExtra("id", it.id)
                )
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(Phone.pretty(row.number))
            .setItems(actions.map { it.first }.toTypedArray()) { _, which ->
                actions[which].second()
            }
            .show()
    }

    private fun applyRule(row: HistRow, position: Int, action: String) {
        Thread {
            app.db.replaceExactRule(row.number, action)
            val newRule = app.db.rules().firstOrNull {
                it.matchType == "EXACT" && Phone.compact(it.pattern) == row.number
            }
            runOnUiThread {
                if (position < adapter.items.size) {
                    adapter.items[position] = row.copy(rule = newRule)
                    adapter.notifyItemChanged(position)
                }
                Toast.makeText(this, "Rule saved", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun removeRule(row: HistRow, position: Int) {
        val ruleId = row.rule?.id ?: return
        Thread {
            app.db.deleteRule(ruleId)
            runOnUiThread {
                if (position < adapter.items.size) {
                    adapter.items[position] = row.copy(rule = null)
                    adapter.notifyItemChanged(position)
                }
                Toast.makeText(this, "Rule removed", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun showLabelDialog(row: HistRow) {
        val input = EditText(this)
        input.hint = "Your label, e.g. \"My HDFC agent\""
        input.setText(app.db.labelFor(row.number) ?: "")
        MaterialAlertDialogBuilder(this)
            .setTitle("Your label")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val label = input.text.toString().trim()
                if (label.isNotEmpty()) {
                    Thread {
                        app.db.upsertLabel(row.number, label)
                        runOnUiThread { refresh() }
                    }.start()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.VH>() {

        val items = mutableListOf<HistRow>()

        fun submit(list: List<HistRow>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemCallBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = items[position]
            val ctx = holder.b.root.context

            val (arrow, arrowColor) = when (row.type) {
                CallLog.Calls.OUTGOING_TYPE -> "↑" to R.color.levelUnknown
                CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE ->
                    "↓" to R.color.actionReject
                else -> "↓" to R.color.actionAllow
            }
            holder.b.tvDir.text = arrow
            holder.b.tvDir.setTextColor(ContextCompat.getColor(ctx, arrowColor))

            holder.b.tvTitle.text = row.title
            holder.b.tvSub.text = Phone.pretty(row.number)
            holder.b.tvTime.text =
                if (row.date > 0) DateUtils.getRelativeTimeSpanString(ctx, row.date).toString() else ""

            val rule = row.rule
            val screened = row.screened
            val identity = row.identity
            when {
                rule != null -> {
                    val action = DecisionEngine.actionOf(rule.action)
                    Ui.badge(holder.b.tvBadge, Ui.actionName(action), Ui.actionColor(ctx, action))
                }
                screened != null -> {
                    val action = Ui.parseAction(screened.action)
                    Ui.badge(holder.b.tvBadge, Ui.actionName(action), Ui.actionColor(ctx, action))
                }
                identity != null && identity.level != IdentityLevel.UNKNOWN ->
                    Ui.badge(holder.b.tvBadge, Ui.levelName(identity.level), Ui.levelColor(ctx, identity.level))
                else -> holder.b.tvBadge.visibility = View.GONE
            }

            holder.b.root.setOnClickListener {
                showActions(row, holder.bindingAdapterPosition)
            }
        }

        inner class VH(val b: ItemCallBinding) : RecyclerView.ViewHolder(b.root)
    }
}
