package `in`.callsentry.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import `in`.callsentry.app.CallSentryApp
import `in`.callsentry.app.R
import `in`.callsentry.app.data.Institution
import `in`.callsentry.app.databinding.ActivityRelationshipsBinding
import `in`.callsentry.app.databinding.ItemRelationshipBinding

class RelationshipsActivity : AppCompatActivity() {

    private lateinit var b: ActivityRelationshipsBinding
    private val app get() = application as CallSentryApp
    private val adapter = RelAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRelationshipsBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationIcon(R.drawable.ic_back)
        b.toolbar.setNavigationOnClickListener { finish() }

        b.rvRelationships.layoutManager = LinearLayoutManager(this)
        b.rvRelationships.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        Thread {
            val list = app.db.institutions().map { it to app.db.hasRelationship(it.id) }
            runOnUiThread {
                adapter.submit(list)
                b.tvEmptyRelationships.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private inner class RelAdapter : RecyclerView.Adapter<RelAdapter.VH>() {

        private val items = mutableListOf<Pair<Institution, Boolean>>()

        fun submit(list: List<Pair<Institution, Boolean>>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemRelationshipBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (inst, declared) = items[position]
            holder.b.tvRelName.text = inst.name
            holder.b.tvRelMeta.text = inst.category
            if (declared) {
                Ui.badge(
                    holder.b.tvRelState, "Declared",
                    ContextCompat.getColor(holder.b.root.context, R.color.levelOfficial)
                )
            } else {
                Ui.badge(
                    holder.b.tvRelState, "Declare",
                    ContextCompat.getColor(holder.b.root.context, R.color.levelUnknown)
                )
            }
            holder.b.root.setOnClickListener {
                Toast.makeText(this@RelationshipsActivity, if (declared) "Relationship removed" else "Relationship declared: ${inst.name}", Toast.LENGTH_SHORT).show()
                Thread { app.db.toggleRelationship(inst.id) }.start()
                refresh()
            }
        }

        inner class VH(val b: ItemRelationshipBinding) : RecyclerView.ViewHolder(b.root)
    }
}
