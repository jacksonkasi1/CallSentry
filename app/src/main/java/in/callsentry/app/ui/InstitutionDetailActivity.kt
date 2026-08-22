package `in`.callsentry.app.ui

import android.content.Intent
import android.net.Uri
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
import `in`.callsentry.app.core.Phone
import `in`.callsentry.app.data.OfficialNumber
import `in`.callsentry.app.databinding.ActivityInstitutionDetailBinding
import `in`.callsentry.app.databinding.ItemNumberBinding

class InstitutionDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityInstitutionDetailBinding
    private val app get() = application as CallSentryApp
    private val adapter = NumbersAdapter()
    private var instId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityInstitutionDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationIcon(R.drawable.ic_back)
        b.toolbar.setNavigationOnClickListener { finish() }

        instId = intent.getLongExtra("id", -1)

        b.rvNumbers.layoutManager = LinearLayoutManager(this)
        b.rvNumbers.adapter = adapter

        b.btnRelationship.setOnClickListener {
            Thread { app.db.toggleRelationship(instId) }.start()
            Toast.makeText(this, if (b.btnRelationship.text.startsWith("Declare")) "Relationship declared" else "Relationship removed", Toast.LENGTH_SHORT).show()
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (instId <= 0) return
        Thread {
            val inst = app.db.institution(instId) ?: return@Thread
            val numbers = app.db.numbersFor(instId)
            val declared = app.db.hasRelationship(instId)
            runOnUiThread {
                b.toolbar.title = inst.name
                b.tvInstName.text = inst.name
                b.tvInstMeta.text = listOfNotNull(
                    inst.category.replaceFirstChar { it.uppercase() },
                    inst.website
                ).joinToString(" · ")
                b.btnRelationship.text = if (declared) "Remove relationship" else "Declare relationship"
                adapter.submit(numbers)
                b.tvEmptyNumbers.visibility = if (numbers.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private inner class NumbersAdapter : RecyclerView.Adapter<NumbersAdapter.VH>() {

        private val items = mutableListOf<OfficialNumber>()

        fun submit(list: List<OfficialNumber>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemNumberBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val num = items[position]
            holder.b.tvNumPhone.text = Phone.pretty(num.phone)
            holder.b.tvNumLabel.text = num.label
            val (verifyText, verifyColor) = when (num.verification) {
                "OFFICIAL" -> "Officially verified" to R.color.levelOfficial
                "PARTNER" -> "Partner-verified" to R.color.levelPartner
                else -> "Your entry" to R.color.levelPersonal
            }
            holder.b.tvNumVerify.text = verifyText
            holder.b.tvNumVerify.setTextColor(ContextCompat.getColor(holder.b.root.context, verifyColor))
            holder.b.btnCall.setOnClickListener {
                startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Phone.dialUri(num.phone)}"))
                )
            }
        }

        inner class VH(val b: ItemNumberBinding) : RecyclerView.ViewHolder(b.root)
    }
}
