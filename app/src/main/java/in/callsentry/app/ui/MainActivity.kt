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
import androidx.core.content.ContextCompat
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

        b.tgPolicy.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val value = when (checkedId) {
                R.id.btnRing -> "ring"
                R.id.btnReject -> "reject"
                else -> "silence"
            }
            app.prefs.edit().putString("unknown_policy", value).apply()
        }

        b.cardScan.setOnClickListener { startActivity(Intent(this, CallLogReviewActivity::class.java)) }
        b.cardRules.setOnClickListener { startActivity(Intent(this, RulesActivity::class.java)) }
        b.cardInstitutions.setOnClickListener { startActivity(Intent(this, InstitutionsActivity::class.java)) }
        b.cardSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

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
        val okColor = ContextCompat.getColor(this, R.color.levelOfficial)
        val offColor = ContextCompat.getColor(this, R.color.levelUnknown)

        b.tvStatusTitle.text = if (held) "Protection is on" else "Protection is off"
        b.tvStatusDetail.text =
            if (held) "Calls are checked on this device before ringing"
            else "Tap below to make CallSentry your screener"
        b.btnRole.visibility = if (held) View.GONE else View.VISIBLE
        b.ivShield.setColorFilter(if (held) okColor else offColor)
        b.ivShield.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, if (held) R.color.circleGreen else R.color.circleGray)
            )

        when (app.prefs.getString("unknown_policy", "silence")) {
            "ring" -> b.tgPolicy.check(R.id.btnRing)
            "reject" -> b.tgPolicy.check(R.id.btnReject)
            else -> b.tgPolicy.check(R.id.btnSilence)
        }

        Thread {
            val calls = app.db.recentCalls(50)
            val (allowed, silenced, blocked) = app.db.stats()
            runOnUiThread {
                val total = allowed + silenced + blocked
                b.tvStats.visibility = if (total == 0) View.INVISIBLE else View.VISIBLE
                b.tvStats.text = "$total screened · $silenced silenced · $blocked blocked"
                adapter.submit(calls)
                b.llEmptyCalls.visibility = if (calls.isEmpty()) View.VISIBLE else View.GONE
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
