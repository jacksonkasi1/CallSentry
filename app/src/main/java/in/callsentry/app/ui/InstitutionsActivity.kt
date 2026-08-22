package `in`.callsentry.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import `in`.callsentry.app.CallSentryApp
import `in`.callsentry.app.R
import `in`.callsentry.app.core.Phone
import `in`.callsentry.app.data.Institution
import `in`.callsentry.app.databinding.ActivityInstitutionsBinding
import `in`.callsentry.app.databinding.DialogInstitutionBinding
import `in`.callsentry.app.databinding.ItemInstitutionBinding

class InstitutionsActivity : AppCompatActivity() {

    private lateinit var b: ActivityInstitutionsBinding
    private val app get() = application as CallSentryApp
    private val adapter = InstAdapter()

    private val categories = listOf(
        "BANK", "NBFC", "INSURANCE", "WALLET", "TELECOM", "GOVERNMENT", "OTHER"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityInstitutionsBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationIcon(R.drawable.ic_back)
        b.toolbar.setNavigationOnClickListener { finish() }

        b.rvInstitutions.layoutManager = LinearLayoutManager(this)
        b.rvInstitutions.adapter = adapter

        b.fabAddInstitution.setOnClickListener { showAddDialog() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        Thread {
            val list = app.db.institutions()
            runOnUiThread {
                adapter.submit(list)
                b.tvEmptyInstitutions.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun showAddDialog() {
        val d = DialogInstitutionBinding.inflate(layoutInflater)
        d.spCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        MaterialAlertDialogBuilder(this)
            .setTitle("Add institution")
            .setView(d.root)
            .setPositiveButton("Add") { _, _ ->
                val name = d.etInstName.text.toString().trim()
                val phone = Phone.digits(d.etInstPhone.text.toString())
                if (name.isNotEmpty() && !phone.isNullOrEmpty()) {
                    val category = d.spCategory.selectedItem?.toString() ?: "OTHER"
                    val phoneLabel = d.etInstPhoneLabel.text.toString().trim().ifEmpty { "Official number" }
                    Thread {
                        val id = app.db.addInstitution(name, category, null)
                        app.db.addNumber(id, phone, phoneLabel, "SELF")
                    }.start()
                    Toast.makeText(this, "Added to your registry", Toast.LENGTH_SHORT).show()
                    refresh()
                } else {
                    Toast.makeText(this, "Name and phone number are required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private inner class InstAdapter : RecyclerView.Adapter<InstAdapter.VH>() {

        private val items = mutableListOf<Institution>()

        fun submit(list: List<Institution>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemInstitutionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val inst = items[position]
            Thread {
                val count = app.db.numberCount(inst.id)
                runOnUiThread {
                    holder.b.tvInstName.text = inst.name
                    holder.b.tvInstMeta.text = "${
                        inst.category.replaceFirstChar { it.uppercase() }
                    } · $count verified number(s)"
                }
            }.start()
            holder.b.root.setOnClickListener {
                startActivity(
                    Intent(this@InstitutionsActivity, InstitutionDetailActivity::class.java)
                        .putExtra("id", inst.id)
                )
            }
        }

        inner class VH(val b: ItemInstitutionBinding) : RecyclerView.ViewHolder(b.root)
    }
}
