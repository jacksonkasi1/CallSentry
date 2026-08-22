package `in`.callsentry.app.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
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
import `in`.callsentry.app.core.DecisionEngine
import `in`.callsentry.app.core.Phone
import `in`.callsentry.app.data.CallRecord
import `in`.callsentry.app.data.UserRule
import `in`.callsentry.app.databinding.ActivityMainBinding
import `in`.callsentry.app.databinding.ItemActivityBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val app get() = application as CallSentryApp
    private val adapter = ActivityAdapter()

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    private data class ActRow(val record: CallRecord, val rule: UserRule?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnRole.setOnClickListener { requestRole() }
        b.btnScan.setOnClickListener { startActivity(Intent(this, CallLogReviewActivity::class.java)) }
        b.btnSafe.setOnClickListener { startActivity(Intent(this, InstitutionsActivity::class.java)) }

        b.rvActivity.layoutManager = LinearLayoutManager(this)
        b.rvActivity.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        Nav.setup(this, b.bottomNav.root, R.id.navHome)
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
        val okColor = ContextCompat.getColor(this, R.color.levelOfficial)
        val offColor = ContextCompat.getColor(this, R.color.levelUnknown)

        b.tvHeroTitle.text = if (held) "You're protected" else "You're not protected"
        b.btnRole.visibility = if (held) View.GONE else View.VISIBLE
        b.ivShield.setColorFilter(if (held) okColor else offColor)
        b.ivShield.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, if (held) R.color.circleGreen else R.color.circleGray)
        )

        if (adapter.itemCount == 0) {
            b.shimmerActivity.visibility = View.VISIBLE
            b.rvActivity.visibility = View.INVISIBLE
            b.llEmpty.visibility = View.GONE
        }

        Thread {
            val (allowed, silenced, blocked) = app.db.stats()
            val rules = app.db.rules()
                .filter { it.matchType == "EXACT" }
                .associateBy { Phone.compact(it.pattern) }
            val rows = app.db.recentCalls(50).map { ActRow(it, rules[it.phone]) }
            val total = allowed + silenced + blocked
            runOnUiThread {
                b.shimmerActivity.visibility = View.GONE
                b.rvActivity.visibility = View.VISIBLE
                b.tvStatBlocked.text = blocked.toString()
                b.tvStatBlocked.setTextColor(ContextCompat.getColor(this, R.color.actionReject))
                b.tvStatSilenced.text = silenced.toString()
                b.tvStatSilenced.setTextColor(ContextCompat.getColor(this, R.color.actionSilence))
                b.tvStatChecked.text = total.toString()
                b.tvHeroSub.text =
                    if (total == 0) "Watching your incoming calls on this device"
                    else "$total calls checked for you · $blocked blocked"
                adapter.submit(rows)
                b.llEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun showActions(row: ActRow, position: Int) {
        val actions = mutableListOf(
            "Block this number" to { applyRule(row, position, "REJECT") },
            "Silence this number" to { applyRule(row, position, "SILENCE") },
            "Always allow" to { applyRule(row, position, "ALLOW") }
        )
        row.rule?.let {
            actions += "Remove rule (${Ui.actionName(DecisionEngine.actionOf(it.action))})" to
                { removeRule(row, position) }
        }
        actions += "Add your label" to { showLabelDialog(row.record.phone) }
        actions += "Evidence & details" to {
            startActivity(
                Intent(this, CallDetailActivity::class.java).putExtra("id", row.record.id)
            )
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(Phone.pretty(row.record.phone))
            .setItems(actions.map { it.first }.toTypedArray()) { _, which ->
                actions[which].second()
            }
            .show()
    }

    private fun applyRule(row: ActRow, position: Int, action: String) {
        Thread {
            app.db.replaceExactRule(row.record.phone, action)
            val newRule = app.db.rules().firstOrNull {
                it.matchType == "EXACT" && Phone.compact(it.pattern) == row.record.phone
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

    private fun removeRule(row: ActRow, position: Int) {
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

    private fun showLabelDialog(phone: String) {
        val input = EditText(this)
        input.hint = "Your label, e.g. \"My HDFC agent\""
        input.setText(app.db.labelFor(phone) ?: "")
        MaterialAlertDialogBuilder(this)
            .setTitle("Your label")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val label = input.text.toString().trim()
                if (label.isNotEmpty()) {
                    Thread {
                        app.db.upsertLabel(phone, label)
                        runOnUiThread { refresh() }
                    }.start()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private inner class ActivityAdapter : RecyclerView.Adapter<ActivityAdapter.VH>() {

        val items = mutableListOf<ActRow>()

        fun submit(list: List<ActRow>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemActivityBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = items[position]
            val ctx = holder.b.root.context
            val record = row.record

            val action = Ui.parseAction(record.action)
            Ui.badge(holder.b.tvActBadge, Ui.actionName(action), Ui.actionColor(ctx, action))

            holder.b.tvActTitle.text = record.identityName ?: Phone.pretty(record.phone)
            holder.b.tvActSub.text = Phone.pretty(record.phone)
            holder.b.tvActTime.text =
                DateUtils.getRelativeTimeSpanString(ctx, record.ts).toString()

            holder.b.root.setOnClickListener {
                showActions(row, holder.bindingAdapterPosition)
            }
        }

        inner class VH(val b: ItemActivityBinding) : RecyclerView.ViewHolder(b.root)
    }
}
