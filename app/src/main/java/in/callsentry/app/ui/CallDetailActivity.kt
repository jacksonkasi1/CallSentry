package `in`.callsentry.app.ui

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import `in`.callsentry.app.CallSentryApp
import `in`.callsentry.app.R
import `in`.callsentry.app.core.CallerIdentity
import `in`.callsentry.app.core.Contacts
import `in`.callsentry.app.core.Decision
import `in`.callsentry.app.core.DecisionEngine
import `in`.callsentry.app.core.Evidence
import `in`.callsentry.app.core.IdentityResolver
import `in`.callsentry.app.core.Phone
import `in`.callsentry.app.data.CallRecord
import `in`.callsentry.app.data.CommunityStats
import `in`.callsentry.app.databinding.ActivityCallDetailBinding
import `in`.callsentry.app.databinding.ItemEvidenceBinding

class CallDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityCallDetailBinding
    private val app get() = application as CallSentryApp
    private val evidenceAdapter = EvidenceAdapter()
    private var record: CallRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCallDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationIcon(R.drawable.ic_back)
        b.toolbar.setNavigationOnClickListener { finish() }

        b.rvEvidence.layoutManager = LinearLayoutManager(this)
        b.rvEvidence.adapter = evidenceAdapter

        b.btnAllow.setOnClickListener { applyRule("ALLOW") }
        b.btnSilence.setOnClickListener { applyRule("SILENCE") }
        b.btnBlock.setOnClickListener { applyRule("REJECT") }
        b.btnLabel.setOnClickListener { showLabelDialog() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val id = intent.getLongExtra("id", -1)
        Thread {
            record = app.db.callById(id)
            val rec = record ?: return@Thread
            val contact = Contacts.lookup(this, rec.phone)
            val identity = IdentityResolver(app.db).resolve(rec.phone, contact)
            val (decision, reason) = DecisionEngine(app.db, app.prefs).decide(rec.phone, identity)
            val stats = app.db.communityStats(rec.phone)
            runOnUiThread { bind(rec, identity, decision, reason, stats) }
        }.start()
    }

    private fun bind(
        rec: CallRecord,
        identity: CallerIdentity,
        decision: Decision,
        reason: String,
        stats: CommunityStats
    ) {
        b.tvNumber.text = Phone.pretty(rec.phone)
        b.tvIdentityName.text = identity.name
        Ui.badge(b.tvBadge, Ui.levelName(identity.level), Ui.levelColor(this, identity.level))
        b.tvConfidence.text = identity.confidence ?: "No identity claim beyond the evidence above."
        b.tvDecision.text = "Decision: ${Ui.actionName(decision)} — $reason"
        b.tvDecision.setTextColor(Ui.actionColor(this, decision))
        evidenceAdapter.submit(identity.evidence)
        b.tvCommunity.text = if (stats.total > 0) {
            "Community totals: ${stats.spam} spam · ${stats.fraud} fraud · ${stats.legitimate} legitimate · ${stats.financial} financial" +
                (stats.topGuess?.let { "\nMost common guess: $it (${stats.topGuessCount} report(s))" } ?: "")
        } else {
            "No community reports for this number yet."
        }
    }

    private fun applyRule(action: String) {
        val rec = record ?: return
        Thread { app.db.replaceExactRule(rec.phone, action) }.start()
        Toast.makeText(this, "Rule saved for ${rec.phone}", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun showLabelDialog() {
        val rec = record ?: return
        val input = EditText(this)
        input.hint = "Your label, e.g. \"My HDFC agent\""
        input.setText(app.db.labelFor(rec.phone) ?: "")
        MaterialAlertDialogBuilder(this)
            .setTitle("Your label")
            .setMessage("A personal label overrides community guesses. It stays on this device.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val label = input.text.toString().trim()
                if (label.isNotEmpty()) {
                    Thread { app.db.upsertLabel(rec.phone, label) }.start()
                    Toast.makeText(this, "Label saved", Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private inner class EvidenceAdapter : RecyclerView.Adapter<EvidenceAdapter.VH>() {

        private val items = mutableListOf<Evidence>()

        fun submit(list: List<Evidence>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemEvidenceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            holder.b.tvEvSource.text = e.source
            holder.b.tvEvText.text = e.statement
        }

        inner class VH(val b: ItemEvidenceBinding) : RecyclerView.ViewHolder(b.root)
    }
}
