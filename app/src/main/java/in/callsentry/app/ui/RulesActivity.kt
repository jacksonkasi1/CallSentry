package `in`.callsentry.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import `in`.callsentry.app.CallSentryApp
import `in`.callsentry.app.R
import `in`.callsentry.app.core.DecisionEngine
import `in`.callsentry.app.core.Phone
import `in`.callsentry.app.data.UserRule
import `in`.callsentry.app.databinding.ActivityRulesBinding
import `in`.callsentry.app.databinding.DialogRuleBinding
import `in`.callsentry.app.databinding.ItemRuleBinding

class RulesActivity : AppCompatActivity() {

    private lateinit var b: ActivityRulesBinding
    private val app get() = application as CallSentryApp
    private val adapter = RulesAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRulesBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationIcon(R.drawable.ic_back)
        b.toolbar.setNavigationOnClickListener { finish() }

        b.rvRules.layoutManager = LinearLayoutManager(this)
        b.rvRules.adapter = adapter

        b.sw140.isChecked = app.prefs.getBoolean("preset_140", true)
        b.swIntl.isChecked = app.prefs.getBoolean("preset_intl", false)
        b.swTollfree.isChecked = app.prefs.getBoolean("preset_tollfree", false)
        b.sw140.setOnCheckedChangeListener { _, c -> app.prefs.edit().putBoolean("preset_140", c).apply() }
        b.swIntl.setOnCheckedChangeListener { _, c -> app.prefs.edit().putBoolean("preset_intl", c).apply() }
        b.swTollfree.setOnCheckedChangeListener { _, c -> app.prefs.edit().putBoolean("preset_tollfree", c).apply() }

        b.fabAdd.setOnClickListener { showRuleDialog() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        Thread {
            val rules = app.db.rules()
            runOnUiThread {
                adapter.submit(rules)
                b.tvEmptyRules.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun showRuleDialog() {
        val d = DialogRuleBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle("New rule")
            .setView(d.root)
            .setPositiveButton("Save") { _, _ ->
                val pattern = d.etPattern.text.toString().trim()
                if (pattern.isNotEmpty()) {
                    val match = if (d.rbPrefix.isChecked) "PREFIX" else "EXACT"
                    val action = when (d.rgAction.checkedRadioButtonId) {
                        R.id.rbActAllow -> "ALLOW"
                        R.id.rbActReject -> "REJECT"
                        else -> "SILENCE"
                    }
                    val label = d.etLabel.text.toString().trim().ifEmpty { null }
                    Thread { app.db.addRule(pattern, match, action, label) }.start()
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private inner class RulesAdapter : RecyclerView.Adapter<RulesAdapter.VH>() {

        private val items = mutableListOf<UserRule>()

        fun submit(list: List<UserRule>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val rule = items[position]
            holder.b.tvRulePattern.text = Phone.pretty(rule.pattern)
            val matchLabel = if (rule.matchType == "PREFIX") "Starts with" else "Exact number"
            holder.b.tvRuleMeta.text = listOfNotNull(matchLabel, rule.label).joinToString(" · ")
            val action = DecisionEngine.actionOf(rule.action)
            Ui.badge(
                holder.b.tvRuleAction,
                Ui.actionName(action),
                Ui.actionColor(holder.b.root.context, action)
            )
            holder.b.root.setOnLongClickListener {
                MaterialAlertDialogBuilder(this@RulesActivity)
                    .setTitle("Delete rule")
                    .setMessage("Delete the rule for ${rule.pattern}?")
                    .setPositiveButton("Delete") { _, _ ->
                        Thread { app.db.deleteRule(rule.id) }.start()
                        refresh()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
        }

        inner class VH(val b: ItemRuleBinding) : RecyclerView.ViewHolder(b.root)
    }
}
