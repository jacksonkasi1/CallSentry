package `in`.callsentry.app.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import `in`.callsentry.app.CallSentryApp
import `in`.callsentry.app.R
import `in`.callsentry.app.core.Phone
import `in`.callsentry.app.data.CallRecord
import `in`.callsentry.app.databinding.ActivityMainBinding
import `in`.callsentry.app.databinding.ItemCallBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val app get() = application as CallSentryApp
    private val adapter = CallsAdapter()

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnRole.setOnClickListener {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            ) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            }
        }

        b.rgPolicy.setOnCheckedChangeListener { _, id ->
            val value = when (id) {
                R.id.rbRing -> "ring"
                R.id.rbReject -> "reject"
                else -> "silence"
            }
            app.prefs.edit().putString("unknown_policy", value).apply()
        }

        b.btnRules.setOnClickListener { startActivity(Intent(this, RulesActivity::class.java)) }
        b.btnInstitutions.setOnClickListener { startActivity(Intent(this, InstitutionsActivity::class.java)) }
        b.btnRelationships.setOnClickListener { startActivity(Intent(this, RelationshipsActivity::class.java)) }
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.btnScanLog.setOnClickListener { startActivity(Intent(this, CallLogReviewActivity::class.java)) }

        b.rvCalls.layoutManager = LinearLayoutManager(this)
        b.rvCalls.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val rm = getSystemService(RoleManager::class.java)
        val held = rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        b.tvStatusTitle.text = if (held) "Protection is on" else "Protection is off"
        b.tvStatusDetail.text = if (held) {
            "CallSentry is your default call screener. Incoming calls are checked on this device before your phone rings."
        } else {
            "Set CallSentry as your default call screening app so it can decide who gets through."
        }
        b.btnRole.visibility = if (held) View.GONE else View.VISIBLE

        when (app.prefs.getString("unknown_policy", "silence")) {
            "ring" -> b.rbRing.isChecked = true
            "reject" -> b.rbReject.isChecked = true
            else -> b.rbSilence.isChecked = true
        }

        Thread {
            val calls = app.db.recentCalls(50)
            val (allowed, silenced, blocked) = app.db.stats()
            runOnUiThread {
                b.tvStatScreened.text = (allowed + silenced + blocked).toString()
                b.tvStatSilenced.text = silenced.toString()
                b.tvStatBlocked.text = blocked.toString()
                adapter.submit(calls)
                b.tvEmptyCalls.visibility = if (calls.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private inner class CallsAdapter : RecyclerView.Adapter<CallsAdapter.VH>() {

        private val items = mutableListOf<CallRecord>()

        fun submit(list: List<CallRecord>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemCallBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val record = items[position]
            val level = Ui.parseLevel(record.identityLevel)
            val action = Ui.parseAction(record.action)
            holder.b.tvCallNumber.text = Phone.pretty(record.phone)
            holder.b.tvCallMeta.text =
                "${Ui.actionName(action)} · ${DateUtils.getRelativeTimeSpanString(this@MainActivity, record.ts)}"
            Ui.badge(holder.b.tvCallBadge, Ui.levelName(level), Ui.levelColor(this@MainActivity, level))
            holder.b.root.setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, CallDetailActivity::class.java)
                        .putExtra("id", record.id)
                )
            }
        }

        inner class VH(val b: ItemCallBinding) : RecyclerView.ViewHolder(b.root)
    }
}
